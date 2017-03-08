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

import com.facebook.presto.resourceGroups.ResourceGroupSpec;
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
import com.facebook.presto.spi.resourceGroups.SchedulingPolicy;
import com.google.common.collect.ImmutableList;
import io.airlift.units.DataSize;
import io.airlift.units.Duration;

import javax.inject.Inject;

import java.util.Map;

import static com.facebook.presto.spi.SystemTable.Distribution.SINGLE_COORDINATOR;
import static com.facebook.presto.spi.type.BigintType.BIGINT;
import static com.facebook.presto.spi.type.BooleanType.BOOLEAN;
import static com.facebook.presto.spi.type.VarcharType.VARCHAR;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class ConfiguredGroupsSystemTable
    implements SystemTable
{
    private final ResourceGroupInfoHolder resourceGroupInfoHolder;

    private static final ConnectorTableMetadata METADATA = new ConnectorTableMetadata(
            new SchemaTableName("system", "configured_resource_groups"),
            ImmutableList.<ColumnMetadata>builder()
                    .add(new ColumnMetadata("resource_group_id", VARCHAR))
                    .add(new ColumnMetadata("soft_memory_limit", VARCHAR))
                    .add(new ColumnMetadata("hard_memory_limit", VARCHAR))
                    .add(new ColumnMetadata("max_queued", BIGINT))
                    .add(new ColumnMetadata("max_running", BIGINT))
                    .add(new ColumnMetadata("scheduling_policy", VARCHAR))
                    .add(new ColumnMetadata("scheduling_weight", BIGINT))
                    .add(new ColumnMetadata("jmx_export", BOOLEAN))
                    .add(new ColumnMetadata("soft_cpu_limit", VARCHAR))
                    .add(new ColumnMetadata("hard_cpu_limit", VARCHAR))
                    .add(new ColumnMetadata("queued_timeout", VARCHAR))
                    .add(new ColumnMetadata("running_timeout", VARCHAR))
                    .build());
    @Inject
    public ConfiguredGroupsSystemTable(ResourceGroupInfoHolder resourceGroupInfoHolder)
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
        for (Map.Entry<ResourceGroupId, ResourceGroupSpec> entry : resourceGroupInfoHolder.getConfiguredGroups().entrySet()) {
            table.addRow(
                    entry.getKey().toString(),
                    entry.getValue().getSoftMemoryLimit().map(DataSize::toString).orElse(
                            format("%.0f%%", (entry.getValue().getSoftMemoryLimitFraction().get() * 100))),
                    entry.getValue().getHardMemoryLimit().map(DataSize::toString).orElse(
                            format("%.0f%%", (entry.getValue().getHardMemoryLimitFraction().get() * 100))
                    ),
                    entry.getValue().getMaxQueued(),
                    entry.getValue().getMaxRunning(),
                    entry.getValue().getSchedulingPolicy().map(SchedulingPolicy::toString).orElse(""),
                    entry.getValue().getSchedulingWeight().orElse(0),
                    entry.getValue().getJmxExport().orElse(false),
                    entry.getValue().getSoftCpuLimit().map(Duration::toString).orElse(""),
                    entry.getValue().getHardCpuLimit().map(Duration::toString).orElse(""),
                    entry.getValue().getQueuedTimeout().map(Duration::toString).orElse(""),
                    entry.getValue().getRunningTimeout().map(Duration::toString).orElse("")
            );
        }
        return table.build().cursor();
    }
}
