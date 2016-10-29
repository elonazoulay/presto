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
package com.facebook.presto.resourceGroups.systemtables;

import com.facebook.presto.resourceGroups.db.ConfigurationNotifier;
import com.facebook.presto.spi.procedure.Procedure;
import com.facebook.presto.spi.procedure.Procedure.Argument;
import com.google.common.collect.ImmutableList;

import javax.inject.Inject;

import static com.facebook.presto.resourceGroups.systemtables.Util.methodHandle;
import static com.facebook.presto.spi.type.StandardTypes.VARCHAR;

public class RemoveSelectorProcedure
{
    private final ConfigurationNotifier configurationNotifier;

    @Inject
    public RemoveSelectorProcedure(ConfigurationNotifier configurationNotifier)
    {
        this.configurationNotifier = configurationNotifier;
    }

    public Procedure getProcedure()
    {
        return new Procedure(
            "system",
            "remove_selector",
            ImmutableList.of(
                    new Argument("resource_group_id_template", VARCHAR),
                    new Argument("user_regex", VARCHAR),
                    new Argument("source_regex", VARCHAR)),
            methodHandle(ConfigurationNotifier.class, "removeSelector",
                    String.class,
                    String.class,
                    String.class).bindTo(configurationNotifier));
    }
}
