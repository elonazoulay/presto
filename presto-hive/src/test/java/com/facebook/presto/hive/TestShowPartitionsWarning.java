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

import com.facebook.presto.Session;
import com.facebook.presto.spi.PrestoWarning;
import com.facebook.presto.spi.WarningCode;
import com.facebook.presto.testing.MaterializedResult;
import com.facebook.presto.tests.DistributedQueryRunner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.intellij.lang.annotations.Language;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.facebook.presto.spi.StandardWarningCode.SHOW_PARTITIONS_DEPRECATED;
import static com.facebook.presto.testing.TestingSession.testSessionBuilder;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static org.testng.Assert.fail;

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
            assertWarnings("SHOW PARTITIONS FROM test_show_partitions_table", ImmutableMap.of(), ImmutableList.of(SHOW_PARTITIONS_DEPRECATED.toWarningCode()));
        }
        finally {
            queryRunner.execute("DROP TABLE IF EXISTS test_show_partitions_table");
        }
    }

    private void assertWarnings(@Language("SQL") String sql, Map<String, String> sessionProperties, List<WarningCode> expectedWarnings)
    {
        Session.SessionBuilder sessionBuilder = testSessionBuilder()
                .setCatalog(queryRunner.getDefaultSession().getCatalog().get())
                .setSchema(queryRunner.getDefaultSession().getSchema().get());
        sessionProperties.forEach(sessionBuilder::setSystemProperty);
        Session testingSession = sessionBuilder.build();
        MaterializedResult result = queryRunner.execute(testingSession, sql);
        Set<WarningCode> warnings = result.getWarnings().stream()
                .map(PrestoWarning::getWarningCode)
                .collect(toImmutableSet());
        for (WarningCode expectedWarning : expectedWarnings) {
            if (!warnings.contains(expectedWarning)) {
                fail("Expected warning: " + expectedWarning);
            }
        }
    }
}
