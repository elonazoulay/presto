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
package com.facebook.presto.plugin.turbonium;

import com.facebook.presto.plugin.turbonium.config.TurboniumConfigManager;
import com.facebook.presto.plugin.turbonium.storage.Table;
import com.facebook.presto.spi.Page;
import com.facebook.presto.spi.PrestoException;
import com.facebook.presto.spi.type.Type;
import com.google.common.collect.ImmutableList;
import io.airlift.log.Logger;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

import javax.inject.Inject;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import static com.facebook.presto.plugin.turbonium.TurboniumErrorCode.MEMORY_LIMIT_EXCEEDED;
import static com.facebook.presto.plugin.turbonium.TurboniumErrorCode.MISSING_DATA;
import static com.facebook.presto.plugin.turbonium.TurboniumErrorCode.TABLE_SIZE_PER_NODE_EXCEEDED;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

@ThreadSafe
public class TurboniumPagesStore
{
    private static final Logger log = Logger.get(TurboniumPagesStore.class);
    private final TurboniumConfigManager configManager;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    @GuardedBy("lock")
    private long currentBytes = 0;

    @Inject
    public TurboniumPagesStore(TurboniumConfigManager configManager)
    {
        this.configManager = configManager;
    }

    @GuardedBy("lock")
    private final Map<Long, Table.Builder> tableBuilder = new HashMap<>();

    @GuardedBy("lock")
    private final Map<Long, Table> tables = new HashMap<>();

    @GuardedBy("lock")
    private final Map<Long, Long> tableSizes = new HashMap<>();

    private long getMaxBytes()
    {
        return configManager.getConfig().getMaxDataPerNode().toBytes();
    }

    private long getMaxBytesPerTable()
    {
        return configManager.getConfig().getMaxTableSizePerNode().toBytes();
    }

    public void initialize(long tableId, TurboniumTableHandle tableHandle)
    {
        try {
            lock.writeLock().lock();
            if (!tableBuilder.containsKey(tableId)) {
                tableBuilder.put(tableId, Table.builder(extractTypes(tableHandle)));
                tableSizes.put(tableId, 0L);
            }
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public void add(Long tableId, Page page)
    {
        Table.Builder builder;
        try {
            lock.writeLock().lock();
            if (!tableSizes.containsKey(tableId)) {
                throw new PrestoException(MISSING_DATA, "Failed to find table on a worker.");
            }
            page.compact();
            long newSize = currentBytes + page.getRetainedSizeInBytes();
            // TODO: base the memory limits on segment size not the source page size
            long newTableSize = tableSizes.get(tableId) + page.getRetainedSizeInBytes();
            long maxBytes = getMaxBytes();
            if (maxBytes < newSize) {
                throw new PrestoException(MEMORY_LIMIT_EXCEEDED, format("Memory limit [%d] for memory connector exceeded", maxBytes));
            }
            long maxBytesPerTable = getMaxBytesPerTable();
            if (maxBytesPerTable < newTableSize) {
                throw new PrestoException(TABLE_SIZE_PER_NODE_EXCEEDED, format("Table segmentCount [%d] bytes per node exceeded", maxBytesPerTable));
            }
            currentBytes = newSize;
            tableSizes.put(tableId, newSize);
            builder = tableBuilder.get(tableId);
        }
        finally {
            lock.writeLock().unlock();
        }
        builder.appendPage(page);
    }

    public synchronized List<Page> getPages(Long tableId, int partNumber, int totalParts, List<Integer> columnIndexes)
    {
        try {
            lock.readLock().lock();
            if (!tables.containsKey(tableId)) {
                throw new PrestoException(MISSING_DATA, "Failed to find table on a worker.");
            }

            Table table = requireNonNull(tables.get(tableId), "Failed to find table on worker");
            table.getPages(partNumber, totalParts, columnIndexes);
            return table.getPages(partNumber, totalParts, columnIndexes);
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public List<Long> listTableIds()
    {
        try {
            lock.readLock().lock();
            return ImmutableList.copyOf(tableSizes.keySet());
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public synchronized SizeInfo getSize(Long tableId)
    {
        try {
            lock.readLock().lock();
            long sizeBytes = tables.get(tableId).getSizeBytes();
            long rowCount = tables.get(tableId).getRowCount();
            long pagesCount = tables.get(tableId).getSegmentCount();
            return new SizeInfo(rowCount, sizeBytes, pagesCount, tableSizes.get(tableId));
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public boolean contains(Long tableId)
    {
        try {
            lock.readLock().lock();
            return tableSizes.containsKey(tableId);
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public void cleanUp(Set<Long> activeTableIds)
    {
        // We have to remember that there might be some race conditions when there are two tables created at once.
        // That can lead to a situation when TurboniumPagesStore already knows about a newer second table on some worker
        // but cleanUp is triggered by insert from older first table, which TurboniumTableHandle was created before
        // second table creation. Thus activeTableIds can have missing latest ids and we can only clean up tables
        // that:
        // - have smaller value then max(activeTableIds).
        // - are missing from activeTableIds set

        if (activeTableIds.isEmpty()) {
            // if activeTableIds is empty, we can not determine latestTableId...
            return;
        }
        long latestTableId  = Collections.max(activeTableIds);

        try {
            lock.writeLock().lock();
            for (Iterator<Map.Entry<Long, Table>> tablePages = tables.entrySet().iterator(); tablePages.hasNext(); ) {
                Map.Entry<Long, Table> tablePagesEntry = tablePages.next();
                Long tableId = tablePagesEntry.getKey();
                if (tableId < latestTableId && !activeTableIds.contains(tableId)) {
                    currentBytes -= tablePagesEntry.getValue().getSizeBytes();
                    tablePages.remove();
                    tableSizes.remove(tableId);
                    tableBuilder.remove(tableId);
                    tables.remove(tableId);
                }
            }
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public void finishCreate(long tableId)
    {
        try {
            lock.writeLock().lock();
            Table.Builder builder = requireNonNull(tableBuilder.remove(tableId), "table not found");
            tables.put(tableId, builder.build());
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public static class SizeInfo
    {
        private final long rowCount;
        private final long byteSize;
        private final long pageCount;
        private final long sourceSize;
        public SizeInfo(long rowCount, long byteSize, long pageCount, long sourceSize)
        {
            this.rowCount = rowCount;
            this.byteSize = byteSize;
            this.pageCount = pageCount;
            this.sourceSize = sourceSize;
        }

        public long getRowCount()
        {
            return rowCount;
        }

        public long getSizeBytes()
        {
            return byteSize;
        }

        public long getPageCount()
        {
            return pageCount;
        }
        public long getSourceSize()
        {
            return sourceSize;
        }
    }

    private static List<Type> extractTypes(TurboniumTableHandle tableHandle)
    {
        return tableHandle.getColumnHandles().stream()
                .map(TurboniumColumnHandle::getColumnType)
                .collect(Collectors.toList());
    }
}
