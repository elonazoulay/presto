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
import static com.facebook.presto.execution.resourceGroups.db.H2QueryRunner.deleteResourceGroupQuery;
import static com.facebook.presto.execution.resourceGroups.db.H2QueryRunner.deleteSelectorQuery;
import static com.facebook.presto.execution.resourceGroups.db.H2QueryRunner.getResourceGroupSpecsQuery;
import static com.facebook.presto.execution.resourceGroups.db.H2QueryRunner.updateResourceGroupQuery;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestResourceGroupSpecSystemTable
{
    @Test
    public void testResourceGroupSpecSystemTable()
            throws Exception
    {
        try (DistributedQueryRunner queryRunner = createQueryRunner()) {
            MaterializedResult result = getResourceGroupSpecsQuery(queryRunner);
            assertEquals(result.getRowCount(), 6);
            for (MaterializedRow row : result.getMaterializedRows()) {
                if (row.getField(0).toString().equals("global.user-${USER}.dashboard-${USER}")) {
                    assertTrue(row.getField(8) == null);
                    break;
                }
            }
            updateResourceGroupQuery(queryRunner, "global.user-${USER}.dashboard-${USER}", "1MB", "1GB", "20GB", 1, 2, null, null, true, null, null, null, null);
            result = getResourceGroupSpecsQuery(queryRunner);
            for (MaterializedRow row : result.getMaterializedRows()) {
                if (row.getField(0).toString().equals("global.user-${USER}.dashboard-${USER}")) {
                    assertTrue((Boolean) row.getField(8));
                    break;
                }
            }
            deleteSelectorQuery(queryRunner, "global.user-${USER}.dashboard-${USER}", "user.*", "(?i).*dashboard.*");
            deleteResourceGroupQuery(queryRunner, "global.user-${USER}.dashboard-${USER}");
            result = getResourceGroupSpecsQuery(queryRunner);
            assertEquals(result.getRowCount(), 5);
        }
    }
}
