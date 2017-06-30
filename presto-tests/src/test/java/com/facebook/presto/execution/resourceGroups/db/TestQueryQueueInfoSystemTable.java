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

import com.facebook.presto.Session;
import com.facebook.presto.execution.QueryManager;
import com.facebook.presto.execution.TestingSessionFactory;
import com.facebook.presto.resourceGroups.db.H2ResourceGroupsDao;
import com.facebook.presto.spi.QueryId;
import com.facebook.presto.testing.MaterializedResult;
import com.facebook.presto.testing.MaterializedRow;
import com.facebook.presto.tests.DistributedQueryRunner;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.Set;

import static com.facebook.presto.execution.QueryState.QUEUED;
import static com.facebook.presto.execution.QueryState.RUNNING;
import static com.facebook.presto.execution.TestQueryRunnerUtil.waitForQueryState;
import static com.facebook.presto.execution.resourceGroups.db.H2TestUtil.adminSession;
import static com.facebook.presto.execution.resourceGroups.db.H2TestUtil.createQueryRunner;
import static com.facebook.presto.execution.resourceGroups.db.H2TestUtil.dashboardSession;
import static com.facebook.presto.execution.resourceGroups.db.H2TestUtil.getDao;
import static com.facebook.presto.execution.resourceGroups.db.H2TestUtil.getDbConfigUrl;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestQueryQueueInfoSystemTable
{
    private static final String QUEUE_INFO_QUERY = "SELECT root_group_id," +
            "resource_group_id," +
            "approximate_order," +
            "query_id," +
            "is_queued" +
            " FROM system.runtime.query_queues";

    // Copy of TestQueues with tests for db reconfiguration of resource groups
    private static final String LONG_LASTING_QUERY = "SELECT COUNT(*) FROM lineitem";

    @Test(timeOut = 60_000)
    public void testQueryQueuesSystemTable()
            throws Exception
    {
        String dbConfigUrl = getDbConfigUrl();
        H2ResourceGroupsDao dao = getDao(dbConfigUrl);

        try (DistributedQueryRunner queryRunner = createQueryRunner(dbConfigUrl, dao)) {
            QueryManager queryManager = queryRunner.getCoordinator().getQueryManager();
            dao.updateResourceGroup(5, "dashboard-${USER}", "1MB", 3, 1, null, null, null, null, null, null, null, 3L);
            MILLISECONDS.sleep(2000);
            // submit first "dashboard" query
            QueryId firstDashboardQuery = createQuery(queryRunner, dashboardSession(), LONG_LASTING_QUERY);

            // wait for the first "dashboard" query to start
            waitForQueryState(queryRunner, firstDashboardQuery, RUNNING);
            assertEquals(queryManager.getStats().getRunningQueries(), 1);
            // submit second "dashboard" query

            QueryId secondDashboardQuery = createQuery(queryRunner, dashboardSession(), LONG_LASTING_QUERY);
            MILLISECONDS.sleep(2000);
            // wait for the second "dashboard" query to be queued ("dashboard.${USER}" queue strategy only allows one "dashboard" query to be accepted for execution)
            waitForQueryState(queryRunner, secondDashboardQuery, QUEUED);

            assertEquals(queryManager.getStats().getRunningQueries(), 1);

            QueryId thirdDashboardQuery = createQuery(queryRunner, dashboardSession(), LONG_LASTING_QUERY);
            MILLISECONDS.sleep(2000);
            // wait for the second "dashboard" query to be queued ("dashboard.${USER}" queue strategy only allows one "dashboard" query to be accepted for execution)
            waitForQueryState(queryRunner, thirdDashboardQuery, QUEUED);
            assertEquals(queryManager.getStats().getRunningQueries(), 1);

            // Allow some time to repopulate QueryQueueInfo cache
            MILLISECONDS.sleep(2000);

            // Get contents of query queue info system table
            Set<Long> ordinals = new HashSet<>();
            int queuedCount = 0;
            int runningCount = 0;
            Session adminSession = adminSession();
            MaterializedResult result = queryRunner.execute(adminSession, QUEUE_INFO_QUERY);
            for (MaterializedRow row : result.getMaterializedRows()) {
                if (row.getField(0).toString().equals("admin")) {
                    continue;
                }
                assertEquals(row.getField(0).toString(), "global");
                String resourceGroupId = row.getField(1).toString();
                // Only filter for dashboard queries
                if (!resourceGroupId.contains("dashboard")) {
                    continue;
                }
                Boolean isQueued = (Boolean) row.getField(4);
                if (isQueued) {
                    queuedCount++;
                }
                else {
                    runningCount++;
                }
                Long ordinal = (Long) row.getField(2);
                ordinals.add(ordinal);
            }
            assertEquals(queuedCount, 2);
            assertEquals(runningCount, 1);
            assertTrue(ordinals.contains(1L));
            assertTrue(ordinals.contains(2L));
        }
    }

    private static QueryId createQuery(DistributedQueryRunner queryRunner, Session session, String sql)
    {
        return queryRunner.getCoordinator().getQueryManager().createQuery(new TestingSessionFactory(session), sql).getQueryId();
    }
}
