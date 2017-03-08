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

import com.facebook.presto.spi.QueryId;
import com.facebook.presto.tests.DistributedQueryRunner;
import org.testng.annotations.Test;

import static com.facebook.presto.execution.QueryState.FAILED;
import static com.facebook.presto.execution.QueryState.QUEUED;
import static com.facebook.presto.execution.QueryState.RUNNING;
import static com.facebook.presto.execution.resourceGroups.db.H2QueryRunner.LONG_LASTING_QUERY;
import static com.facebook.presto.execution.resourceGroups.db.H2QueryRunner.cancelQuery;
import static com.facebook.presto.execution.resourceGroups.db.H2QueryRunner.createQuery;
import static com.facebook.presto.execution.resourceGroups.db.H2QueryRunner.createQueryRunner;
import static com.facebook.presto.execution.resourceGroups.db.H2QueryRunner.insertResourceGroupQuery;
import static com.facebook.presto.execution.resourceGroups.db.H2QueryRunner.insertSelectorQuery;
import static com.facebook.presto.execution.resourceGroups.db.H2QueryRunner.newSessionWithSource;
import static com.facebook.presto.execution.resourceGroups.db.H2QueryRunner.waitForQueryState;

public class TestWeightedFifoSchedulingPolicy
{
    @Test(timeOut = 240_000)
    public void testWeightedFifoSchedulingPolicy()
            throws Exception
    {
        try (DistributedQueryRunner queryRunner = createQueryRunner()) {
            setupWeightedFifo(queryRunner);
            // Create 2 filler queries so other queries can be queued
            QueryId filler1 = createQuery(queryRunner, newSessionWithSource("etl-hi"), LONG_LASTING_QUERY);
            QueryId filler2 = createQuery(queryRunner, newSessionWithSource("etl-hi"), LONG_LASTING_QUERY);
            waitForQueryState(queryRunner, filler1, RUNNING);
            waitForQueryState(queryRunner, filler2, RUNNING);
            // Create 2 queries for each resource group
            QueryId etlLo1 = createQuery(queryRunner, newSessionWithSource("etl-lo"), LONG_LASTING_QUERY);
            QueryId etlLo2 = createQuery(queryRunner, newSessionWithSource("etl-lo"), LONG_LASTING_QUERY);
            QueryId etlMed1 = createQuery(queryRunner, newSessionWithSource("etl-med"), LONG_LASTING_QUERY);
            QueryId etlMed2 = createQuery(queryRunner, newSessionWithSource("etl-med"), LONG_LASTING_QUERY);
            QueryId etlHi1 = createQuery(queryRunner, newSessionWithSource("etl-hi"), LONG_LASTING_QUERY);
            QueryId etlHi2 = createQuery(queryRunner, newSessionWithSource("etl-hi"), LONG_LASTING_QUERY);
            // Wait for filler queries to run
            waitForQueryState(queryRunner, filler1, RUNNING);
            waitForQueryState(queryRunner, filler2, RUNNING);
            // Verify all other queries are queued
            waitForQueryState(queryRunner, etlLo1, QUEUED);
            waitForQueryState(queryRunner, etlLo2, QUEUED);
            waitForQueryState(queryRunner, etlMed1, QUEUED);
            waitForQueryState(queryRunner, etlMed2, QUEUED);
            waitForQueryState(queryRunner, etlHi1, QUEUED);
            waitForQueryState(queryRunner, etlHi2, QUEUED);
            //Cancel filler queries and wait for etlHi to begin
            cancelQuery(queryRunner, filler1);
            cancelQuery(queryRunner, filler2);
            waitForQueryState(queryRunner, filler1, FAILED);
            waitForQueryState(queryRunner, filler2, FAILED);
            waitForQueryState(queryRunner, etlHi1, RUNNING);
            waitForQueryState(queryRunner, etlHi2, RUNNING);
            // Verify other queries are queued
            waitForQueryState(queryRunner, etlLo1, QUEUED);
            waitForQueryState(queryRunner, etlLo2, QUEUED);
            waitForQueryState(queryRunner, etlMed1, QUEUED);
            waitForQueryState(queryRunner, etlMed2, QUEUED);
            // Cancel etlHi queries
            cancelQuery(queryRunner, etlHi1);
            cancelQuery(queryRunner, etlHi2);
            waitForQueryState(queryRunner, etlHi1, FAILED);
            waitForQueryState(queryRunner, etlHi2, FAILED);
            // Wait for etlMed queries to run
            waitForQueryState(queryRunner, etlMed1, RUNNING);
            waitForQueryState(queryRunner, etlMed2, RUNNING);
            // Verify etlLo queries are still queued
            waitForQueryState(queryRunner, etlLo1, QUEUED);
            waitForQueryState(queryRunner, etlLo2, QUEUED);
            // Cancel etlMed queries
            cancelQuery(queryRunner, etlMed1);
            cancelQuery(queryRunner, etlMed2);
            waitForQueryState(queryRunner, etlMed1, FAILED);
            waitForQueryState(queryRunner, etlMed2, FAILED);
            // Wait for etlLo queries to run
            waitForQueryState(queryRunner, etlLo1, RUNNING);
            waitForQueryState(queryRunner, etlLo2, RUNNING);
        }
    }

    private static void setupWeightedFifo(DistributedQueryRunner queryRunner)
    {
        insertResourceGroupQuery(queryRunner, "bi", "1MB", "1GB", 100, 2, "weighted_fifo", null, null, null, null, null, null);
        insertResourceGroupQuery(queryRunner, "bi.etl-hi", "1MB", "1GB", 100, 2, "weighted_fifo", 100, null, null, null, null, null);
        insertResourceGroupQuery(queryRunner, "bi.etl-med", "1MB", "1GB", 100, 2, "weighted_fifo", 50, null, null, null, null, null);
        insertResourceGroupQuery(queryRunner, "bi.etl-lo", "1MB", "1GB", 100, 2, "weighted_fifo", 20, null, null, null, null, null);
        insertSelectorQuery(queryRunner, "bi.etl-hi", "user.*", "etl-hi");
        insertSelectorQuery(queryRunner, "bi.etl-med", "user.*", "etl-med");
        insertSelectorQuery(queryRunner, "bi.etl-lo", "user.*", "etl-lo");
    }
}
