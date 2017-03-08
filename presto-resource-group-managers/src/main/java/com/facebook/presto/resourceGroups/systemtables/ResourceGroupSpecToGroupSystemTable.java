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

import com.facebook.presto.resourceGroups.ResourceGroupIdTemplate;
import com.facebook.presto.spi.ColumnMetadata;
import com.facebook.presto.spi.ConnectorSession;
import com.facebook.presto.spi.ConnectorTableMetadata;
import com.facebook.presto.spi.InMemoryRecordSet;
import com.facebook.presto.spi.RecordCursor;
import com.facebook.presto.spi.SchemaTableName;
import com.facebook.presto.spi.SystemTable;
import com.facebook.presto.spi.connector.ConnectorTransactionHandle;
import com.facebook.presto.spi.predicate.TupleDomain;
import com.facebook.presto.spi.resourceGroups.ResourceGroupId;
import com.google.common.collect.ImmutableList;

import javax.inject.Inject;

import java.util.List;
import java.util.Map;

import static com.facebook.presto.spi.SystemTable.Distribution.SINGLE_COORDINATOR;
import static com.facebook.presto.spi.type.VarcharType.VARCHAR;

import static java.util.Objects.requireNonNull;

public class ResourceGroupSpecToGroupSystemTable
        implements SystemTable
{
    private final ResourceGroupInfoHolder resourceGroupInfoHolder;
    private static final ConnectorTableMetadata METADATA = new ConnectorTableMetadata(
            new SchemaTableName("system", "spec_to_group"),
            ImmutableList.<ColumnMetadata>builder()
                    .add(new ColumnMetadata("resource_group_template_id", VARCHAR))
                    .add(new ColumnMetadata("resource_group_id", VARCHAR))
                    .build());

    @Inject
    public ResourceGroupSpecToGroupSystemTable(ResourceGroupInfoHolder resourceGroupInfoHolder)
    {
        this.resourceGroupInfoHolder = requireNonNull(resourceGroupInfoHolder, "resourceGroupInfoHolder is null");
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
    public RecordCursor cursor(ConnectorTransactionHandle transactionHandle, ConnectorSession session, TupleDomain<Integer> constraint)
    {
        InMemoryRecordSet.Builder table = InMemoryRecordSet.builder(METADATA);
        for (Map.Entry<ResourceGroupIdTemplate, List<ResourceGroupId>> entry : resourceGroupInfoHolder.getSpecToGroup().entrySet()) {
            ResourceGroupIdTemplate specId = entry.getKey();
            for (ResourceGroupId groupId : entry.getValue()) {
                table.addRow(specId, groupId);
            }
        }
        return table.build().cursor();
    }
}
