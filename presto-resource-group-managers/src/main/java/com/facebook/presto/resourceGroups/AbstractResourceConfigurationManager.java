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
import com.facebook.presto.spi.memory.MemoryPoolId;
import com.facebook.presto.spi.resourceGroups.QueryType;
import com.facebook.presto.spi.resourceGroups.ResourceGroup;
import com.facebook.presto.spi.resourceGroups.ResourceGroupConfigurationManager;
import com.facebook.presto.spi.resourceGroups.ResourceGroupSelector;
import com.facebook.presto.spi.resourceGroups.SelectionContext;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import io.airlift.units.DataSize;
import io.airlift.units.Duration;

import javax.annotation.concurrent.GuardedBy;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;

import static com.facebook.presto.spi.resourceGroups.SchedulingPolicy.WEIGHTED;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static io.airlift.units.DataSize.Unit.BYTE;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public abstract class AbstractResourceConfigurationManager
        implements ResourceGroupConfigurationManager
{
    @GuardedBy("generalPoolMemoryFraction")
    private final Map<ResourceGroup, Double> generalPoolMemoryFraction = new HashMap<>();
    @GuardedBy("generalPoolMemoryFraction")
    private long generalPoolBytes;

    private final ResourceGroupConfigurationInfo configurationInfo;

    protected abstract Optional<Duration> getCpuQuotaPeriod();
    protected abstract List<ResourceGroupSpec> getRootGroups();

    // Implementations override this method to load internal data structures
    protected abstract ManagerSpec loadInternal();

    // Calls loadInternal and then sets the configuration info
    @VisibleForTesting
    public final ManagerSpec load()
    {
        ManagerSpec managerSpec = loadInternal();
        setConfigurationInfo(managerSpec);
        return managerSpec;
    }

    protected void validateRootGroups(ManagerSpec managerSpec)
    {
        Queue<ResourceGroupSpec> groups = new LinkedList<>(managerSpec.getRootGroups());
        while (!groups.isEmpty()) {
            ResourceGroupSpec group = groups.poll();
            groups.addAll(group.getSubGroups());
            if (group.getSoftCpuLimit().isPresent() || group.getHardCpuLimit().isPresent()) {
                checkArgument(managerSpec.getCpuQuotaPeriod().isPresent(), "cpuQuotaPeriod must be specified to use cpu limits on group: %s", group.getName());
            }
            if (group.getSoftCpuLimit().isPresent()) {
                checkArgument(group.getHardCpuLimit().isPresent(), "Must specify hard CPU limit in addition to soft limit");
                checkArgument(group.getSoftCpuLimit().get().compareTo(group.getHardCpuLimit().get()) <= 0, "Soft CPU limit cannot be greater than hard CPU limit");
            }
            if (group.getSchedulingPolicy().isPresent()) {
                if (group.getSchedulingPolicy().get() == WEIGHTED) {
                    for (ResourceGroupSpec subGroup : group.getSubGroups()) {
                        checkArgument(subGroup.getSchedulingWeight().isPresent(), "Must specify scheduling weight for each sub group when using \"weighted\" scheduling policy");
                    }
                }
                else {
                    for (ResourceGroupSpec subGroup : group.getSubGroups()) {
                        checkArgument(!subGroup.getSchedulingWeight().isPresent(), "Must use \"weighted\" scheduling policy when using scheduling weight");
                    }
                }
            }
        }
    }

    protected List<ResourceGroupSelector> buildSelectors(ManagerSpec managerSpec)
    {
        ImmutableList.Builder<ResourceGroupSelector> selectors = ImmutableList.builder();
        for (SelectorSpec spec : managerSpec.getSelectors()) {
            validateSelectors(managerSpec.getRootGroups(), spec);
            selectors.add(new StaticSelector(spec.getUserRegex(), spec.getSourceRegex(), spec.getQueryType(), spec.getGroup()));
        }
        return selectors.build();
    }

    private void validateSelectors(List<ResourceGroupSpec> groups, SelectorSpec spec)
    {
        spec.getQueryType().ifPresent(this::validateQueryType);
        List<ResourceGroupNameTemplate> selectorGroups = spec.getGroup().getSegments();
        StringBuilder fullyQualifiedGroupName = new StringBuilder();
        while (!selectorGroups.isEmpty()) {
            ResourceGroupNameTemplate groupName = selectorGroups.get(0);
            fullyQualifiedGroupName.append(groupName);
            Optional<ResourceGroupSpec> match = groups
                    .stream()
                    .filter(groupSpec -> groupSpec.getName().equals(groupName))
                    .findFirst();
            if (!match.isPresent()) {
                throw new IllegalArgumentException(format("Selector refers to nonexistent group: %s", fullyQualifiedGroupName.toString()));
            }
            fullyQualifiedGroupName.append(".");
            groups = match.get().getSubGroups();
            selectorGroups = selectorGroups.subList(1, selectorGroups.size());
        }
    }

    private void validateQueryType(String queryType)
    {
        try {
            QueryType.valueOf(queryType.toUpperCase());
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(format("Selector specifies an invalid query type: %s", queryType));
        }
    }

    private void setConfigurationInfo(ManagerSpec managerSpec)
    {
        configurationInfo.setRootGroupSpecs(managerSpec.getRootGroups());
        configurationInfo.setSelectorSpecs(managerSpec.getSelectors());
        configurationInfo.setCpuQuotaPeriod(managerSpec.getCpuQuotaPeriod());
    }

    protected AbstractResourceConfigurationManager(ClusterMemoryPoolManager memoryPoolManager, ResourceGroupConfigurationInfo configurationInfo)
    {
        memoryPoolManager.addChangeListener(new MemoryPoolId("general"), poolInfo -> {
            Map<ResourceGroup, DataSize> memoryLimits = new HashMap<>();
            synchronized (generalPoolMemoryFraction) {
                for (Map.Entry<ResourceGroup, Double> entry : generalPoolMemoryFraction.entrySet()) {
                    double bytes = poolInfo.getMaxBytes() * entry.getValue();
                    // setSoftMemoryLimit() acquires a lock on the root group of its tree, which could cause a deadlock if done while holding the "generalPoolMemoryFraction" lock
                    memoryLimits.put(entry.getKey(), new DataSize(bytes, BYTE));
                }
                generalPoolBytes = poolInfo.getMaxBytes();
            }
            for (Map.Entry<ResourceGroup, DataSize> entry : memoryLimits.entrySet()) {
                entry.getKey().setSoftMemoryLimit(entry.getValue());
            }
        });
        this.configurationInfo = requireNonNull(configurationInfo, "configurationInfo is null");
    }

    protected Map.Entry<ResourceGroupIdTemplate, ResourceGroupSpec> getMatchingSpec(ResourceGroup group, SelectionContext context)
    {
        List<ResourceGroupSpec> candidates = getRootGroups();
        List<String> segments = group.getId().getSegments();
        ResourceGroupSpec match = null;
        List<ResourceGroupNameTemplate> templateId = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            List<ResourceGroupSpec> nextCandidates = null;
            ResourceGroupSpec nextCandidatesParent = null;
            for (ResourceGroupSpec candidate : candidates) {
                if (candidate.getName().expandTemplate(context).equals(segments.get(i))) {
                    templateId.add(candidate.getName());
                    if (i == segments.size() - 1) {
                        if (match != null) {
                            throw new IllegalStateException(format("Ambiguous configuration for %s. Matches %s and %s", group.getId(), match.getName(), candidate.getName()));
                        }
                        match = candidate;
                    }
                    else {
                        if (nextCandidatesParent != null) {
                            throw new IllegalStateException(format("Ambiguous configuration for %s. Matches %s and %s", group.getId(), nextCandidatesParent.getName(), candidate.getName()));
                        }
                        nextCandidates = candidate.getSubGroups();
                        nextCandidatesParent = candidate;
                    }
                }
            }
            if (nextCandidates == null) {
                break;
            }
            candidates = nextCandidates;
        }
        checkState(match != null, "No matching configuration found for: %s", group.getId());

        return new AbstractMap.SimpleImmutableEntry<>(ResourceGroupIdTemplate.fromSegments(templateId), match);
    }

    protected void configureGroup(ResourceGroup group, ResourceGroupSpec match, ResourceGroupIdTemplate templateId)
    {
        reconfigureGroup(group, match);
        configurationInfo.addGroup(group.getId(), templateId);
    }

    protected void reconfigureGroup(ResourceGroup group, ResourceGroupSpec match)
    {
        if (match.getSoftMemoryLimit().isPresent()) {
            group.setSoftMemoryLimit(match.getSoftMemoryLimit().get());
        }
        else {
            synchronized (generalPoolMemoryFraction) {
                double fraction = match.getSoftMemoryLimitFraction().get();
                generalPoolMemoryFraction.put(group, fraction);
                group.setSoftMemoryLimit(new DataSize(generalPoolBytes * fraction, BYTE));
            }
        }
        group.setMaxQueuedQueries(match.getMaxQueued());
        group.setMaxRunningQueries(match.getMaxRunning());
        if (match.getQueuedTimeLimit().isPresent()) {
            group.setQueuedTimeLimit(match.getQueuedTimeLimit().get());
        }
        if (match.getRunningTimeLimit().isPresent()) {
            group.setRunningTimeLimit(match.getRunningTimeLimit().get());
        }
        if (match.getSchedulingPolicy().isPresent()) {
            group.setSchedulingPolicy(match.getSchedulingPolicy().get());
        }
        if (match.getSchedulingWeight().isPresent()) {
            group.setSchedulingWeight(match.getSchedulingWeight().get());
        }
        // if the new and current values do not differ an exception is thrown
        if (match.getJmxExport().isPresent() && match.getJmxExport().get() != group.getJmxExport()) {
            group.setJmxExport(match.getJmxExport().get());
        }
        if (match.getSoftCpuLimit().isPresent() || match.getHardCpuLimit().isPresent()) {
            // This will never throw an exception if the validateManagerSpec method succeeds
            checkState(getCpuQuotaPeriod().isPresent(), "Must specify hard CPU limit in addition to soft limit");
            Duration limit;
            if (match.getHardCpuLimit().isPresent()) {
                limit = match.getHardCpuLimit().get();
            }
            else {
                limit = match.getSoftCpuLimit().get();
            }
            long rate = (long) Math.min(1000.0 * limit.toMillis() / (double) getCpuQuotaPeriod().get().toMillis(), Long.MAX_VALUE);
            rate = Math.max(1, rate);
            group.setCpuQuotaGenerationMillisPerSecond(rate);
        }
        if (match.getSoftCpuLimit().isPresent()) {
            group.setSoftCpuLimit(match.getSoftCpuLimit().get());
        }
        if (match.getHardCpuLimit().isPresent()) {
            group.setHardCpuLimit(match.getHardCpuLimit().get());
        }
    }
}
