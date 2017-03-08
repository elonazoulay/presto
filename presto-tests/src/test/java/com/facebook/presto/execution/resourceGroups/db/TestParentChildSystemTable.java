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
import static com.facebook.presto.execution.resourceGroups.db.H2QueryRunner.newDashboardSession;
import static org.testng.Assert.assertEquals;

public class TestParentChildSystemTable
{
    @Test
    public void testParentChild()
            throws Exception
    {
        try (DistributedQueryRunner queryRunner = createQueryRunner()) {
            MaterializedResult result = getParentChild(queryRunner);
            assertEquals(result.getRowCount(), 8);
            int expectedLeafCount = 4;
            int expectedIntermediateCount = 4;
            int actualLeafCount = 0;
            int actualIntermediateCount = 0;
            for (MaterializedRow row : result.getMaterializedRows()) {
                if (row.getField(1) == null) {
                    actualLeafCount++;
                }
                else if (row.getField(0) != null) {
                    actualIntermediateCount++;
                }
            }
            assertEquals(actualLeafCount, expectedLeafCount);
            assertEquals(actualIntermediateCount, expectedIntermediateCount);
        }
    }

    private static MaterializedResult getParentChild(DistributedQueryRunner queryRunner)
    {
        return queryRunner.execute(newDashboardSession(),
                "SELECT parent_template_id, child_template_id FROM resource_group_managers.system.parent_child");
    }
}
