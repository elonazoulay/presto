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

import com.facebook.presto.execution.QueryManager;
import com.facebook.presto.execution.QueryState;
import com.facebook.presto.spi.QueryId;
import com.facebook.presto.spi.resourceGroups.ResourceGroupId;
import com.facebook.presto.spi.resourceGroups.ResourceGroupInfo;
import com.facebook.presto.tests.DistributedQueryRunner;
import com.google.common.collect.ImmutableSet;
import org.testng.annotations.Test;

import java.util.concurrent.TimeUnit;

import static com.facebook.presto.execution.QueryState.FAILED;
import static com.facebook.presto.execution.QueryState.QUEUED;
import static com.facebook.presto.execution.QueryState.RUNNING;
import static com.facebook.presto.execution.resourceGroups.db.H2QueryRunner.HUGE_MEMORY_QUERY;
import static com.facebook.presto.execution.resourceGroups.db.H2QueryRunner.LONG_LASTING_QUERY;
import static com.facebook.presto.execution.resourceGroups.db.H2QueryRunner.cancelQuery;
import static com.facebook.presto.execution.resourceGroups.db.H2QueryRunner.createQuery;
import static com.facebook.presto.execution.resourceGroups.db.H2QueryRunner.createQueryRunner;
import static com.facebook.presto.execution.resourceGroups.db.H2QueryRunner.deleteSelectorQuery;
import static com.facebook.presto.execution.resourceGroups.db.H2QueryRunner.getSimpleQueryRunner;
import static com.facebook.presto.execution.resourceGroups.db.H2QueryRunner.insertSelectorQuery;
import static com.facebook.presto.execution.resourceGroups.db.H2QueryRunner.newDashboardSession;
import static com.facebook.presto.execution.resourceGroups.db.H2QueryRunner.newRejectionSession;
import static com.facebook.presto.execution.resourceGroups.db.H2QueryRunner.newSession;
import static com.facebook.presto.execution.resourceGroups.db.H2QueryRunner.updateResourceGroupQuery;
import static com.facebook.presto.execution.resourceGroups.db.H2QueryRunner.waitForQueryState;
import static com.facebook.presto.spi.StandardErrorCode.EXCEEDED_MEMORY_LIMIT;
import static com.facebook.presto.spi.StandardErrorCode.QUERY_REJECTED;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.testng.Assert.assertEquals;

// run single threaded to avoid creating multiple query runners at once
@Test(singleThreaded = true)
public class TestQueues
{
    // Copy of TestQueues with tests for db reconfiguration of resource groups
    @Test(timeOut = 60_000)
    public void testRunningQuery()
            throws Exception
    {
        try (DistributedQueryRunner queryRunner = getSimpleQueryRunner()) {
            queryRunner.execute("SELECT COUNT(*), clerk FROM orders GROUP BY clerk");
            while (true) {
                ResourceGroupInfo global = queryRunner.getCoordinator().getResourceGroupManager().get().getResourceGroupInfo(new ResourceGroupId(new ResourceGroupId("global"), "bi-user"));
                if (global.getSoftMemoryLimit().toBytes() > 0) {
                    break;
                }
                TimeUnit.SECONDS.sleep(2);
            }
        }
    }

    @Test(timeOut = 240_000)
    public void testBasic()
            throws Exception
    {
        try (DistributedQueryRunner queryRunner = createQueryRunner()) {
            QueryManager queryManager = queryRunner.getCoordinator().getQueryManager();
            // submit first "dashboard" query
            QueryId firstDashboardQuery = createQuery(queryRunner, newDashboardSession(), LONG_LASTING_QUERY);
            // wait for the first "dashboard" query to start
            waitForQueryState(queryRunner, firstDashboardQuery, RUNNING);
            assertEquals(queryManager.getStats().getRunningQueries(), 1);
            // submit second "dashboard" query
            QueryId secondDashboardQuery = createQuery(queryRunner, newDashboardSession(), LONG_LASTING_QUERY);
            MILLISECONDS.sleep(2000);
            // wait for the second "dashboard" query to be queued ("dashboard.${USER}" queue strategy only allows one "dashboard" query to be accepted for execution)
            waitForQueryState(queryRunner, secondDashboardQuery, QUEUED);
            assertEquals(queryManager.getStats().getRunningQueries(), 1);
            // Update db to allow for 1 more running query in dashboard resource group
            updateResourceGroupQuery(queryRunner, "global.user-${USER}", "1MB", "1GB", "20GB", 3, 4, null, null, null, null, null, null, null);
            updateResourceGroupQuery(queryRunner, "global.user-${USER}.dashboard-${USER}", "1MB", "1GB", "20GB", 1, 2, null, null, null, null, null, null, null);
            waitForQueryState(queryRunner, secondDashboardQuery, RUNNING);
            QueryId thirdDashboardQuery = createQuery(queryRunner, newDashboardSession(), LONG_LASTING_QUERY);
            waitForQueryState(queryRunner, thirdDashboardQuery, QUEUED);
            assertEquals(queryManager.getStats().getRunningQueries(), 2);
            // submit first non "dashboard" query
            QueryId firstNonDashboardQuery = createQuery(queryRunner, newSession(), LONG_LASTING_QUERY);
            // wait for the first non "dashboard" query to start
            waitForQueryState(queryRunner, firstNonDashboardQuery, RUNNING);
            assertEquals(queryManager.getStats().getRunningQueries(), 3);
            // submit second non "dashboard" query
            QueryId secondNonDashboardQuery = createQuery(queryRunner, newSession(), LONG_LASTING_QUERY);
            // wait for the second non "dashboard" query to start
            waitForQueryState(queryRunner, secondNonDashboardQuery, RUNNING);
            assertEquals(queryManager.getStats().getRunningQueries(), 4);
            // cancel first "dashboard" query, the second "dashboard" query and second non "dashboard" query should start running
            cancelQuery(queryRunner, firstDashboardQuery);
            waitForQueryState(queryRunner, firstDashboardQuery, FAILED);
            waitForQueryState(queryRunner, thirdDashboardQuery, RUNNING);
            // DEL waitForRunningQueryCount(queryRunner, 4);
            // DEL waitForCompleteQueryCount(queryRunner, 1);
            assertEquals(queryManager.getStats().getRunningQueries(), 4);
            // 1 failed query + 2 procedure calls
            assertEquals(queryManager.getStats().getCompletedQueries().getTotalCount(), 3);
        }
    }

    @Test(timeOut = 240_000)
    public void testTwoQueriesAtSameTime()
            throws Exception
    {
        try (DistributedQueryRunner queryRunner = createQueryRunner()) {
            QueryId firstDashboardQuery = createQuery(queryRunner, newDashboardSession(), LONG_LASTING_QUERY);
            QueryId secondDashboardQuery = createQuery(queryRunner, newDashboardSession(), LONG_LASTING_QUERY);

            ImmutableSet<QueryState> queuedOrRunning = ImmutableSet.of(QUEUED, RUNNING);
            waitForQueryState(queryRunner, firstDashboardQuery, RUNNING);
            waitForQueryState(queryRunner, secondDashboardQuery, QUEUED);
        }
    }

    @Test(timeOut = 240_000)
    public void testTooManyQueries()
            throws Exception
    {
        try (DistributedQueryRunner queryRunner = createQueryRunner()) {
            QueryId firstDashboardQuery = createQuery(queryRunner, newDashboardSession(), LONG_LASTING_QUERY);
            waitForQueryState(queryRunner, firstDashboardQuery, RUNNING);

            QueryId secondDashboardQuery = createQuery(queryRunner, newDashboardSession(), LONG_LASTING_QUERY);
            waitForQueryState(queryRunner, secondDashboardQuery, QUEUED);

            QueryId thirdDashboardQuery = createQuery(queryRunner, newDashboardSession(), LONG_LASTING_QUERY);
            waitForQueryState(queryRunner, thirdDashboardQuery, FAILED);

            // Allow one more query to run and resubmit third query
            updateResourceGroupQuery(queryRunner, "global.user-${USER}", "1MB", "1GB", "20GB", 3, 4, null, null, null, null, null, null, null);
            updateResourceGroupQuery(queryRunner, "global.user-${USER}.dashboard-${USER}", "1MB", "20GB", "1GB", 1, 2, null, null, null, null, null, null, null);

            waitForQueryState(queryRunner, secondDashboardQuery, RUNNING);
            thirdDashboardQuery = createQuery(queryRunner, newDashboardSession(), LONG_LASTING_QUERY);
            waitForQueryState(queryRunner, thirdDashboardQuery, QUEUED);

            // Lower running queries in dashboard resource groups and wait until groups are reconfigured
            updateResourceGroupQuery(queryRunner, "global.user-${USER}.dashboard-${USER}", "1MB", "1GB", "20GB", 1, 1, null, null, null, null, null, null, null);

            // Cancel query and verify that third query is still queued
            cancelQuery(queryRunner, firstDashboardQuery);
            waitForQueryState(queryRunner, firstDashboardQuery, FAILED);
            MILLISECONDS.sleep(2000);
            waitForQueryState(queryRunner, thirdDashboardQuery, QUEUED);
        }
    }

    @Test(timeOut = 240_000)
    public void testRejection()
            throws Exception
    {
        try (DistributedQueryRunner queryRunner = createQueryRunner()) {
            // Verify the query cannot be submitted
            QueryId queryId = createQuery(queryRunner, newRejectionSession(), LONG_LASTING_QUERY);
            waitForQueryState(queryRunner, queryId, FAILED);
            QueryManager queryManager = queryRunner.getCoordinator().getQueryManager();
            assertEquals(queryManager.getQueryInfo(queryId).getErrorCode(), QUERY_REJECTED.toErrorCode());
            insertSelectorQuery(queryRunner, "global.user-${USER}.adhoc-${USER}", "user.*", "(?i).*reject.*");

            // Verify the query can be submitted
            queryId = createQuery(queryRunner, newRejectionSession(), LONG_LASTING_QUERY);
            waitForQueryState(queryRunner, queryId, RUNNING);
            deleteSelectorQuery(queryRunner, "global.user-${USER}.adhoc-${USER}", "user.*", "(?i).*reject.*");

            // Verify the query cannot be submitted
            queryId = createQuery(queryRunner, newRejectionSession(), LONG_LASTING_QUERY);
            waitForQueryState(queryRunner, queryId, FAILED);
        }
    }

    @Test(timeOut = 240_000)
    public void testRunningTimeout()
            throws Exception
    {
        try (DistributedQueryRunner queryRunner = createQueryRunner()) {
            updateResourceGroupQuery(queryRunner, "global.user-${USER}.dashboard-${USER}", "1MB", "1GB", "20GB", 1, 1, null, null, null, null, null, null, "3s");
            QueryId firstDashboardQuery = createQuery(queryRunner, newDashboardSession(), LONG_LASTING_QUERY);
            waitForQueryState(queryRunner, firstDashboardQuery, FAILED);
        }
    }

    @Test(timeOut = 240_000)
    public void testQueuedTimeout()
            throws Exception
    {
        try (DistributedQueryRunner queryRunner = createQueryRunner()) {
            updateResourceGroupQuery(queryRunner, "global.user-${USER}.dashboard-${USER}", "1MB", "1GB", "20GB", 1, 1, null, null, null, null, null, "5s", null);
            QueryId firstDashboardQuery = createQuery(queryRunner, newDashboardSession(), LONG_LASTING_QUERY);
            waitForQueryState(queryRunner, firstDashboardQuery, RUNNING);
            QueryId secondDashboardQuery = createQuery(queryRunner, newDashboardSession(), LONG_LASTING_QUERY);
            waitForQueryState(queryRunner, secondDashboardQuery, QUEUED);
            waitForQueryState(queryRunner, secondDashboardQuery, FAILED);
        }
    }

    @Test(timeOut = 240_000)
    public void testHardMemoryLimit()
            throws Exception
    {
        try (DistributedQueryRunner queryRunner = createQueryRunner()) {
            updateResourceGroupQuery(queryRunner, "global.user-${USER}.dashboard-${USER}", "8kB", "10kB", "20GB", 1, 1, null, null, null, null, null, null, null);
            QueryId firstDashboardQuery = createQuery(queryRunner, newDashboardSession(), HUGE_MEMORY_QUERY);
            waitForQueryState(queryRunner, firstDashboardQuery, RUNNING);
            waitForQueryState(queryRunner, firstDashboardQuery, FAILED);
            assertEquals(EXCEEDED_MEMORY_LIMIT.toErrorCode(), queryRunner.getCoordinator().getQueryManager().getQueryInfo(firstDashboardQuery).getErrorCode());
        }
    }

    @Test(timeOut = 240_000)
    public void testMaxMemoryPerQuery()
            throws Exception
    {
        try (DistributedQueryRunner queryRunner = createQueryRunner()) {
            updateResourceGroupQuery(queryRunner, "global.user-${USER}.dashboard-${USER}", "4GB", "4GB", "40kB", 1, 1, null, null, null, null, null, null, null);
            QueryId firstDashboardQuery = createQuery(queryRunner, newDashboardSession(), HUGE_MEMORY_QUERY);
            waitForQueryState(queryRunner, firstDashboardQuery, RUNNING);
            waitForQueryState(queryRunner, firstDashboardQuery, FAILED);
            assertEquals(EXCEEDED_MEMORY_LIMIT.toErrorCode(), queryRunner.getCoordinator().getQueryManager().getQueryInfo(firstDashboardQuery).getErrorCode());
        }
    }
}
