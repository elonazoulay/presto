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

package com.facebook.presto.plugin.memory.systemtables;

import com.facebook.presto.plugin.memory.MemoryMetadata;
import com.facebook.presto.plugin.memory.MemoryPagesStore;
import com.facebook.presto.plugin.memory.MemoryTableHandle;
import com.facebook.presto.spi.ColumnMetadata;
import com.facebook.presto.spi.ConnectorPageSource;
import com.facebook.presto.spi.ConnectorSession;
import com.facebook.presto.spi.ConnectorTableMetadata;
import com.facebook.presto.spi.FixedPageSource;
import com.facebook.presto.spi.NodeManager;
import com.facebook.presto.spi.Page;
import com.facebook.presto.spi.SchemaTableName;
import com.facebook.presto.spi.SystemTable;
import com.facebook.presto.spi.connector.ConnectorTransactionHandle;
import com.facebook.presto.spi.predicate.TupleDomain;
import com.google.common.collect.ImmutableList;

import javax.inject.Inject;

import java.util.List;

import static com.facebook.presto.spi.SystemTable.Distribution.ALL_NODES;
import static com.facebook.presto.spi.type.BigintType.BIGINT;
import static com.facebook.presto.spi.type.VarcharType.VARCHAR;
import static io.airlift.slice.Slices.utf8Slice;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

public class MemoryInfoSystemTable
    implements SystemTable
{
    private static final String TABLE_NAME = "table_name";
    private static final String SCHEMA_NAME = "table_schema";
    private final ConnectorTableMetadata tableMetadata;
    private final MemoryMetadata metadata;
    private final MemoryPagesStore pagesStore;
    private final NodeManager nodeManager;
    private final String nodeId;

    @Inject
    public MemoryInfoSystemTable(MemoryMetadata metadata, MemoryPagesStore pagesStore, NodeManager nodeManager)
    {
        this.metadata = requireNonNull(metadata, "metadata is null");
        this.pagesStore = requireNonNull(pagesStore, "pagesStore is null");
        this.nodeManager = requireNonNull(nodeManager, "nodeManager is null");
        this.nodeId = nodeManager.getCurrentNode().getNodeIdentifier();
        this.tableMetadata = new ConnectorTableMetadata(
                new SchemaTableName("system", "table_stats"),
                ImmutableList.of(
                        new ColumnMetadata("node_name", VARCHAR),
                        new ColumnMetadata(SCHEMA_NAME, VARCHAR),
                        new ColumnMetadata(TABLE_NAME, VARCHAR),
                        new ColumnMetadata("size", BIGINT)
                )
        );
    }

    @Override
    public Distribution getDistribution()
    {
        return ALL_NODES;
    }

    @Override
    public ConnectorTableMetadata getTableMetadata()
    {
        return tableMetadata;
    }

    @Override
    public ConnectorPageSource pageSource(ConnectorTransactionHandle transactionHandle, ConnectorSession session, TupleDomain<Integer> constraint)
    {
        return new FixedPageSource(getTableSizes());
    }

    private List<Page> getTableSizes()
    {
        PageListBuilder pageBuilder = new PageListBuilder(tableMetadata.getColumns().stream()
                .map(ColumnMetadata::getType)
                .collect(toList()));
        List<SchemaTableName> tableNames = metadata.listTables(null, MemoryMetadata.SCHEMA_NAME);
        for (SchemaTableName table : tableNames) {
            MemoryTableHandle tableHandle = (MemoryTableHandle) metadata.getTableHandle(null, table);
            long tableId = tableHandle.getTableId();
            Long size = pagesStore.getSize(tableId);
            if (size == null) {
                continue;
            }
            pageBuilder.beginRow();
            VARCHAR.writeSlice(pageBuilder.nextBlockBuilder(), utf8Slice(nodeId));
            VARCHAR.writeSlice(pageBuilder.nextBlockBuilder(), utf8Slice(table.getSchemaName()));
            VARCHAR.writeSlice(pageBuilder.nextBlockBuilder(), utf8Slice(table.getTableName()));
            BIGINT.writeLong(pageBuilder.nextBlockBuilder(), size);
        }
        return pageBuilder.build();
    }
}
