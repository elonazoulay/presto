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

import com.facebook.presto.resourceGroups.ResourceGroupIdTemplate;
import com.facebook.presto.resourceGroups.ResourceGroupSpec;
import com.facebook.presto.resourceGroups.SelectorSpec;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.units.Duration;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;

public class ResourceGroupInfoHolder
{
    private final AtomicReference<List<SelectorSpec>> selectorSpecs = new AtomicReference<>(ImmutableList.of());
    private final AtomicReference<Map<ResourceGroupIdTemplate, ResourceGroupSpec>> resourceGroupSpecs = new AtomicReference<>(ImmutableMap.of());
    private final ConcurrentMap<ResourceGroupIdTemplate, List<ResourceGroupId>> specToGroup = new ConcurrentHashMap<>();
    private final AtomicReference<Optional<Duration>> cpuQuotaPeriod = new AtomicReference<>(Optional.empty());

    public void setSelectorSpecs(List<SelectorSpec> selectorSpecs)
    {
        this.selectorSpecs.set(requireNonNull(selectorSpecs, "selectorSpecs is null"));
    }

    public List<SelectorSpec> getSelectorSpecs()
    {
        return selectorSpecs.get();
    }

    public void setResourceGroupSpecs(Map<ResourceGroupIdTemplate, ResourceGroupSpec> resourceGroupSpecs)
    {
        this.resourceGroupSpecs.set(requireNonNull(ImmutableMap.copyOf(resourceGroupSpecs), "resourceGroupSpecs is null"));
    }

    public Map<ResourceGroupIdTemplate, ResourceGroupSpec> getResourceGroupSpecs()
    {
        return resourceGroupSpecs.get();
    }

    public void addGroupToSpec(ResourceGroupIdTemplate specId, ResourceGroupId groupId)
    {
        specToGroup.computeIfAbsent(specId, v -> new LinkedList<>()).add(groupId);
    }

    public Map<ResourceGroupIdTemplate, List<ResourceGroupId>> getSpecToGroup()
    {
        return ImmutableMap.copyOf(specToGroup);
    }

    public void setCpuQuotaPeriod(Optional<Duration> cpuQuotaPeriod)
    {
        this.cpuQuotaPeriod.set(cpuQuotaPeriod);
    }

    public Optional<Duration> getCpuQuotaPeriod()
    {
        return cpuQuotaPeriod.get();
    }
}
