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

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.Mapper;

import java.util.List;

public interface TableInfoDao
{
    @SqlUpdate("CREATE TABLE IF NOT EXISTS table_stats(\n" +
            "  node_id VARCHAR(128) NOT NULL\n" +
            "  schema_name VARCHAR(256) NOT NULL,\n" +
            "  table_name VARCHAR(256) NOT NULL,\n" +
            "  size bigint NOT NULL")
    void createTableInfo();

    @SqlQuery("SELECT node_id, schema_name, table_name, size FROM table_stats")
    @Mapper(TableInfoRow.Mapper.class)
    List<TableInfoRow> getTableInfo();

    @SqlUpdate("INSERT INTO table_stats (node_id, schema_name, table_name, size) VALUES(:node_id, :schema_name, :table_name, :size)")
    void insertTableInfo(
            @Bind("node_id") String nodeId,
            @Bind("schema_name") String schemaName,
            @Bind("table_name") String tableName,
            @Bind("size") long size
    );

    @SqlUpdate("UPDATE table_stats SET size = size + :size WHERE node_id = :node_id and schema_name = :schema_name and table_name = :table_name")
    void addSize(
            @Bind("node_id") String nodeId,
            @Bind("schema_name") String schemaName,
            @Bind("table_name") String tableName,
            @Bind("size") long size
    );

    @SqlUpdate("DELETE FROM table_stats WHERE schema_name = :schema_name and table_name = :table_name")
    void removeTable(
            @Bind("schema_name") String schemaName,
            @Bind("table_name") String tableName
    );
}
