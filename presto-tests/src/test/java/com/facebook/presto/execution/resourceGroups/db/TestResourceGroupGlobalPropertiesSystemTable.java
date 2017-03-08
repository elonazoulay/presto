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
package com.facebook.presto.execution.resourceGroups.db;

import com.facebook.presto.testing.MaterializedResult;
import com.facebook.presto.testing.MaterializedRow;
import com.facebook.presto.tests.DistributedQueryRunner;
import org.testng.annotations.Test;

import static com.facebook.presto.execution.resourceGroups.db.H2QueryRunner.createQueryRunner;
import static com.facebook.presto.execution.resourceGroups.db.H2QueryRunner.getResourceGroupsGlobalPropertiesQuery;
import static com.facebook.presto.execution.resourceGroups.db.H2QueryRunner.setCpuQuotaPeriodQuery;
import static com.facebook.presto.resourceGroups.db.ResourceGroupGlobalProperties.CPU_QUOTA_PERIOD;
import static org.testng.Assert.assertEquals;

public class TestResourceGroupGlobalPropertiesSystemTable
{
    @Test
    public void testResourceGroupsGlobalPropertiesSystemTable()
            throws Exception
    {
        try (DistributedQueryRunner queryRunner = createQueryRunner()) {
            MaterializedResult result = getResourceGroupsGlobalPropertiesQuery(queryRunner);
            assertEquals(result.getRowCount(), 1);
            for (MaterializedRow row : result.getMaterializedRows()) {
                if (row.getField(0).toString().equals(CPU_QUOTA_PERIOD)) {
                    assertEquals(row.getField(1), "1.00h");
                    break;
                }
            }
            setCpuQuotaPeriodQuery(queryRunner, "2h");
            result = getResourceGroupsGlobalPropertiesQuery(queryRunner);
            assertEquals(result.getRowCount(), 1);
            for (MaterializedRow row : result.getMaterializedRows()) {
                if (row.getField(0).toString().equals(CPU_QUOTA_PERIOD)) {
                    assertEquals(row.getField(1), "2.00h");
                    break;
                }
            }
        }
    }
}
