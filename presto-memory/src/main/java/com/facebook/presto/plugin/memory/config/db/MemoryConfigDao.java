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
package com.facebook.presto.plugin.memory.config.db;

import com.facebook.presto.plugin.memory.MemoryConfig;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.Mapper;

import java.util.List;

public interface MemoryConfigDao
{
    @SqlUpdate("CREATE TABLE IF NOT EXISTS memory_config (\n" +
            "  max_data_per_node BIGINT NOT NULL,\n" +
            "  max_table_size_per_node BIGINT NOT NULL,\n" +
            "  splits_per_node INT NOT NULL\n" +
            ")")
    void createConfigTable();

    @SqlQuery("SELECT max_data_per_node,\n" +
            "  max_table_size_per_node,\n" +
            "  splits_per_node\n" +
            "  FROM memory_config")
    @Mapper(MemoryConfigMapper.class)
    List<MemoryConfig> getMemoryConfig();

    @SqlUpdate("INSERT into memory_config (max_data_per_node, max_table_size_per_node, splits_per_node)\n" +
    "  VALUES(:max_data_per_node, :max_table_size_per_node, :splits_per_node)")
    void insertMemoryConfig(
            @Bind("max_data_per_node") long maxDataPerNode,
            @Bind("max_table_size_per_node") long maxTableSizePerNode,
            @Bind("splits_per_node") int splitsPerNode);

    @SqlUpdate("UPDATE memory_config SET max_data_per_node = :max_data_per_node")
    void updateMaxDataPerNode(@Bind("max_data_per_node") long maxDataPerNode);

    @SqlUpdate("UPDATE memory_config SET max_table_size_per_node = :max_table_size_per_node")
    void updateMaxTableSizePerNode(@Bind("max_table_size_per_node") long maxTableSizePerNode);

    @SqlUpdate("UPDATE memory_config SET splits_per_node = :splits_per_node")
    void updateSplitsPerNode(@Bind("splits_per_node") int splitsPerNode);
}
