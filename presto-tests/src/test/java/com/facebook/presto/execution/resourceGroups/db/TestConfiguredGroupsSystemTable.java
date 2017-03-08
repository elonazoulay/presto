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
import com.google.common.collect.ImmutableSet;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.Set;

import static com.facebook.presto.execution.resourceGroups.db.H2QueryRunner.createQueryRunner;
import static com.facebook.presto.execution.resourceGroups.db.H2QueryRunner.newDashboardSession;
import static org.testng.Assert.assertEquals;

public class TestConfiguredGroupsSystemTable
{
    @Test
    public void testConfiguredGroups()
            throws Exception
    {
        try (DistributedQueryRunner queryRunner = createQueryRunner()) {
            MaterializedResult result = getConfiguredGroups(queryRunner);
            assertEquals(result.getRowCount(), 3);
            Set<String> expected = ImmutableSet.of("global", "global.user-user", "global.user-user.dashboard-user");
            Set<String> actual = new HashSet<>();
            for (MaterializedRow row : result.getMaterializedRows()) {
                actual.add(row.getField(1).toString());
            }
            assertEquals(actual, expected);
        }
    }

    private static MaterializedResult getConfiguredGroups(DistributedQueryRunner queryRunner)
    {
        return queryRunner.execute(newDashboardSession(),
                "SELECT resource_group_template_id, resource_group_id FROM resource_group_managers.system.configured_groups");
    }
}
