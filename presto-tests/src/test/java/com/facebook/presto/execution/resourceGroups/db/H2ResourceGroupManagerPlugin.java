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

import com.facebook.presto.resourceGroups.db.ConfigurationNotifier;
import com.facebook.presto.resourceGroups.systemtables.QueryQueueCache;
import com.facebook.presto.resourceGroups.systemtables.ResourceGroupInfoHolder;
import com.facebook.presto.resourceGroups.systemtables.ResourceGroupsConnectorFactory;
import com.facebook.presto.spi.Plugin;
import com.facebook.presto.spi.connector.ConnectorFactory;
import com.facebook.presto.spi.resourceGroups.ResourceGroupConfigurationManagerFactory;
import com.google.common.collect.ImmutableList;

import static com.google.common.base.MoreObjects.firstNonNull;

public class H2ResourceGroupManagerPlugin
        implements Plugin
{
    private final QueryQueueCache queryQueueCache = new QueryQueueCache();
    private final ResourceGroupInfoHolder resourceGroupInfoHolder = new ResourceGroupInfoHolder();
    private final ConfigurationNotifier configurationNotifier = new ConfigurationNotifier();

    @Override
    public Iterable<ResourceGroupConfigurationManagerFactory> getResourceGroupConfigurationManagerFactories()
    {
        return ImmutableList.of(
                new H2ResourceGroupConfigurationManagerFactory(getClassLoader(), queryQueueCache, resourceGroupInfoHolder, configurationNotifier));
    }

    @Override
    public Iterable<ConnectorFactory> getConnectorFactories()
    {
        return ImmutableList.of(new ResourceGroupsConnectorFactory(queryQueueCache, resourceGroupInfoHolder, configurationNotifier));
    }

    private static ClassLoader getClassLoader()
    {
        return firstNonNull(Thread.currentThread().getContextClassLoader(), H2ResourceGroupManagerPlugin.class.getClassLoader());
    }
}
