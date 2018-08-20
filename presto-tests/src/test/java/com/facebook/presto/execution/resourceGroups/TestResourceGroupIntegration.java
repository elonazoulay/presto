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
package com.facebook.presto.execution.resourceGroups;

import com.facebook.presto.resourceGroups.ResourceGroupManagerPlugin;
import com.facebook.presto.server.ResourceGroupInfo;
import com.facebook.presto.spi.resourceGroups.ResourceGroupId;
import com.facebook.presto.tests.DistributedQueryRunner;
import com.facebook.presto.tests.tpch.TpchQueryRunnerBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.airlift.units.DataSize;
import io.airlift.units.Duration;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;

import static com.facebook.presto.spi.resourceGroups.ResourceGroupState.CAN_RUN;
import static com.facebook.presto.spi.resourceGroups.SchedulingPolicy.FAIR;
import static com.facebook.presto.spi.resourceGroups.SchedulingPolicy.QUERY_PRIORITY;
import static com.facebook.presto.testing.TestingSession.testSessionBuilder;
import static io.airlift.testing.Assertions.assertLessThan;
import static io.airlift.units.Duration.nanosSince;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

@Test(singleThreaded = true)
public class TestResourceGroupIntegration
{
    @Test
    public void testMemoryFraction()
            throws Exception
    {
        try (DistributedQueryRunner queryRunner = TpchQueryRunnerBuilder.builder().build()) {
            queryRunner.installPlugin(new ResourceGroupManagerPlugin());
            getResourceGroupManager(queryRunner).setConfigurationManager("file", ImmutableMap.of(
                    "resource-groups.config-file", getResourceFilePath("resource_groups_memory_percentage.json")));

            queryRunner.execute("SELECT COUNT(*), clerk FROM orders GROUP BY clerk");
            waitForGlobalResourceGroup(queryRunner);
        }
    }

    @Test
    public void testPathToRoot()
            throws Exception
    {
        try (DistributedQueryRunner queryRunner = TpchQueryRunnerBuilder.builder().build()) {
            queryRunner.installPlugin(new ResourceGroupManagerPlugin());
            InternalResourceGroupManager<?> manager = getResourceGroupManager(queryRunner);
            manager.setConfigurationManager("file", ImmutableMap.of(
                    "resource-groups.config-file", getResourceFilePath("resource_groups_config_dashboard.json")));

            queryRunner.execute(testSessionBuilder().setCatalog("tpch").setSchema("tiny").setSource("dashboard-foo").build(), "SELECT COUNT(*), clerk FROM orders GROUP BY clerk");
            List<ResourceGroupInfo> path = manager.getPathToRoot(new ResourceGroupId(new ResourceGroupId(new ResourceGroupId("global"), "user-user"), "dashboard-user"));
            assertEquals(path.size(), 3);
            assertTrue(path.get(1).getSubGroups() != null);
            assertEquals(path.get(2).getId(), new ResourceGroupId("global"));
            assertEquals(path.get(2).getHardConcurrencyLimit(), 100);
            assertEquals(path.get(2).getRunningQueries(), null);
        }
    }

    @Test
    public void testGetResourceGroupInfos()
            throws Exception
    {
        try (DistributedQueryRunner queryRunner = TpchQueryRunnerBuilder.builder().build()) {
            queryRunner.installPlugin(new ResourceGroupManagerPlugin());
            InternalResourceGroupManager<?> manager = getResourceGroupManager(queryRunner);
            manager.setConfigurationManager("file", ImmutableMap.of(
                    "resource-groups.config-file", getResourceFilePath("resource_group_config_all_parameters.json")));
            queryRunner.execute(testSessionBuilder().setCatalog("tpch").setSchema("tiny").setSource("dashboard-foo").build(), "SELECT COUNT(*) FROM nation");
            queryRunner.execute(testSessionBuilder().setCatalog("tpch").setSchema("tiny").setSource("adhoc-foo").build(), "SELECT COUNT(*) FROM nation");
            queryRunner.execute(testSessionBuilder().setCatalog("tpch").setSchema("tiny").setSource("test").build(), "SELECT COUNT(*) FROM nation");
            Set<ResourceGroupInfo> resourceGroupInfos = ImmutableSet.copyOf(manager.getAllResourceGroupInfos());
            Set<ResourceGroupInfo> expected = ImmutableSet.<ResourceGroupInfo>builder()
                    .add(new ResourceGroupInfo(
                            new ResourceGroupId("global"),
                            CAN_RUN,
                            FAIR,
                            1,
                            DataSize.valueOf("1MB"),
                            99,
                            100,
                            1000,
                            Duration.valueOf("1h"),
                            Duration.valueOf("3h"),
                            DataSize.valueOf("0B"),
                            0,
                            0,
                            0,
                            null,
                            null))
                    .add(new ResourceGroupInfo(
                            new ResourceGroupId(ImmutableList.of("global", "user-user")),
                            CAN_RUN,
                            QUERY_PRIORITY,
                            1,
                            DataSize.valueOf("1MB"),
                            3,
                            4,
                            3,
                            Duration.valueOf("1h"),
                            Duration.valueOf("3h"),
                            DataSize.valueOf("0B"),
                            0,
                            0,
                            0,
                            null,
                            null))
                    .add(new ResourceGroupInfo(
                            new ResourceGroupId(ImmutableList.of("global", "test")),
                            CAN_RUN,
                            FAIR,
                            1,
                            DataSize.valueOf("1MB"),
                            2,
                            3,
                            3,
                            Duration.valueOf("1h"),
                            Duration.valueOf("3h"),
                            DataSize.valueOf("0B"),
                            0,
                            0,
                            0,
                            null,
                            null))
                    .add(new ResourceGroupInfo(
                            new ResourceGroupId(ImmutableList.of("global", "user-user", "dashboard-user")),
                            CAN_RUN,
                            QUERY_PRIORITY,
                            1,
                            DataSize.valueOf("1MB"),
                            1,
                            2,
                            4,
                            Duration.valueOf("1h"),
                            Duration.valueOf("3h"),
                            DataSize.valueOf("0B"),
                            0,
                            0,
                            0,
                            null,
                            null))
                    .add(new ResourceGroupInfo(
                            new ResourceGroupId(ImmutableList.of("global", "user-user", "adhoc-user")),
                            CAN_RUN,
                            QUERY_PRIORITY,
                            1,
                            DataSize.valueOf("1MB"),
                            2,
                            3,
                            3,
                            Duration.valueOf("1h"),
                            Duration.valueOf("3h"),
                            DataSize.valueOf("0B"),
                            0,
                            0,
                            0,
                            null,
                            null))
                    .build();
            assertEquals(resourceGroupInfos, expected);
        }
    }
    private String getResourceFilePath(String fileName)
    {
        return this.getClass().getClassLoader().getResource(fileName).getPath();
    }

    @Test(enabled = false)
    public static void waitForGlobalResourceGroup(DistributedQueryRunner queryRunner)
            throws InterruptedException
    {
        long startTime = System.nanoTime();
        while (true) {
            SECONDS.sleep(1);
            ResourceGroupInfo global = getResourceGroupManager(queryRunner).getResourceGroupInfo(new ResourceGroupId("global"));
            if (global.getSoftMemoryLimit().toBytes() > 0) {
                break;
            }
            assertLessThan(nanosSince(startTime).roundTo(SECONDS), 60L);
        }
    }

    private static InternalResourceGroupManager<?> getResourceGroupManager(DistributedQueryRunner queryRunner)
    {
        return queryRunner.getCoordinator().getResourceGroupManager()
                .orElseThrow(() -> new IllegalArgumentException("no resource group manager"));
    }
}
