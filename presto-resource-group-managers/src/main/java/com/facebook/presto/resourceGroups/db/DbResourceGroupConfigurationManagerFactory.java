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

import com.facebook.presto.resourceGroups.systemtables.QueryQueueCache;
import com.facebook.presto.resourceGroups.systemtables.ResourceGroupInfoHolder;
import com.facebook.presto.spi.classloader.ThreadContextClassLoader;
import com.facebook.presto.spi.memory.ClusterMemoryPoolManager;
import com.facebook.presto.spi.resourceGroups.ResourceGroupConfigurationManager;
import com.facebook.presto.spi.resourceGroups.ResourceGroupConfigurationManagerContext;
import com.facebook.presto.spi.resourceGroups.ResourceGroupConfigurationManagerFactory;
import com.google.inject.Injector;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.json.JsonModule;

import java.util.Map;

import static java.util.Objects.requireNonNull;

public class DbResourceGroupConfigurationManagerFactory
        implements ResourceGroupConfigurationManagerFactory
{
    private final ClassLoader classLoader;
    private final QueryQueueCache queryQueueCache;
    private final ResourceGroupInfoHolder resourceGroupInfoHolder;
    private final ConfigurationNotifier configurationNotifier;

    public DbResourceGroupConfigurationManagerFactory(ClassLoader classLoader, QueryQueueCache queryQueueCache, ResourceGroupInfoHolder resourceGroupInfoHolder, ConfigurationNotifier configurationNotifier)
    {
        this.classLoader = requireNonNull(classLoader, "classLoader is null");
        this.queryQueueCache = requireNonNull(queryQueueCache, "queryQueueCache is null");
        this.resourceGroupInfoHolder = requireNonNull(resourceGroupInfoHolder, "resourceGroupInfoHolder is null");
        this.configurationNotifier = requireNonNull(configurationNotifier, "configurationNotifier is null");
    }

    @Override
    public String getName()
    {
        return "db";
    }

    @Override
    public ResourceGroupConfigurationManager create(Map<String, String> config, ResourceGroupConfigurationManagerContext context)
    {
        try (ThreadContextClassLoader ignored = new ThreadContextClassLoader(classLoader)) {
            Bootstrap app = new Bootstrap(
                    new JsonModule(),
                    new DbResourceGroupsModule(),
                    binder -> binder.bind(QueryQueueCache.class).toInstance(queryQueueCache),
                    binder -> binder.bind(ResourceGroupInfoHolder.class).toInstance(resourceGroupInfoHolder),
                    binder -> binder.bind(ConfigurationNotifier.class).toInstance(configurationNotifier),
                    binder -> binder.bind(ClusterMemoryPoolManager.class).toInstance(context.getMemoryPoolManager())
            );

            Injector injector = app
                    .strictConfig()
                    .doNotInitializeLogging()
                    .setRequiredConfigurationProperties(config)
                    .initialize();
            return injector.getInstance(DbResourceGroupConfigurationManager.class);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
