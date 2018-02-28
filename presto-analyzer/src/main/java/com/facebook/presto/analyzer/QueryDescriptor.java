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
package com.facebook.presto.analyzer;

import static java.util.Objects.requireNonNull;

public class QueryDescriptor
{
    private final String environment;
    private final String queryId;
    private final String source;
    private final String sql;

    public QueryDescriptor(String environment, String queryId, String source, String sql)
    {
        this.environment = requireNonNull(environment, "environment is null");
        this.queryId = requireNonNull(queryId, "queryId is null");
        this.source = requireNonNull(source, "source is null");
        this.sql = requireNonNull(sql, "sql is null");
    }

    public String getEnvironment()
    {
        return environment;
    }

    public String getQueryId()
    {
        return queryId;
    }

    public String getSource()
    {
        return source;
    }

    public String getSql()
    {
        return sql;
    }
}
