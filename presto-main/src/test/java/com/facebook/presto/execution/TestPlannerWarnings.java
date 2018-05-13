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
package com.facebook.presto.execution;

import com.facebook.presto.Session.SessionBuilder;
import com.facebook.presto.testing.LocalQueryRunner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static com.facebook.presto.TestWarningsUtil.assertPlannerWarnings;
import static com.facebook.presto.spi.StandardWarningCode.SHOW_PARTITIONS_DEPRECATED;
import static com.facebook.presto.testing.TestingSession.testSessionBuilder;

public class TestPlannerWarnings
{
    private LocalQueryRunner queryRunner;

    @BeforeClass
    public void setUp()
            throws Exception
    {
        SessionBuilder sessionBuilder = testSessionBuilder()
                .setCatalog("tpch")
                .setSchema("tiny");
        queryRunner = new LocalQueryRunner(sessionBuilder.build());
    }

    @AfterClass(alwaysRun = true)
    public void tearDown()
    {
        queryRunner.close();
    }

    @Test
    public void testShowPartitions()
    {
        assertPlannerWarnings(queryRunner, "SHOW PARTITIONS FROM orders", ImmutableMap.of(), ImmutableList.of(SHOW_PARTITIONS_DEPRECATED.toWarningCode()));
    }
}
