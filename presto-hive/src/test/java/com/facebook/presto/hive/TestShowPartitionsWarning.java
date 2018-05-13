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
package com.facebook.presto.hive;

import com.facebook.presto.tests.DistributedQueryRunner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static com.facebook.presto.TestWarningsUtil.assertWarnings;
import static com.facebook.presto.spi.StandardWarningCode.SHOW_PARTITIONS_DEPRECATED;

@Test(singleThreaded = true)
public class TestShowPartitionsWarning
{
    private DistributedQueryRunner queryRunner;

    @BeforeClass
    public void setUp()
            throws Exception
    {
        queryRunner = HiveQueryRunner.createQueryRunner();
    }

    @AfterClass(alwaysRun = true)
    public void tearDown()
    {
        queryRunner.close();
    }

    @Test
    public void testShowPartitions()
    {
        try {
            queryRunner.execute("CREATE TABLE test_show_partitions_table WITH (partitioned_by=ARRAY ['j']) AS " +
                    "SELECT * FROM (VALUES (CAST (1 AS BIGINT), CAST (2 AS BIGINT))) t(i, j)");
            assertWarnings(queryRunner, "SHOW PARTITIONS FROM test_show_partitions_table", ImmutableMap.of(), ImmutableList.of(SHOW_PARTITIONS_DEPRECATED.toWarningCode()));
        }
        finally {
            queryRunner.execute("DROP TABLE IF EXISTS test_show_partitions_table");
        }
    }
}
