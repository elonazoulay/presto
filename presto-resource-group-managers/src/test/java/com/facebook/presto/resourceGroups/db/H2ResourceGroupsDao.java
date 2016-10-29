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
import org.skife.jdbi.v2.sqlobject.SqlUpdate;

public interface H2ResourceGroupsDao
        extends ResourceGroupsDao
{
    @Override
    @SqlUpdate("MERGE INTO resource_groups_global_properties\n" +
            "(name, value) VALUES (:name, :value)")
    void upsertResourceGroupsGlobalProperties(
            @Bind("name") String name,
            @Bind("value") String value);

    @SqlUpdate("UPDATE resource_groups_global_properties SET name = :name")
    void updateResourceGroupsGlobalProperties(@Bind("name") String name);
}
