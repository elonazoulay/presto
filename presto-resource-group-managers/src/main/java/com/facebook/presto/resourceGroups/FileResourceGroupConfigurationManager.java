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
package com.facebook.presto.resourceGroups;

import com.facebook.presto.resourceGroups.systemtables.ResourceGroupConfigurationInfo;
import com.facebook.presto.spi.memory.ClusterMemoryPoolManager;
import com.facebook.presto.spi.resourceGroups.ResourceGroup;
import com.facebook.presto.spi.resourceGroups.ResourceGroupSelector;
import com.facebook.presto.spi.resourceGroups.SelectionContext;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import io.airlift.json.JsonCodec;
import io.airlift.units.Duration;

import javax.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;

public class FileResourceGroupConfigurationManager
        extends AbstractResourceConfigurationManager
{
    private final AtomicReference<List<ResourceGroupSpec>> rootGroups = new AtomicReference(ImmutableList.of());
    private final AtomicReference<List<ResourceGroupSelector>> selectors = new AtomicReference(ImmutableList.of());
    private final AtomicReference<Optional<Duration>> cpuQuotaPeriod = new AtomicReference<>(Optional.empty());
    private final JsonCodec<ManagerSpec> codec;
    private final Path configPath;

    @Inject
    public FileResourceGroupConfigurationManager(ClusterMemoryPoolManager memoryPoolManager, FileResourceGroupConfig config, JsonCodec<ManagerSpec> codec, ResourceGroupConfigurationInfo configurationInfo)
    {
        super(memoryPoolManager, configurationInfo);
        requireNonNull(config, "config is null");
        this.codec = requireNonNull(codec, "codec is null");
        this.configPath = Paths.get(config.getConfigFile());
        load();
    }

    @Override
    protected ManagerSpec loadInternal()
    {
        ManagerSpec managerSpec;
        try {
            managerSpec = codec.fromJson(Files.readAllBytes(configPath));
        }
        catch (IOException e) {
            throw Throwables.propagate(e);
        }
        this.cpuQuotaPeriod.set(managerSpec.getCpuQuotaPeriod());
        validateRootGroups(managerSpec);
        this.rootGroups.set(managerSpec.getRootGroups());
        this.selectors.set(buildSelectors(managerSpec));
        return managerSpec;
    }

    @Override
    protected Optional<Duration> getCpuQuotaPeriod()
    {
        return cpuQuotaPeriod.get();
    }

    @Override
    protected List<ResourceGroupSpec> getRootGroups()
    {
        return rootGroups.get();
    }

    @Override
    public void configure(ResourceGroup group, SelectionContext context)
    {
        Map.Entry<ResourceGroupIdTemplate, ResourceGroupSpec> entry = getMatchingSpec(group, context);
        configureGroup(group, entry.getValue(), entry.getKey());
    }

    @Override
    public List<ResourceGroupSelector> getSelectors()
    {
        return selectors.get();
    }
}
