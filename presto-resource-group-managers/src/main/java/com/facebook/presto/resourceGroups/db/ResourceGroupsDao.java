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
package com.facebook.presto.resourceGroups.db;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.Mapper;
import org.skife.jdbi.v2.sqlobject.stringtemplate.UseStringTemplate3StatementLocator;
import org.skife.jdbi.v2.unstable.BindIn;

import java.util.List;

@UseStringTemplate3StatementLocator
public interface ResourceGroupsDao
{
    @SqlUpdate("CREATE TABLE IF NOT EXISTS resource_groups_global_properties (\n" +
            "  name VARCHAR(128) NOT NULL PRIMARY KEY,\n" +
            "  value VARCHAR(512) NULL,\n" +
            "  CHECK (name in ('cpu_quota_period'))\n" +
            ")")
    void createResourceGroupsGlobalPropertiesTable();

    @SqlQuery("SELECT value FROM resource_groups_global_properties WHERE name = 'cpu_quota_period'")
    @Mapper(ResourceGroupGlobalProperties.Mapper.class)
    List<ResourceGroupGlobalProperties> getResourceGroupGlobalProperties();

    @SqlUpdate("INSERT INTO resource_groups_global_properties (name, value)\n" +
            "  VALUES (:name, :value) ON DUPLICATE KEY UPDATE value = :value")
    void upsertResourceGroupsGlobalProperties(
            @Bind("name") String name,
            @Bind("value") String value
    );

    @SqlUpdate("DELETE FROM resource_groups_global_properties WHERE name = 'cpu_quota_period'")
    void deleteResourceGroupsGlobalProperties();

    @SqlUpdate("CREATE TABLE IF NOT EXISTS resource_groups (\n" +
            "  resource_group_id BIGINT NOT NULL AUTO_INCREMENT,\n" +
            "  name VARCHAR(250) NOT NULL,\n" +
            "  soft_memory_limit VARCHAR(128) NOT NULL,\n" +
            "  hard_memory_limit VARCHAR(128) NOT NULL,\n" +
            "  max_memory_per_query VARCHAR(128) NULL,\n" +
            "  max_queued INT NOT NULL,\n" +
            "  max_running INT NOT NULL,\n" +
            "  scheduling_policy VARCHAR(128) NULL,\n" +
            "  scheduling_weight INT NULL,\n" +
            "  jmx_export BOOLEAN NULL,\n" +
            "  soft_cpu_limit VARCHAR(128) NULL,\n" +
            "  hard_cpu_limit VARCHAR(128) NULL,\n" +
            "  queued_timeout VARCHAR(128) NULL,\n" +
            "  running_timeout VARCHAR(128) NULL,\n" +
            "  parent BIGINT NULL,\n" +
            "  PRIMARY KEY (resource_group_id),\n" +
            "  FOREIGN KEY (parent) REFERENCES resource_groups (resource_group_id)\n" +
            ")")
    void createResourceGroupsTable();

    @SqlQuery("SELECT resource_group_id, name, soft_memory_limit, hard_memory_limit, max_memory_per_query, max_queued, max_running," +
            "  scheduling_policy, scheduling_weight, jmx_export, soft_cpu_limit, hard_cpu_limit, " +
            "  queued_timeout, running_timeout, parent\n" +
            "FROM resource_groups")
    @Mapper(ResourceGroupSpecBuilder.Mapper.class)
    List<ResourceGroupSpecBuilder> getResourceGroups();

    @SqlQuery("SELECT resource_group_id, user_regex, source_regex from selectors")
    @Mapper(SelectorRecord.Mapper.class)
    List<SelectorRecord> getSelectors();

    @SqlUpdate("CREATE TABLE IF NOT EXISTS selectors (\n" +
            "  resource_group_id BIGINT NOT NULL,\n" +
            "  user_regex VARCHAR(512),\n" +
            "  source_regex VARCHAR(512),\n" +
            "  FOREIGN KEY (resource_group_id) REFERENCES resource_groups (resource_group_id)\n" +
            ")")
    void createSelectorsTable();

    @SqlUpdate("INSERT INTO resource_groups\n" +
            "(resource_group_id, name, soft_memory_limit, hard_memory_limit, max_memory_per_query, max_queued, max_running, scheduling_policy, scheduling_weight, jmx_export, soft_cpu_limit, hard_cpu_limit, queued_timeout, running_timeout, parent)\n" +
            "VALUES (:resource_group_id, :name, :soft_memory_limit, :hard_memory_limit, :max_memory_per_query, :max_queued, :max_running, :scheduling_policy, :scheduling_weight, :jmx_export, :soft_cpu_limit, :hard_cpu_limit, :queued_timeout, :running_timeout, :parent)")
    void insertResourceGroup(
            @Bind("resource_group_id") long resourceGroupId,
            @Bind("name") String name,
            @Bind("soft_memory_limit") String softMemoryLimit,
            @Bind("hard_memory_limit") String hardMemoryLimit,
            @Bind("max_memory_per_query") String maxMemoryPerQuery,
            @Bind("max_queued") int maxQueued,
            @Bind("max_running") int maxRunning,
            @Bind("scheduling_policy") String schedulingPolicy,
            @Bind("scheduling_weight") Integer schedulingWeight,
            @Bind("jmx_export") Boolean jmxExport,
            @Bind("soft_cpu_limit") String softCpuLimit,
            @Bind("hard_cpu_limit") String hardCpuLimit,
            @Bind("queued_timeout") String queuedTimeout,
            @Bind("running_timeout") String runningTimeout,
            @Bind("parent") Long parent
    );

    @SqlUpdate("UPDATE resource_groups SET\n" +
            "  resource_group_id = :resource_group_id,\n" +
            "  name = :name,\n" +
            "  soft_memory_limit = :soft_memory_limit,\n" +
            "  hard_memory_limit = :hard_memory_limit,\n" +
            "  max_memory_per_query = :max_memory_per_query,\n" +
            "  max_queued = :max_queued,\n" +
            "  max_running = :max_running,\n" +
            "  scheduling_policy = :scheduling_policy,\n" +
            "  scheduling_weight = :scheduling_weight,\n" +
            "  jmx_export = :jmx_export,\n" +
            "  soft_cpu_limit = :soft_cpu_limit,\n" +
            "  hard_cpu_limit = :hard_cpu_limit,\n" +
            "  queued_timeout = :queued_timeout,\n" +
            "  running_timeout = :running_timeout,\n" +
            "  parent = :parent\n" +
            "WHERE resource_group_id = :resource_group_id")
    void updateResourceGroup(
            @Bind("resource_group_id") long resourceGroupId,
            @Bind("name") String resourceGroup,
            @Bind("soft_memory_limit") String softMemoryLimit,
            @Bind("hard_memory_limit") String hardMemoryLimit,
            @Bind("max_memory_per_query") String maxMemoryPerQuery,
            @Bind("max_queued") int maxQueued,
            @Bind("max_running") int maxRunning,
            @Bind("scheduling_policy") String schedulingPolicy,
            @Bind("scheduling_weight") Integer schedulingWeight,
            @Bind("jmx_export") Boolean jmxExport,
            @Bind("soft_cpu_limit") String softCpuLimit,
            @Bind("hard_cpu_limit") String hardCpuLimit,
            @Bind("queued_timeout") String queuedTimeout,
            @Bind("running_timeout") String runningTimeout,
            @Bind("parent") Long parent);

    @SqlUpdate("DELETE FROM resource_groups WHERE resource_group_id = :resource_group_id")
    void deleteResourceGroup(@Bind("resource_group_id") long resourceGroupId);

    @SqlUpdate("UPDATE resource_groups SET\n" +
        "  scheduling_policy = :scheduling_policy\n" +
        "  WHERE resource_group_id in (<resource_group_ids>)")
    void updateSchedulingPolicy(
        @Bind("scheduling_policy") String schedulingPolicy,
        @BindIn("resource_group_ids") List<Long> resourceGroupIds);

    @SqlUpdate("INSERT INTO selectors\n" +
            "(resource_group_id, user_regex, source_regex)\n" +
            "VALUES (:resource_group_id, :user_regex, :source_regex)")
    void insertSelector(
            @Bind("resource_group_id") long resourceGroupId,
            @Bind("user_regex") String userRegex,
            @Bind("source_regex") String sourceRegex);

    @SqlUpdate("UPDATE selectors SET\n" +
            " resource_group_id = :resource_group_id,\n" +
            "  user_regex = :user_regex,\n" +
            "  source_regex = :source_regex\n" +
            "WHERE resource_group_id = :resource_group_id\n" +
            "  AND ((user_regex IS NULL AND :old_user_regex IS NULL) OR user_regex = :old_user_regex)\n" +
            "  AND ((source_regex IS NULL AND :old_source_regex IS NULL) OR source_regex = :old_source_regex)")
    void updateSelector(
            @Bind("resource_group_id") long resourceGroupId,
            @Bind("user_regex") String newUserRegex,
            @Bind("source_regex") String newSourceRegex,
            @Bind("old_user_regex") String oldUserRegex,
            @Bind("old_source_regex") String oldSourceRegex);

    @SqlUpdate("DELETE FROM selectors WHERE resource_group_id = :resource_group_id\n" +
            " AND ((user_regex IS NULL AND :user_regex IS NULL) OR user_regex = :user_regex)\n" +
            " AND ((source_regex IS NULL AND :source_regex IS NULL) OR source_regex = :source_regex)")
    void deleteSelector(
            @Bind("resource_group_id") long resourceGroupId,
            @Bind("user_regex") String userRegex,
            @Bind("source_regex") String sourceRegex);

    @SqlUpdate("DELETE FROM selectors WHERE resource_group_id = :resource_group_id")
    void deleteSelectors(@Bind("resource_group_id") long resourceGroup);
}
