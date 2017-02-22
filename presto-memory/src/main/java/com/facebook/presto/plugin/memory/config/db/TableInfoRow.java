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

import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class TableInfoRow
{
    private final String nodeId;
    private final String schemaName;
    private final String tableName;
    private final long size;

    public TableInfoRow(String nodeId, String schemaName, String tableName, long size)
    {
        this.nodeId = nodeId;
        this.schemaName = schemaName;
        this.tableName = tableName;
        this.size = size;
    }

    public static class Mapper
        implements ResultSetMapper<TableInfoRow>
    {
        @Override
        public TableInfoRow map(int index, ResultSet resultSet, StatementContext context)
                throws SQLException
        {
            String nodeId = resultSet.getString("node_id");
            String schemaName = resultSet.getString("schema_name");
            String tableName = resultSet.getString("table_name");
            long size = resultSet.getLong("size");
            return new TableInfoRow(nodeId, schemaName, tableName, size);
        }
    }
}
