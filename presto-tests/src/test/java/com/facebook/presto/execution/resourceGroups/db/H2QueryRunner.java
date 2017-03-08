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
import com.facebook.presto.execution.QueryState;
import com.facebook.presto.execution.TestingSessionFactory;
import com.facebook.presto.resourceGroups.db.DbResourceGroupConfig;
import com.facebook.presto.resourceGroups.db.H2DaoProvider;
import com.facebook.presto.resourceGroups.db.H2ResourceGroupsDao;
import com.facebook.presto.spi.Plugin;
import com.facebook.presto.spi.QueryId;
import com.facebook.presto.sql.parser.SqlParserOptions;
import com.facebook.presto.testing.MaterializedResult;
import com.facebook.presto.tests.DistributedQueryRunner;
import com.facebook.presto.tests.tpch.TpchQueryRunner;
import com.facebook.presto.tpch.TpchPlugin;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.airlift.log.Logger;
import io.airlift.log.Logging;

import java.util.Map;
import java.util.Random;
import java.util.Set;

import static com.facebook.presto.resourceGroups.db.ResourceGroupGlobalProperties.CPU_QUOTA_PERIOD;
import static com.facebook.presto.testing.TestingSession.testSessionBuilder;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class H2QueryRunner
{
    private static final String RESOURCE_GROUP_SPECS_QUERY = "SELECT resource_group_template_id,\n" +
            "  soft_memory_limit,\n" +
            "  hard_memory_limit,\n" +
            "  max_queued,\n" +
            "  max_running,\n" +
            "  scheduling_policy,\n" +
            "  scheduling_weight,\n" +
            "  jmx_export,\n" +
            "  soft_cpu_limit,\n" +
            "  hard_cpu_limit,\n" +
            "  queued_timeout,\n" +
            "  running_timeout\n" +
            "FROM resource_group_managers.system.resource_group_specs";

    private static final String SELECTOR_SPECS_QUERY = "SELECT resource_group_id,\n" +
            "  user_regex,\n" +
            "  source_regex\n" +
            "FROM resource_group_managers.system.selectors";
    private static final String GLOBAL_PROPERTIES_QUERY = "SELECT name, value FROM resource_group_managers.system.resource_groups_global_properties";
    private static final String NAME = "h2";
    public static final String LONG_LASTING_QUERY = "SELECT COUNT(*) FROM lineitem";
    public static final String HUGE_MEMORY_QUERY = "SELECT COUNT(*) FROM lineitem a join lineitem b on a.orderkey = b.orderkey";

    private H2QueryRunner() {}

    public static DistributedQueryRunner createQueryRunner()
            throws Exception
    {
        String dbConfigUrl = getDbConfigUrl();
        H2ResourceGroupsDao dao = getDao(dbConfigUrl);
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        builder.put("experimental.resource-groups-enabled", "true");
        builder.put("http-server.http.port", "8080");
        Map<String, String> properties = builder.build();
        setup(dao);
        DistributedQueryRunner queryRunner = new DistributedQueryRunner(testSessionBuilder().build(), 2, ImmutableMap.of(), properties, new SqlParserOptions());
        try {
            Plugin h2ResourceGroupManagerPlugin = new H2ResourceGroupManagerPlugin();
            queryRunner.installPlugin(h2ResourceGroupManagerPlugin);
            queryRunner.getCoordinator().getResourceGroupManager().get()
                    .setConfigurationManager(NAME, ImmutableMap.of("resource-groups.config-db-url", dbConfigUrl));
            queryRunner.installPlugin(new TpchPlugin());
            queryRunner.createCatalog("tpch", "tpch");
            queryRunner.createCatalog("resource_group_managers", "resource-group-managers");

            return queryRunner;
        }
        catch (Exception e) {
            queryRunner.close();
            throw e;
        }
    }

    public static DistributedQueryRunner getSimpleQueryRunner()
            throws Exception
    {
        String dbConfigUrl = getDbConfigUrl();
        H2ResourceGroupsDao dao = getDao(dbConfigUrl);
        setup(dao);
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        builder.put("experimental.resource-groups-enabled", "true");
        Map<String, String> properties = builder.build();
        DistributedQueryRunner queryRunner = TpchQueryRunner.createQueryRunner(properties);
        Plugin h2ResourceGroupManagerPlugin = new H2ResourceGroupManagerPlugin();
        queryRunner.installPlugin(h2ResourceGroupManagerPlugin);
        queryRunner.getCoordinator().getResourceGroupManager().get()
                .setConfigurationManager(NAME, ImmutableMap.of("resource-groups.config-db-url", dbConfigUrl));
        return queryRunner;
    }

    private static void setup(H2ResourceGroupsDao dao)
            throws InterruptedException
    {
        dao.upsertResourceGroupsGlobalProperties(CPU_QUOTA_PERIOD, "1h");
        dao.insertResourceGroup(1, "global", "1MB", "1MB", 100, 1000, null, null, null, null, null, null, null, null);
        dao.insertResourceGroup(2, "bi-${USER}", "1MB", "1MB", 3, 2, null, null, null, null, null, null, null, 1L);
        dao.insertResourceGroup(3, "user-${USER}", "1MB", "1MB", 3, 3, null, null, null, null, null, null, null, 1L);
        dao.insertResourceGroup(4, "adhoc-${USER}", "1MB", "1MB", 3, 3, null, null, null, null, null, null, null, 3L);
        dao.insertResourceGroup(5, "dashboard-${USER}", "1MB", "1MB", 1, 1, null, null, null, null, null, null, null, 3L);
        dao.insertResourceGroup(6, "admin", "1MB", "1MB", 3, 3, null, null, null, null, null, null, null, null);
        dao.insertSelector(2, "user.*", "test");
        dao.insertSelector(4, "user.*", "(?i).*adhoc.*");
        dao.insertSelector(5, "user.*", "(?i).*dashboard.*");
        dao.insertSelector(6, "user.*", "(?i).*admin.*");
    }

    private static String getDbConfigUrl()
    {
        Random rnd = new Random();
        return "jdbc:h2:mem:test_" + Math.abs(rnd.nextLong());
    }

    private static H2ResourceGroupsDao getDao(String url)
    {
        DbResourceGroupConfig dbResourceGroupConfig = new DbResourceGroupConfig()
                .setConfigDbUrl(url);
        H2ResourceGroupsDao dao = new H2DaoProvider(dbResourceGroupConfig).get();
        dao.createResourceGroupsTable();
        dao.createSelectorsTable();
        dao.createResourceGroupsGlobalPropertiesTable();
        return dao;
    }

    public static Session newSession()
    {
        return testSessionBuilder()
                .setCatalog("tpch")
                .setSchema("sf100000")
                .setSource("adhoc")
                .build();
    }

    public static Session newDashboardSession()
    {
        return testSessionBuilder()
                .setCatalog("tpch")
                .setSchema("sf100000")
                .setSource("dashboard")
                .build();
    }

    public static Session newRejectionSession()
    {
        return testSessionBuilder()
                .setCatalog("tpch")
                .setSchema("sf100000")
                .setSource("reject")
                .build();
    }

    public static Session newSessionWithSource(String source)
    {
        return testSessionBuilder()
                .setCatalog("tpch")
                .setSchema("sf100000")
                .setSource(source)
                .build();
    }

    public static Session newAdminSession()
    {
        return testSessionBuilder()
                .setCatalog("resource_group_managers")
                .setSchema("system")
                .setSource("admin")
                .build();
    }

    public static QueryId createQuery(DistributedQueryRunner queryRunner, Session session, String sql)
    {
        return queryRunner.getCoordinator().getQueryManager().createQuery(new TestingSessionFactory(session), sql).getQueryId();
    }

    public static void cancelQuery(DistributedQueryRunner queryRunner, QueryId queryId)
    {
        queryRunner.getCoordinator().getQueryManager().cancelQuery(queryId);
    }

    public static void waitForQueryState(DistributedQueryRunner queryRunner, QueryId queryId, QueryState expectedQueryState)
            throws InterruptedException
    {
        waitForQueryState(queryRunner, queryId, ImmutableSet.of(expectedQueryState));
    }

    public static void waitForQueryState(DistributedQueryRunner queryRunner, QueryId queryId, Set<QueryState> expectedQueryStates)
            throws InterruptedException
    {
        QueryManager queryManager = queryRunner.getCoordinator().getQueryManager();
        do {
            MILLISECONDS.sleep(500);
        }
        while (!expectedQueryStates.contains(queryManager.getQueryInfo(queryId).getState()));
    }

    public static MaterializedResult getResourceGroupSpecsQuery(DistributedQueryRunner queryRunner)
    {
        return queryRunner.execute(newAdminSession(), RESOURCE_GROUP_SPECS_QUERY);
    }

    public static void insertResourceGroupQuery(
            DistributedQueryRunner queryRunner,
            String resourceGroupTemplateId,
            String softMemoryLimit,
            String hardMemoryLimit,
            int maxQueued,
            int maxRunning,
            String schedulingPolicy,
            Integer schedulingWeight,
            Boolean jmxExport,
            String softCpuLimit,
            String hardCpuLimit,
            String queuedTimeout,
            String runningTimeout)
    {
        queryRunner.execute(newAdminSession(),
                format("CALL resource_group_managers.system.add_resource_group(%s, %s, %s, %s, %s, %s,%s, %s, %s, %s, %s, %s)",
                        toParam(resourceGroupTemplateId),
                        toParam(softMemoryLimit),
                        toParam(hardMemoryLimit),
                        maxQueued,
                        maxRunning,
                        toParam(schedulingPolicy),
                        schedulingWeight,
                        jmxExport,
                        toParam(softCpuLimit),
                        toParam(hardCpuLimit),
                        toParam(queuedTimeout),
                        toParam(runningTimeout)));
    }

    public static void updateResourceGroupQuery(
            DistributedQueryRunner queryRunner,
            String resourceGroupTemplateId,
            String softMemoryLimit,
            String hardMemoryLimit,
            int maxQueued,
            int maxRunning,
            String schedulingPolicy,
            Integer schedulingWeight,
            Boolean jmxExport,
            String softCpuLimit,
            String hardCpuLimit,
            String queuedTimeout,
            String runningTimeout)
    {
        queryRunner.execute(newAdminSession(),
                format("CALL resource_group_managers.system.alter_resource_group(%s, %s, %s, %s, %s, %s,%s, %s, %s, %s, %s, %s)",
                        toParam(resourceGroupTemplateId),
                        toParam(softMemoryLimit),
                        toParam(hardMemoryLimit),
                        maxQueued,
                        maxRunning,
                        toParam(schedulingPolicy),
                        schedulingWeight,
                        jmxExport,
                        toParam(softCpuLimit),
                        toParam(hardCpuLimit),
                        toParam(queuedTimeout),
                        toParam(runningTimeout)));
    }

    public static void deleteResourceGroupQuery(DistributedQueryRunner queryRunner, String resourceGroupTemplateId)
    {
        queryRunner.execute(newAdminSession(),
                format("CALL resource_group_managers.system.remove_resource_group(%s)", toParam(resourceGroupTemplateId)));
    }

    public static MaterializedResult getSelectorsQuery(DistributedQueryRunner queryRunner)
    {
        return queryRunner.execute(newAdminSession(), SELECTOR_SPECS_QUERY);
    }

    public static void insertSelectorQuery(
            DistributedQueryRunner queryRunner,
            String resourceGroupTemplateId,
            String userRegex,
            String sourceRegex)
    {
        queryRunner.execute(newAdminSession(),
                format("CALL resource_group_managers.system.add_selector(%s, %s, %s)",
                        toParam(resourceGroupTemplateId),
                        toParam(userRegex),
                        toParam(sourceRegex)));
    }

    public static void updateSelectorQuery(
            DistributedQueryRunner queryRunner,
            String resourceGroupTemplateId,
            String oldUserRegex,
            String oldSourceRegex,
            String userRegex,
            String sourceRegex)
    {
        queryRunner.execute(newAdminSession(),
                format("CALL resource_group_managers.system.alter_selector(%s, %s, %s, %s, %s)",
                        toParam(resourceGroupTemplateId),
                        toParam(oldUserRegex),
                        toParam(oldSourceRegex),
                        toParam(userRegex),
                        toParam(sourceRegex)));
    }

    public static void deleteSelectorQuery(
            DistributedQueryRunner queryRunner,
            String resourceGroupTemplateId,
            String userRegex,
            String sourceRegex)
    {
        queryRunner.execute(newAdminSession(),
                format("CALL resource_group_managers.system.remove_selector(%s, %s, %s)",
                        toParam(resourceGroupTemplateId),
                        toParam(userRegex),
                        toParam(sourceRegex)));
    }

    public static MaterializedResult getResourceGroupsGlobalPropertiesQuery(DistributedQueryRunner queryRunner)
    {
        return queryRunner.execute(newAdminSession(), GLOBAL_PROPERTIES_QUERY);
    }

    public static void setCpuQuotaPeriodQuery(
            DistributedQueryRunner queryRunner,
            String cpuQuotaPeriod)
    {
        queryRunner.execute(newAdminSession(),
                format("CALL resource_group_managers.system.set_cpu_quota_period(%s)",
                        toParam(cpuQuotaPeriod)));
    }

    private static String toParam(String value)
    {
        return (value == null) ? null : format("'%s'", value);
    }

    public static void main(String[] args)
            throws Exception
    {
        Logging.initialize();
        DistributedQueryRunner queryRunner = createQueryRunner();
        Logger log = Logger.get(H2QueryRunner.class);
        log.info("======== SERVER STARTED ========");
        log.info("\n====\n%s\n====", queryRunner.getCoordinator().getBaseUrl());
        Thread.sleep(Long.MAX_VALUE);
    }
}
