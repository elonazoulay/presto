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
package com.facebook.presto.resourceGroups.systemtables;

import com.facebook.presto.spi.ColumnMetadata;
import com.facebook.presto.spi.ConnectorPageSource;
import com.facebook.presto.spi.ConnectorSession;
import com.facebook.presto.spi.ConnectorTableMetadata;
import com.facebook.presto.spi.FixedPageSource;
import com.facebook.presto.spi.Page;
import com.facebook.presto.spi.PageBuilder;
import com.facebook.presto.spi.SchemaTableName;
import com.facebook.presto.spi.SystemTable;
import com.facebook.presto.spi.connector.ConnectorTransactionHandle;
import com.facebook.presto.spi.predicate.TupleDomain;
import com.facebook.presto.spi.resourceGroups.QueryQueueInfo;
import com.facebook.presto.spi.type.Type;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

import java.util.List;

import static com.facebook.presto.spi.SystemTable.Distribution.SINGLE_COORDINATOR;
import static com.facebook.presto.spi.type.BigintType.BIGINT;
import static com.facebook.presto.spi.type.BooleanType.BOOLEAN;
import static com.facebook.presto.spi.type.VarcharType.VARCHAR;
import static io.airlift.slice.Slices.utf8Slice;

import static java.util.stream.Collectors.toList;

public class ResourceGroupsInfoSystemTable
    implements SystemTable
{
    private static final ConnectorTableMetadata METADATA = new ConnectorTableMetadata(
            new SchemaTableName("system", "query_queues"),
            ImmutableList.<ColumnMetadata>builder()
                    .add(new ColumnMetadata("root_group_id", VARCHAR))
                    .add(new ColumnMetadata("resource_group_id", VARCHAR))
                    .add(new ColumnMetadata("approximate_order", BIGINT))
                    .add(new ColumnMetadata("query_id", VARCHAR))
                    .add(new ColumnMetadata("is_queued", BOOLEAN))
                    .build());
    private final QueryQueueCache queryQueueCache;

    @Inject
    public ResourceGroupsInfoSystemTable(QueryQueueCache queryQueueCache)
    {
        this.queryQueueCache = queryQueueCache;
    }
    @Override
    public Distribution getDistribution()
    {
        return SINGLE_COORDINATOR;
    }

    @Override
    public ConnectorTableMetadata getTableMetadata()
    {
        return METADATA;
    }

    @Override
    public ConnectorPageSource pageSource(ConnectorTransactionHandle transactionHandle, ConnectorSession session, TupleDomain<Integer> constraint)
    {
        ImmutableList.Builder<Page> pages = ImmutableList.builder();
        List<Type> types = METADATA.getColumns().stream().map(ColumnMetadata::getType).collect(toList());
        PageBuilder pageBuilder = new PageBuilder(types);
        for (QueryQueueInfo queryQueueInfo : queryQueueCache.getQueueInfo()) {
            String rootGroupId = queryQueueInfo.getRootGroupId().toString();
            for (QueryQueueInfo.QueryEntry entry : queryQueueInfo.getQueryQueue()) {
                if (pageBuilder.isFull()) {
                    pages.add(pageBuilder.build());
                    pageBuilder.reset();
                }
                pageBuilder.declarePosition();
                int channel = 0;
                VARCHAR.writeSlice(pageBuilder.getBlockBuilder(channel++), utf8Slice(rootGroupId));
                VARCHAR.writeSlice(pageBuilder.getBlockBuilder(channel++), utf8Slice(entry.getLeafGroupId().toString()));
                BIGINT.writeLong(pageBuilder.getBlockBuilder(channel++), entry.getApproximatePosition());
                VARCHAR.writeSlice(pageBuilder.getBlockBuilder(channel++), utf8Slice(entry.getQueryId().toString()));
                BOOLEAN.writeBoolean(pageBuilder.getBlockBuilder(channel), entry.getIsQueued());
            }
        }
        if (!pageBuilder.isEmpty()) {
            pages.add(pageBuilder.build());
        }
        /*
        pageBuilder.declarePosition();

        int channel = 0;
        BlockBuilder blockBuilder = pageBuilder.getBlockBuilder(channel++);

        BIGINT.writeLong(blockBuilder, 11);
        blockBuilder = pageBuilder.getBlockBuilder(channel);
        BIGINT.writeLong(blockBuilder, 12);
        channel = 0;
        pageBuilder.declarePosition();
        blockBuilder = pageBuilder.getBlockBuilder(channel++);
        BIGINT.writeLong(blockBuilder, 21);
        blockBuilder = pageBuilder.getBlockBuilder(channel++);
        BIGINT.writeLong(blockBuilder, 22);
        */
        return new FixedPageSource(pages.build());
    }
}
