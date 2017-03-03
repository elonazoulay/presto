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
package com.facebook.presto.plugin.turbonium.config.db;

import org.h2.jdbcx.JdbcDataSource;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.IDBI;

import javax.inject.Inject;
import javax.inject.Provider;

import static com.facebook.presto.plugin.turbonium.config.db.DatabaseUtil.onDemandDao;
import static java.util.Objects.requireNonNull;

public class H2DaoProvider
        implements Provider<MemoryConfigDao>
{
    private final MemoryConfigDao dao;

    @Inject
    public H2DaoProvider(MemoryDbConfig dbConfig)
    {
        requireNonNull(dbConfig, "dbConfig is null");
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL(dbConfig.getConfigDbUrl());
        IDBI dbi = new DBI(dataSource);
        this.dao = onDemandDao(dbi, MemoryConfigDao.class);
    }

    @Override
    public MemoryConfigDao get()
    {
        return dao;
    }
}
