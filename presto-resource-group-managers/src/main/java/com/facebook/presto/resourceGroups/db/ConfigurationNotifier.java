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

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkState;
import static java.lang.Math.toIntExact;
import static java.util.Objects.requireNonNull;

public class ConfigurationNotifier
{
    private final AtomicReference<DbResourceGroupConfigurationManager> configurationManager = new AtomicReference<>();

    /*
     * This object is used across injectors, need to set configuration manager after it is instantiated
     */

    public void setConfigurationManager(DbResourceGroupConfigurationManager configurationManager)
    {
        checkState(this.configurationManager.compareAndSet(null, configurationManager), "configuration manager already set");
    }

    public synchronized void addResourceGroup(
            String resourceGroupIdTemplate,
            String softMemoryLimit,
            String hardMemoryLimit,
            String maxMemoryPerQuery,
            long maxQueued,
            long maxRunning,
            String schedulingPolicy,
            Long schedulingWeight,
            Boolean jmxExport,
            String softCpuLimit,
            String hardCpuLimit,
            String queuedTimeout,
            String runningTimeout
    )
    {
        requireNonNull(configurationManager.get(), "configuration manager is not set");
        configurationManager.get().addResourceGroup(
                resourceGroupIdTemplate,
                softMemoryLimit,
                hardMemoryLimit,
                maxMemoryPerQuery,
                toIntExact(maxQueued),
                toIntExact(maxRunning),
                Optional.ofNullable(schedulingPolicy),
                Optional.ofNullable((schedulingWeight == null) ? null : toIntExact(schedulingWeight)),
                Optional.ofNullable(jmxExport),
                Optional.ofNullable(softCpuLimit),
                Optional.ofNullable(hardCpuLimit),
                Optional.ofNullable(queuedTimeout),
                Optional.ofNullable(runningTimeout)
        );
    }

    public synchronized void alterResourceGroup(
            String resourceGroupIdTemplate,
            String softMemoryLimit,
            String hardMemoryLimit,
            String maxMemoryPerQuery,
            long maxQueued,
            long maxRunning,
            String schedulingPolicy,
            Long schedulingWeight,
            Boolean jmxExport,
            String softCpuLimit,
            String hardCpuLimit,
            String queuedTimeout,
            String runningTimeout
    )
    {
        requireNonNull(configurationManager.get(), "configuration manager is not set");
        configurationManager.get().alterResourceGroup(
                resourceGroupIdTemplate,
                softMemoryLimit,
                hardMemoryLimit,
                maxMemoryPerQuery,
                toIntExact(maxQueued),
                toIntExact(maxRunning),
                Optional.ofNullable(schedulingPolicy),
                Optional.ofNullable((schedulingWeight == null) ? null : toIntExact(schedulingWeight)),
                Optional.ofNullable(jmxExport),
                Optional.ofNullable(softCpuLimit),
                Optional.ofNullable(hardCpuLimit),
                Optional.ofNullable(queuedTimeout),
                Optional.ofNullable(runningTimeout)
        );
    }

    public synchronized void removeResourceGroup(
            String resourceGroupIdTemplate
    )
    {
        requireNonNull(configurationManager.get(), "configuration manager is not set");
        configurationManager.get().removeResourceGroup(resourceGroupIdTemplate);
    }

    public void addSelector(String resourceGroupIdTemplate, String userRegex, String sourceRegex)
    {
        requireNonNull(configurationManager.get(), "configuration manager is not set");
        configurationManager.get().addSelector(resourceGroupIdTemplate, userRegex, sourceRegex);
    }

    public void alterSelector(String resourceGroupIdTemplate, String oldUserRegex, String oldSourceRegex, String userRegex, String sourceRegex)
    {
        requireNonNull(configurationManager.get(), "configuration manager is not set");
        configurationManager.get().alterSelector(resourceGroupIdTemplate, oldUserRegex, oldSourceRegex, userRegex, sourceRegex);
    }

    public void removeSelector(String resourceGroupIdTemplate, String userRegex, String sourceRegex)
    {
        requireNonNull(configurationManager.get(), "configuration manager is not set");
        configurationManager.get().removeSelector(resourceGroupIdTemplate, userRegex, sourceRegex);
    }

    public void reload()
    {
        requireNonNull(configurationManager.get(), "configuration manager is not set");
        configurationManager.get().load();
    }

    public void setCpuQuotaPeriod(String cpuQuotaPeriod)
    {
        requireNonNull(configurationManager.get(), "configuration manager is not set");
        configurationManager.get().setCpuQuotaPeriod(cpuQuotaPeriod);
    }
}
