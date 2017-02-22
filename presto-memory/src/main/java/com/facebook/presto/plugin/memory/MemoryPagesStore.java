/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.plugin.memory;

import com.facebook.presto.plugin.memory.config.MemoryConfigManager;
import com.facebook.presto.spi.Page;
import com.facebook.presto.spi.PrestoException;
import com.facebook.presto.spi.block.Block;
import com.google.common.collect.ImmutableList;
import io.airlift.log.Logger;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.facebook.presto.plugin.memory.MemoryErrorCode.MEMORY_LIMIT_EXCEEDED;
import static com.facebook.presto.plugin.memory.MemoryErrorCode.MISSING_DATA;
import static com.facebook.presto.plugin.memory.MemoryErrorCode.TABLE_SIZE_PER_NODE_EXCEEDED;
import static java.lang.String.format;

@ThreadSafe
public class MemoryPagesStore
{
    private static final Logger log = Logger.get(MemoryPagesStore.class);
    private final MemoryConfigManager configManager;

    @GuardedBy("this")
    private long currentBytes = 0;

    @Inject
    public MemoryPagesStore(MemoryConfigManager configManager)
    {
        this.configManager = configManager;
    }

    @GuardedBy("this")
    private final Map<Long, List<Page>> pages = new HashMap<>();

    @GuardedBy("this")
    private final Map<Long, Long> tableSizes = new HashMap<>();

    @GuardedBy("this")
    private final Map<Long, String> tableNames = new HashMap<>();

    @GuardedBy("this")
    private final Map<String, Long> tableIds = new HashMap<>();

    private long getMaxBytes()
    {
        return configManager.getConfig().getMaxDataPerNode().toBytes();
    }

    private long getMaxBytesPerTable()
    {
        return configManager.getConfig().getMaxTableSizePerNode().toBytes();
    }

    public synchronized void initialize(String tableName, long tableId)
    {
        if (!pages.containsKey(tableId)) {
            pages.put(tableId, new ArrayList<>());
            tableIds.put(tableName, tableId);
            tableNames.put(tableId, tableName);
            tableSizes.put(tableId, 0L);
        }
    }

    public synchronized void add(Long tableId, Page page)
    {
        if (!contains(tableId)) {
            throw new PrestoException(MISSING_DATA, "Failed to find table on a worker.");
        }

        long newSize = currentBytes + page.getRetainedSizeInBytes();
        long newTableSize = tableSizes.get(tableId) + page.getRetainedSizeInBytes();
        long maxBytes = getMaxBytes();
        if (maxBytes < newSize) {
            throw new PrestoException(MEMORY_LIMIT_EXCEEDED, format("Memory limit [%d] for memory connector exceeded", maxBytes));
        }
        long maxBytesPerTable = getMaxBytesPerTable();
        if (maxBytesPerTable < newTableSize) {
            throw new PrestoException(TABLE_SIZE_PER_NODE_EXCEEDED, format("Table size [%d] bytes per node exceeded", maxBytesPerTable));
        }
        currentBytes = newSize;
        tableSizes.put(tableId, newSize);
        List<Page> tablePages = pages.get(tableId);
        tablePages.add(page);
    }

    public synchronized List<Page> getPages(Long tableId, int partNumber, int totalParts, List<Integer> columnIndexes)
    {
        if (!contains(tableId)) {
            throw new PrestoException(MISSING_DATA, "Failed to find table on a worker.");
        }

        List<Page> tablePages = pages.get(tableId);
        ImmutableList.Builder<Page> partitionedPages = ImmutableList.builder();

        for (int i = partNumber; i < tablePages.size(); i += totalParts) {
            partitionedPages.add(getColumns(tablePages.get(i), columnIndexes));
        }

        return partitionedPages.build();
    }

    public synchronized List<String> listTables()
    {
        return ImmutableList.copyOf(tableIds.keySet());
    }

    public synchronized long getSize(String tableName)
    {
        long tableId = tableIds.get(tableName);
        return tableSizes.getOrDefault(tableId, 0L);
    }

    public synchronized boolean contains(Long tableId)
    {
        return pages.containsKey(tableId);
    }

    public synchronized void cleanUp(Set<Long> activeTableIds)
    {
        // We have to remember that there might be some race conditions when there are two tables created at once.
        // That can lead to a situation when MemoryPagesStore already knows about a newer second table on some worker
        // but cleanUp is triggered by insert from older first table, which MemoryTableHandle was created before
        // second table creation. Thus activeTableIds can have missing latest ids and we can only clean up tables
        // that:
        // - have smaller value then max(activeTableIds).
        // - are missing from activeTableIds set

        if (activeTableIds.isEmpty()) {
            // if activeTableIds is empty, we can not determine latestTableId...
            return;
        }
        long latestTableId  = Collections.max(activeTableIds);

        for (Iterator<Map.Entry<Long, List<Page>>> tablePages = pages.entrySet().iterator(); tablePages.hasNext(); ) {
            Map.Entry<Long, List<Page>> tablePagesEntry = tablePages.next();
            Long tableId = tablePagesEntry.getKey();
            if (tableId < latestTableId && !activeTableIds.contains(tableId)) {
                for (Page removedPage : tablePagesEntry.getValue()) {
                    currentBytes -= removedPage.getRetainedSizeInBytes();
                }
                tablePages.remove();
                tableSizes.remove(tableId);
                String tableName = tableNames.remove(tableId);
                tableIds.remove(tableName);
            }
        }
    }

    private static Page getColumns(Page page, List<Integer> columnIndexes)
    {
        Block[] blocks = page.getBlocks();
        Block[] outputBlocks = new Block[columnIndexes.size()];

        for (int i = 0; i < columnIndexes.size(); i++) {
            outputBlocks[i] = blocks[columnIndexes.get(i)];
        }

        return new Page(page.getPositionCount(), outputBlocks);
    }
}
