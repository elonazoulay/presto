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
import com.facebook.presto.spi.ConnectorSession;
import com.facebook.presto.spi.ConnectorTableMetadata;
import com.facebook.presto.spi.InMemoryRecordSet;
import com.facebook.presto.spi.InMemoryRecordSet.Builder;
import com.facebook.presto.spi.RecordCursor;
import com.facebook.presto.spi.SchemaTableName;
import com.facebook.presto.spi.SystemTable;
import com.facebook.presto.spi.connector.ConnectorTransactionHandle;
import com.facebook.presto.spi.predicate.TupleDomain;
import com.google.common.collect.ImmutableList;
import io.airlift.units.Duration;

import javax.inject.Inject;

import java.util.Optional;

import static com.facebook.presto.resourceGroups.db.ResourceGroupGlobalProperties.CPU_QUOTA_PERIOD;
import static com.facebook.presto.spi.SystemTable.Distribution.SINGLE_COORDINATOR;
import static com.facebook.presto.spi.type.VarcharType.VARCHAR;
import static java.util.Objects.requireNonNull;

public class ResourceGroupGlobalPropertiesSystemTable
        implements SystemTable
{
    private final ResourceGroupInfoHolder resourceGroupInfoHolder;

    private static final ConnectorTableMetadata METADATA = new ConnectorTableMetadata(
            new SchemaTableName("system", "resource_groups_global_properties"),
            ImmutableList.<ColumnMetadata>builder()
                .add(new ColumnMetadata("name", VARCHAR))
                .add(new ColumnMetadata("value", VARCHAR))
                .build());

    @Inject
    public ResourceGroupGlobalPropertiesSystemTable(ResourceGroupInfoHolder resourceGroupInfoHolder)
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
        Builder table = InMemoryRecordSet.builder(METADATA);
        Optional<Duration> cpuQuotaPeriod = resourceGroupInfoHolder.getCpuQuotaPeriod();
        table.addRow(CPU_QUOTA_PERIOD, cpuQuotaPeriod.map(Duration::toString).orElse(null));
        return table.build().cursor();
    }
}
