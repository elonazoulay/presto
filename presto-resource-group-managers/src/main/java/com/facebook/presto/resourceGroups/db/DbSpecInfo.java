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
import com.facebook.presto.resourceGroups.ManagerSpec;
import com.facebook.presto.resourceGroups.ResourceGroupIdTemplate;
import com.facebook.presto.resourceGroups.ResourceGroupSpec;
import com.facebook.presto.resourceGroups.SelectorSpec;
import com.facebook.presto.resourceGroups.StaticSelector;
import com.facebook.presto.resourceGroups.systemtables.ResourceGroupInfoHolder;
import com.facebook.presto.spi.resourceGroups.ResourceGroupSelector;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import io.airlift.units.Duration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.facebook.presto.resourceGroups.AbstractResourceConfigurationManager.buildSelectors;
import static com.facebook.presto.resourceGroups.AbstractResourceConfigurationManager.validateCpuQuotaPeriodForGroup;
import static com.facebook.presto.resourceGroups.AbstractResourceConfigurationManager.validateRootGroups;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class DbSpecInfo
{
    private final ResourceGroupInfoHolder resourceGroupInfoHolder;
    private final ManagerSpec managerSpec;
    private final Map<ResourceGroupIdTemplate, ResourceGroupSpec> resourceGroupSpecs;
    private final Map<ResourceGroupIdTemplate, Long> resourceGroupsDbIdsLookup;
    private final long maxResourceGroupDbId;
    private final Map<SelectorSpec, ResourceGroupSelector> specToSelector;
    private final List<ResourceGroupSelector> selectors;

    public DbSpecInfo(ResourceGroupInfoHolder resourceGroupInfoHolder)
    {
        this(
                resourceGroupInfoHolder,
                new ManagerSpec(ImmutableList.of(), ImmutableList.of(), Optional.empty()),
                ImmutableMap.of(),
                ImmutableMap.of());
    }

    private DbSpecInfo(
            ResourceGroupInfoHolder resourceGroupInfoHolder,
            ManagerSpec managerSpec,
            Map<ResourceGroupIdTemplate, ResourceGroupSpec> resourceGroupSpecs,
            Map<ResourceGroupIdTemplate, Long> resourceGroupsDbIdsLookup,
            long maxResourceGroupDbId,
            Map<SelectorSpec, ResourceGroupSelector> specToSelector,
            List<ResourceGroupSelector> selectors
    )
    {
        this.resourceGroupInfoHolder = requireNonNull(resourceGroupInfoHolder, "resourceGroupInfoHolder is null");
        this.managerSpec = requireNonNull(managerSpec, "managerSpec is null");
        this.resourceGroupSpecs = requireNonNull(resourceGroupSpecs, "resourceGroupSpecs is null");
        this.resourceGroupsDbIdsLookup = requireNonNull(resourceGroupsDbIdsLookup, "resourceGroupsDbIdsLookup is null");
        this.maxResourceGroupDbId = requireNonNull(maxResourceGroupDbId, "maxResourceGroupDbId is null");
        this.specToSelector = requireNonNull(specToSelector, "specToSelector is null");
        this.selectors = ImmutableList.copyOf(selectors);
        this.resourceGroupInfoHolder.setResourceGroupSpecs(this.resourceGroupSpecs);
        this.resourceGroupInfoHolder.setSelectorSpecs(this.managerSpec.getSelectors());
        this.resourceGroupInfoHolder.setCpuQuotaPeriod(this.managerSpec.getCpuQuotaPeriod());
    }

    private DbSpecInfo(
            ResourceGroupInfoHolder resourceGroupInfoHolder,
            ManagerSpec managerSpec,
            Map<ResourceGroupIdTemplate, ResourceGroupSpec> resourceGroupSpecs,
            Map<Long, ResourceGroupIdTemplate> resourceGroupsDbIds)
    {
        this.resourceGroupInfoHolder = resourceGroupInfoHolder;
        this.managerSpec = managerSpec;
        this.resourceGroupSpecs = ImmutableMap.copyOf(resourceGroupSpecs);
        long maxId = 0;
        ImmutableMap.Builder<ResourceGroupIdTemplate, Long> builder = ImmutableMap.builder();
        for (Map.Entry<Long, ResourceGroupIdTemplate> entry : resourceGroupsDbIds.entrySet()) {
            builder.put(entry.getValue(), entry.getKey());
            if (entry.getKey() > maxId) {
                maxId = entry.getKey();
            }
        }
        this.resourceGroupsDbIdsLookup = builder.build();
        this.maxResourceGroupDbId = maxId;
        this.resourceGroupInfoHolder.setResourceGroupSpecs(this.resourceGroupSpecs);
        this.specToSelector = buildSelectors(managerSpec);
        this.selectors = ImmutableList.copyOf(this.specToSelector.values());
        this.resourceGroupInfoHolder.setSelectorSpecs(ImmutableList.copyOf(this.specToSelector.keySet()));
        this.resourceGroupInfoHolder.setCpuQuotaPeriod(this.managerSpec.getCpuQuotaPeriod());
    }

    public ManagerSpec getManagerSpec()
    {
        return managerSpec;
    }

    public List<ResourceGroupSelector> getSelectors()
    {
        return selectors;
    }

    public long getMaxDbId()
    {
        return maxResourceGroupDbId;
    }

    public Long getDbId(ResourceGroupIdTemplate resourceGroupIdTemplate)
    {
        requireNonNull(resourceGroupIdTemplate, "resourceGroupIdTemplate is null");
        return resourceGroupsDbIdsLookup.get(resourceGroupIdTemplate);
    }

    public ResourceGroupSpec getSpec(ResourceGroupIdTemplate resourceGroupIdTemplate)
    {
        requireNonNull(resourceGroupIdTemplate, "resourceGroupIdTemplate is null");
        return resourceGroupSpecs.get(resourceGroupIdTemplate);
    }

    public Set<ResourceGroupIdTemplate> getChangedSpecs(DbSpecInfo oldSpecInfo)
    {
        requireNonNull(oldSpecInfo, "old spec info is null");
        ImmutableSet.Builder<ResourceGroupIdTemplate> changedSpecs = ImmutableSet.builder();
        for (Map.Entry<ResourceGroupIdTemplate, ResourceGroupSpec> entry : resourceGroupSpecs.entrySet()) {
            if (!entry.getValue().sameConfig(oldSpecInfo.getSpec(entry.getKey()))) {
                changedSpecs.add(entry.getKey());
            }
        }
        return changedSpecs.build();
    }

    public Set<ResourceGroupIdTemplate> getDeletedSpecs(DbSpecInfo oldSpecInfo)
    {
        requireNonNull(oldSpecInfo, "old spec info is null");
        return Sets.difference(oldSpecInfo.resourceGroupSpecs.keySet(), resourceGroupSpecs.keySet());
    }

    private static Optional<Duration> getCpuQuotaPeriodFromDb(ResourceGroupsDao dao)
    {
        List<ResourceGroupGlobalProperties> globalProperties = dao.getResourceGroupGlobalProperties();
        checkState(globalProperties.size() <= 1, "There is more than one cpu_quota_period");
        return (!globalProperties.isEmpty()) ? globalProperties.get(0).getCpuQuotaPeriod() : Optional.empty();
    }

    private static void populateFromDbHelper(
            ResourceGroupsDao dao,
            Map<Long, ResourceGroupSpecBuilder> recordMap,
            Set<Long> rootGroupIds,
            Map<Long, ResourceGroupIdTemplate> resourceGroupIdTemplateMap,
            Map<Long, Set<Long>> subGroupIdsToBuild
    )
    {
        List<ResourceGroupSpecBuilder> records = dao.getResourceGroups();
        for (ResourceGroupSpecBuilder record : records) {
            recordMap.put(record.getId(), record);
            if (!record.getParentId().isPresent()) {
                rootGroupIds.add(record.getId());
                resourceGroupIdTemplateMap.put(record.getId(), new ResourceGroupIdTemplate(record.getNameTemplate().toString()));
            }
            else {
                subGroupIdsToBuild.computeIfAbsent(record.getParentId().get(), k -> new HashSet<>()).add(record.getId());
            }
        }
    }

    public static DbSpecInfo buildSpecsFromDb(ResourceGroupInfoHolder resourceGroupInfoHolder, ResourceGroupsDao dao)
    {
        // New resource group spec map
        Map<ResourceGroupIdTemplate, ResourceGroupSpec> resourceGroupSpecs = new HashMap<>();
        // Set of root group db ids
        Set<Long> rootGroupIds = new HashSet<>();
        // Map of id from db to resource group spec
        Map<Long, ResourceGroupSpec> resourceGroupSpecMap = new HashMap<>();
        // Map of id from db to resource group template id
        Map<Long, ResourceGroupIdTemplate> resourceGroupIdTemplateMap = new HashMap<>();
        // Map of id from db to resource group spec builder
        Map<Long, ResourceGroupSpecBuilder> recordMap = new HashMap<>();
        // Map of subgroup id's not yet built
        Map<Long, Set<Long>> subGroupIdsToBuild = new HashMap<>();
        populateFromDbHelper(dao, recordMap, rootGroupIds, resourceGroupIdTemplateMap, subGroupIdsToBuild);
        // Build up resource group specs from leaf to root
        for (LinkedList<Long> queue = new LinkedList<>(rootGroupIds); !queue.isEmpty(); ) {
            Long id = queue.pollFirst();
            resourceGroupIdTemplateMap.computeIfAbsent(id, k -> {
                ResourceGroupSpecBuilder builder = recordMap.get(id);
                return ResourceGroupIdTemplate.forSubGroupNamed(
                        resourceGroupIdTemplateMap.get(builder.getParentId().get()),
                        builder.getNameTemplate().toString());
            });
            Set<Long> childrenToBuild = subGroupIdsToBuild.getOrDefault(id, ImmutableSet.of());
            // Add to resource group specs if no more child resource groups are left to build
            if (childrenToBuild.isEmpty()) {
                ResourceGroupSpecBuilder builder = recordMap.get(id);
                ResourceGroupSpec resourceGroupSpec = builder.build();
                resourceGroupSpecMap.put(id, resourceGroupSpec);
                // Add newly built spec to spec map
                resourceGroupSpecs.put(resourceGroupIdTemplateMap.get(id), resourceGroupSpec);
                // Add this resource group spec to parent subgroups and remove id from subgroup ids to build
                builder.getParentId().ifPresent(parentId -> {
                    recordMap.get(parentId).addSubGroup(resourceGroupSpec);
                    subGroupIdsToBuild.get(parentId).remove(id);
                });
            }
            else {
                // Add this group back to queue since it still has subgroups to build
                queue.addFirst(id);
                // Add this group's subgroups to the queue so that when this id is dequeued again childrenToBuild will be empty
                queue.addAll(0, childrenToBuild);
            }
        }

        // Specs are built from db records, validate and return manager spec
        List<ResourceGroupSpec> rootGroups = rootGroupIds.stream().map(resourceGroupSpecMap::get).collect(Collectors.toList());

        List<SelectorSpec> selectors = dao.getSelectors().stream().map(selectorRecord ->
                new SelectorSpec(selectorRecord.getUserRegex(), selectorRecord.getSourceRegex(),
                        resourceGroupIdTemplateMap.get(selectorRecord.getResourceGroupId()))
        ).collect(Collectors.toList());
        ManagerSpec managerSpec = new ManagerSpec(rootGroups, selectors, getCpuQuotaPeriodFromDb(dao));
        validateRootGroups(managerSpec);
        return new DbSpecInfo(resourceGroupInfoHolder, managerSpec, resourceGroupSpecs, resourceGroupIdTemplateMap);
    }

    public static DbSpecInfo withAddedSelector(DbSpecInfo specInfo, SelectorSpec spec)
    {
        ImmutableMap.Builder<SelectorSpec, ResourceGroupSelector> builder = ImmutableMap.builder();
        builder.putAll(specInfo.specToSelector);
        builder.put(spec, new StaticSelector(spec.getUserRegex(), spec.getSourceRegex(), spec.getGroup()));
        ImmutableMap<SelectorSpec, ResourceGroupSelector> specToSelector = builder.build();
        ManagerSpec updatedManagerSpec = new ManagerSpec(
                specInfo.managerSpec.getRootGroups(),
                ImmutableList.copyOf(specToSelector.keySet()),
                specInfo.managerSpec.getCpuQuotaPeriod());

        return new DbSpecInfo(
                specInfo.resourceGroupInfoHolder,
                updatedManagerSpec,
                specInfo.resourceGroupSpecs,
                specInfo.resourceGroupsDbIdsLookup,
                specInfo.maxResourceGroupDbId,
                specToSelector,
                ImmutableList.copyOf(specToSelector.values()));
    }

    public static DbSpecInfo withUpdatedSelector(DbSpecInfo specInfo, SelectorSpec current, SelectorSpec updated)
    {
        checkState(current.getGroup().equals(updated.getGroup()),
                format("Resource groups %s and %s do not match for selectors", current.getGroup(), updated.getGroup()));
        checkState(!current.equals(updated), "selector specs are identical");
        Map<SelectorSpec, ResourceGroupSelector> builder = new HashMap<>(specInfo.specToSelector);
        ResourceGroupSelector updatedSelector = new StaticSelector(updated.getUserRegex(), updated.getSourceRegex(), updated.getGroup());
        requireNonNull(builder.remove(current), "selector does not exist");
        checkState(builder.put(updated, updatedSelector) == null, "selector already exists");
        ManagerSpec updatedManagerSpec = new ManagerSpec(
                specInfo.managerSpec.getRootGroups(),
                ImmutableList.copyOf(builder.keySet()),
                specInfo.managerSpec.getCpuQuotaPeriod());
        return new DbSpecInfo(
                specInfo.resourceGroupInfoHolder,
                updatedManagerSpec,
                specInfo.resourceGroupSpecs,
                specInfo.resourceGroupsDbIdsLookup,
                specInfo.maxResourceGroupDbId,
                ImmutableMap.copyOf(builder),
                ImmutableList.copyOf(builder.values()));
    }

    public static DbSpecInfo withRemovedSelector(DbSpecInfo specInfo, SelectorSpec removed)
    {
        Map<SelectorSpec, ResourceGroupSelector> builder = new HashMap<>(specInfo.specToSelector);
        checkState(builder.remove(removed) != null, "spec does not exist");
        ManagerSpec updatedManagerSpec = new ManagerSpec(
                specInfo.managerSpec.getRootGroups(),
                ImmutableList.copyOf(builder.keySet()),
                specInfo.managerSpec.getCpuQuotaPeriod());
        return  new DbSpecInfo(
                specInfo.resourceGroupInfoHolder,
                updatedManagerSpec,
                specInfo.resourceGroupSpecs,
                specInfo.resourceGroupsDbIdsLookup,
                specInfo.maxResourceGroupDbId,
                ImmutableMap.copyOf(builder),
                ImmutableList.copyOf(builder.values()));
    }

    public static DbSpecInfo withCpuQuotaPeriod(DbSpecInfo specInfo, Optional<Duration> cpuQuotaPeriod)
    {
        requireNonNull(cpuQuotaPeriod, "cpuQuotaPeriod is null");
        if (specInfo.managerSpec.getCpuQuotaPeriod().isPresent() && !cpuQuotaPeriod.isPresent()) {
            for (ResourceGroupSpec spec : specInfo.resourceGroupSpecs.values()) {
                validateCpuQuotaPeriodForGroup(cpuQuotaPeriod.isPresent(), spec);
            }
        }
        ManagerSpec updatedManagerSpec = new ManagerSpec(
                specInfo.managerSpec.getRootGroups(),
                specInfo.managerSpec.getSelectors(),
                cpuQuotaPeriod);

        return new DbSpecInfo(
                specInfo.resourceGroupInfoHolder,
                updatedManagerSpec,
                specInfo.resourceGroupSpecs,
                specInfo.resourceGroupsDbIdsLookup,
                specInfo.maxResourceGroupDbId,
                specInfo.specToSelector,
                specInfo.selectors);
    }
}
