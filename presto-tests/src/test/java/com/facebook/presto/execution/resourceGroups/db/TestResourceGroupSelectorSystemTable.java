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
import static com.facebook.presto.execution.resourceGroups.db.H2QueryRunner.deleteSelectorQuery;
import static com.facebook.presto.execution.resourceGroups.db.H2QueryRunner.getSelectorsQuery;
import static com.facebook.presto.execution.resourceGroups.db.H2QueryRunner.updateSelectorQuery;
import static org.testng.Assert.assertEquals;

public class TestResourceGroupSelectorSystemTable
{
    @Test
    public void testResourceGroupSelectorSystemTable()
            throws Exception
    {
        try (DistributedQueryRunner queryRunner = createQueryRunner()) {
            MaterializedResult result = getSelectorsQuery(queryRunner);
            assertEquals(result.getRowCount(), 4);
            for (MaterializedRow row : result.getMaterializedRows()) {
                if (row.getField(0).toString().equals("global.user-${USER}.dashboard-${USER}")) {
                    assertEquals(row.getField(1), "user.*");
                    break;
                }
            }
            updateSelectorQuery(queryRunner, "global.user-${USER}.dashboard-${USER}", "user.*", "(?i).*dashboard.*", "foo", "(?i).*dashboard.*");
            result = getSelectorsQuery(queryRunner);
            for (MaterializedRow row : result.getMaterializedRows()) {
                if (row.getField(0).toString().equals("global.user-${USER}.dashboard-${USER}")) {
                    assertEquals(row.getField(1), "foo");
                    break;
                }
            }
            deleteSelectorQuery(queryRunner, "global.user-${USER}.dashboard-${USER}", "foo", "(?i).*dashboard.*");
            result = getSelectorsQuery(queryRunner);
            assertEquals(result.getRowCount(), 3);
        }
    }
}
