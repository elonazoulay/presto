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

import com.facebook.presto.resourceGroups.AbstractResourceConfigurationManager;
import com.facebook.presto.resourceGroups.ResourceGroupIdTemplate;
import com.facebook.presto.resourceGroups.ResourceGroupNameTemplate;
import com.facebook.presto.resourceGroups.ResourceGroupSpec;
import com.facebook.presto.resourceGroups.SelectorSpec;
import com.facebook.presto.resourceGroups.systemtables.QueryQueueCache;
import com.facebook.presto.resourceGroups.systemtables.ResourceGroupInfoHolder;
import com.facebook.presto.spi.PrestoException;
import com.facebook.presto.spi.memory.ClusterMemoryPoolManager;
import com.facebook.presto.spi.resourceGroups.QueryQueueInfo;
import com.facebook.presto.spi.resourceGroups.ResourceGroup;
import com.facebook.presto.spi.resourceGroups.ResourceGroupId;
import com.facebook.presto.spi.resourceGroups.ResourceGroupSelector;
import com.facebook.presto.spi.resourceGroups.SchedulingPolicy;
import com.facebook.presto.spi.resourceGroups.SelectionContext;
import com.google.common.collect.ImmutableList;
import io.airlift.log.Logger;
import io.airlift.units.Duration;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static com.facebook.presto.resourceGroups.db.ResourceGroupErrorCode.GENERIC_RESOURCE_GROUP_ERROR;
import static com.facebook.presto.resourceGroups.db.ResourceGroupErrorCode.INVALID_CPU_QUOTA_PERIOD;
import static com.facebook.presto.resourceGroups.db.ResourceGroupErrorCode.RESOURCE_GROUP_NOT_FOUND;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class DbResourceGroupConfigurationManager
        extends AbstractResourceConfigurationManager
{
    private static final Logger log = Logger.get(DbResourceGroupConfigurationManager.class);
    private final ResourceGroupsDao dao;
    private final ConcurrentMap<ResourceGroupId, ResourceGroup> groups = new ConcurrentHashMap<>();
    private final ConcurrentMap<ResourceGroupIdTemplate, List<ResourceGroupId>> configuredGroups = new ConcurrentHashMap<>();
    private final AtomicBoolean started = new AtomicBoolean();
    private final ResourceGroupInfoHolder resourceGroupInfoHolder;
    private final ConfigurationNotifier configurationNotifier;
    private final AtomicReference<DbSpecInfo> specInfo;

    @Inject
    public DbResourceGroupConfigurationManager(
            ClusterMemoryPoolManager memoryPoolManager,
            ResourceGroupsDao dao,
            QueryQueueCache queryQueueCache,
            ResourceGroupInfoHolder resourceGroupInfoHolder,
            ConfigurationNotifier configurationNotifier)
    {
        super(memoryPoolManager, queryQueueCache);
        this.resourceGroupInfoHolder = requireNonNull(resourceGroupInfoHolder, "resourceGroupInfoHolder is null");
        this.specInfo = new AtomicReference<>(new DbSpecInfo(resourceGroupInfoHolder));
        this.configurationNotifier = requireNonNull(configurationNotifier, "configurationNotifier is null");
        requireNonNull(memoryPoolManager, "memoryPoolManager is null");
        requireNonNull(dao, "daoProvider is null");
        this.dao = dao;
        this.dao.createResourceGroupsGlobalPropertiesTable();
        this.dao.createResourceGroupsTable();
        this.dao.createSelectorsTable();
        load();
    }

    @Override
    protected Optional<Duration> getCpuQuotaPeriodMillis()
    {
        return getSpecInfo().getManagerSpec().getCpuQuotaPeriod();
    }

    @Override
    protected List<ResourceGroupSpec> getRootGroups()
    {
        return getSpecInfo().getManagerSpec().getRootGroups();
    }

    @PreDestroy
    public void destroy()
    {
        destroyCache();
    }

    @PostConstruct
    public void start()
    {
        if (started.compareAndSet(false, true)) {
            configurationNotifier.setConfigurationManager(this);
            startCache();
        }
    }

    @Override
    public void configure(ResourceGroup group, SelectionContext context)
    {
        Map.Entry<ResourceGroupIdTemplate, ResourceGroupSpec> entry = getMatchingSpec(group, context);
        if (groups.putIfAbsent(group.getId(), group) == null) {
            // If a new spec replaces the spec returned from getMatchingSpec the group will be reconfigured on the next run of load().
            configuredGroups.computeIfAbsent(entry.getKey(), v -> new LinkedList<>()).add(group.getId());
        }
        synchronized (getRootGroup(group.getId())) {
            configureGroup(group, entry.getValue());
            resourceGroupInfoHolder.addGroupToSpec(entry.getKey(), group.getId());
        }
    }

    @Override
    public List<ResourceGroupSelector> getSelectors()
    {
        return getSpecInfo().getSelectors();
    }

    synchronized void load()
    {
        try {
            DbSpecInfo specsFromDb = DbSpecInfo.buildSpecsFromDb(resourceGroupInfoHolder, dao);
            DbSpecInfo oldSpecsFromDb = this.specInfo.getAndSet(specsFromDb);
            configureChangedGroups(specsFromDb.getChangedSpecs(oldSpecsFromDb));
            disableDeletedGroups(specsFromDb.getDeletedSpecs(oldSpecsFromDb));
        }
        catch (Exception ex) {
            log.error("Error loading resource groups: %s", ex);
            throw new PrestoException(GENERIC_RESOURCE_GROUP_ERROR, ex);
        }
    }

    private DbSpecInfo getSpecInfo()
    {
        return specInfo.get();
    }

    synchronized void addSelector(String resourceGroupIdTemplate, String userRegex, String sourceRegex)
    {
        try {
            ResourceGroupIdTemplate idTemplate = new ResourceGroupIdTemplate(resourceGroupIdTemplate);
            long resourceGroupDbId = requireNonNull(getSpecInfo().getDbId(idTemplate),
                    format("invalid resourceGroupIdTemplate %s", resourceGroupIdTemplate));
            SelectorSpec spec = new SelectorSpec(
                    Optional.ofNullable(userRegex).map(Pattern::compile),
                    Optional.ofNullable(sourceRegex).map(Pattern::compile),
                    idTemplate);
            validateSelector(getRootGroups(), spec.getGroup().getSegments());
            dao.insertSelector(resourceGroupDbId,
                    userRegex,
                    sourceRegex);
            specInfo.set(DbSpecInfo.withAddedSelector(getSpecInfo(), spec));
        }
        catch (NullPointerException ex) {
            throw new PrestoException(RESOURCE_GROUP_NOT_FOUND, ex);
        }
        catch (PrestoException ex) {
            throw ex;
        }
        catch (Exception ex) {
            throw new PrestoException(GENERIC_RESOURCE_GROUP_ERROR, ex);
        }
    }

    synchronized void alterSelector(String resourceGroupIdTemplate, String oldUserRegex, String oldSourceRegex, String userRegex, String sourceRegex)
    {
        try {
            ResourceGroupIdTemplate idTemplate = new ResourceGroupIdTemplate(resourceGroupIdTemplate);
            long resourceGroupDbId = requireNonNull(getSpecInfo().getDbId(idTemplate),
                    format("invalid resourceGroupIdTemplate %s", resourceGroupIdTemplate));
            SelectorSpec current = new SelectorSpec(
                    Optional.ofNullable(oldUserRegex).map(Pattern::compile),
                    Optional.ofNullable(oldSourceRegex).map(Pattern::compile),
                    idTemplate);
            SelectorSpec updated = new SelectorSpec(
                    Optional.ofNullable(userRegex).map(Pattern::compile),
                    Optional.ofNullable(sourceRegex).map(Pattern::compile),
                    idTemplate);
            if (current.equals(updated)) {
                return;
            }
            dao.updateSelector(
                    resourceGroupDbId,
                    oldUserRegex,
                    oldSourceRegex,
                    userRegex,
                    sourceRegex);
            specInfo.set(DbSpecInfo.withUpdatedSelector(getSpecInfo(), current, updated));
        }
        catch (NullPointerException ex) {
            throw new PrestoException(RESOURCE_GROUP_NOT_FOUND, ex);
        }
        catch (PrestoException ex) {
            throw ex;
        }
        catch (Exception ex) {
            throw new PrestoException(GENERIC_RESOURCE_GROUP_ERROR, ex);
        }
    }

    synchronized void removeSelector(String resourceGroupIdTemplate, String userRegex, String sourceRegex)
    {
        try {
            ResourceGroupIdTemplate idTemplate = new ResourceGroupIdTemplate(resourceGroupIdTemplate);
            long resourceGroupDbId = requireNonNull(getSpecInfo().getDbId(idTemplate),
                    format("invalid resourceGroupIdTemplate %s", resourceGroupIdTemplate));
            SelectorSpec spec = new SelectorSpec(
                    Optional.ofNullable(userRegex).map(Pattern::compile),
                    Optional.ofNullable(sourceRegex).map(Pattern::compile),
                    idTemplate);
            dao.deleteSelector(resourceGroupDbId, userRegex, sourceRegex);
            specInfo.set(DbSpecInfo.withRemovedSelector(getSpecInfo(), spec));
        }
        catch (NullPointerException ex) {
            throw new PrestoException(RESOURCE_GROUP_NOT_FOUND, ex);
        }
        catch (PrestoException ex) {
            throw ex;
        }
        catch (Exception ex) {
            throw new PrestoException(GENERIC_RESOURCE_GROUP_ERROR, ex);
        }
    }

    synchronized void setCpuQuotaPeriod(String cpuQuotaPeriod)
    {
        try {
            Optional<Duration> quotaPeriod = Optional.ofNullable(cpuQuotaPeriod).map(Duration::valueOf);
            DbSpecInfo current = specInfo.get();
            DbSpecInfo newInfo = DbSpecInfo.withCpuQuotaPeriod(current, quotaPeriod);
            if (cpuQuotaPeriod == null) {
                dao.deleteResourceGroupsGlobalProperties();
            }
            else {
                dao.upsertResourceGroupsGlobalProperties(ResourceGroupGlobalProperties.CPU_QUOTA_PERIOD, cpuQuotaPeriod);
            }
            specInfo.set(newInfo);
        }
        catch (Exception ex) {
            throw new PrestoException(INVALID_CPU_QUOTA_PERIOD, ex);
        }
    }

    synchronized void addResourceGroup(
            String resourceGroupIdTemplate,
            String softMemoryLimit,
            String hardMemoryLimit,
            String maxMemoryPerQuery,
            int maxQueued,
            int maxRunning,
            Optional<String> schedulingPolicy,
            Optional<Integer> schedulingWeight,
            Optional<Boolean> jmxExport,
            Optional<String> softCpuLimit,
            Optional<String> hardCpuLimit,
            Optional<String> queuedTimeout,
            Optional<String> runningTimeout)
    {
        try {
            ResourceGroupIdTemplate newId = new ResourceGroupIdTemplate(resourceGroupIdTemplate);
            checkState(getSpecInfo().getSpec(newId) == null, "resource group spec already exists");
            ResourceGroupNameTemplate nameTemplate = ResourceGroupIdTemplate.getNameTemplate(newId);
            Optional<ResourceGroupIdTemplate> parentId = ResourceGroupIdTemplate.parentOf(newId);
            long newDbId = getSpecInfo().getMaxDbId() + 1;
            Optional<Long> parentDbId = parentId.map(id ->
                    requireNonNull(getSpecInfo().getDbId(id), format("parent resource group spec %s does not exist", id)));
            ResourceGroupSpecBuilder specBuilder = new ResourceGroupSpecBuilder(
                    newDbId,
                    nameTemplate,
                    softMemoryLimit,
                    hardMemoryLimit,
                    maxMemoryPerQuery,
                    maxQueued,
                    maxRunning,
                    schedulingPolicy,
                    schedulingWeight,
                    jmxExport,
                    softCpuLimit,
                    hardCpuLimit,
                    queuedTimeout,
                    runningTimeout,
                    parentDbId);
            ResourceGroupSpec spec = specBuilder.build();
            validateGroupSpec(getCpuQuotaPeriodMillis().isPresent(), spec);
            Optional<ResourceGroupSpec> parentSpec = parentId.map(id -> getSpecInfo().getSpec(id));
            if (parentId.isPresent()) {
                checkState(parentSpec.isPresent(), "Parent resource group spec does not exist");
                if (parentSpec.get().getSubGroups().isEmpty()) {
                    // leaf group
                    // verify there are no queries running or queued since this will no longer be a leaf group
                    verifyQueueIsEmpty(parentId.get());
                }
                parentSpec = parentSpec.map(p -> ResourceGroupSpec.copyAndAddSubGroups(p, ImmutableList.of(spec)));
                parentSpec.ifPresent(p -> validateResourceGroupSpecSchedulingPolicy(p, spec));
            }
            specBuilder.insert(dao);
            load();
        }
        catch (NullPointerException ex) {
            ex.printStackTrace();
            throw new PrestoException(RESOURCE_GROUP_NOT_FOUND, ex);
        }
        catch (PrestoException ex) {
            ex.printStackTrace();
            throw ex;
        }
        catch (Exception ex) {
            throw new PrestoException(GENERIC_RESOURCE_GROUP_ERROR, ex);
        }
    }

    private void verifyQueueIsEmpty(ResourceGroupIdTemplate resourceGroupIdTemplate)
    {
        // checkState(rootGroupsLock.isWriteLockedByCurrentThread(), "current thread does not hold write lock");
        List<ResourceGroupId> resourceGroupIds = configuredGroups.getOrDefault(resourceGroupIdTemplate, ImmutableList.of());
        Optional<QueryQueueInfo> info = Optional.empty();
        if (!resourceGroupIds.isEmpty()) {
            ResourceGroup root = getRootGroup(resourceGroupIds.get(0));
            info = root.getQueryQueueInfo();
        }
        if (info.isPresent() && !info.get().getQueryQueue().isEmpty()) {
            for (ResourceGroupId id : resourceGroupIds) {
                for (QueryQueueInfo.QueryEntry entry : info.get().getQueryQueue()) {
                    checkState(!entry.getLeafGroupId().equals(id), "Query queues not empty");
                }
            }
        }
    }

    private void validateResourceGroupSpecSchedulingPolicy(ResourceGroupSpec parent, ResourceGroupSpec spec)
    {
        if (isRecursive(spec) || isRecursive(parent)) {
            checkState(parent.getSchedulingPolicy().equals(spec.getSchedulingPolicy()),
                    "This scheduling policy must match parent scheduling policy");
        }
    }

    private static boolean isRecursive(ResourceGroupSpec spec)
    {
        return spec.getSchedulingPolicy().map(SchedulingPolicy::isRecursive).orElse(false);
    }

    synchronized void alterResourceGroup(
            String resourceGroupIdTemplate,
            String softMemoryLimit,
            String hardMemoryLimit,
            String maxMemoryPerQuery,
            int maxQueued,
            int maxRunning,
            Optional<String> schedulingPolicy,
            Optional<Integer> schedulingWeight,
            Optional<Boolean> jmxExport,
            Optional<String> softCpuLimit,
            Optional<String> hardCpuLimit,
            Optional<String> queuedTimeout,
            Optional<String> runningTimeout)
    {
        try {
            ResourceGroupIdTemplate idTemplate = new ResourceGroupIdTemplate(resourceGroupIdTemplate);
            ResourceGroupNameTemplate nameTemplate = ResourceGroupIdTemplate.getNameTemplate(idTemplate);
            ResourceGroupSpec currentSpec = requireNonNull(getSpecInfo().getSpec(idTemplate), "resource group spec does not exist");
            long dbId = requireNonNull(getSpecInfo().getDbId(idTemplate), "resource group spec does not exist");
            Optional<ResourceGroupIdTemplate> parentId = ResourceGroupIdTemplate.parentOf(idTemplate);
            Optional<Long> parentDbId = parentId.map(id ->
                    requireNonNull(getSpecInfo().getDbId(id), format("parent resource group spec %s does not exist", id)));
            ResourceGroupSpecBuilder specBuilder = new ResourceGroupSpecBuilder(
                    dbId,
                    nameTemplate,
                    softMemoryLimit,
                    hardMemoryLimit,
                    maxMemoryPerQuery,
                    maxQueued,
                    maxRunning,
                    schedulingPolicy,
                    schedulingWeight,
                    jmxExport,
                    softCpuLimit,
                    hardCpuLimit,
                    queuedTimeout,
                    runningTimeout,
                    parentDbId
            );
            for (ResourceGroupSpec subGroup : currentSpec.getSubGroups()) {
                specBuilder.addSubGroup(subGroup);
            }
            ResourceGroupSpec updatedSpec = specBuilder.build();
            if (updatedSpec.sameConfig(currentSpec)) {
                return;
            }
            validateGroupSpec(getCpuQuotaPeriodMillis().isPresent(), updatedSpec);
            // If scheduling policy is changed reload from db after updates
            if (parentId.isPresent()) {
                // Guaranteed to exist since child spec exists
                ResourceGroupSpec parentSpec = getSpecInfo().getSpec(parentId.get());
                validateResourceGroupSpecSchedulingPolicy(parentSpec, updatedSpec);
            }
            else {
                // root group
                updateSchedulingPolicy(idTemplate, currentSpec, updatedSpec);
            }
            specBuilder.update(dao);
            load();
        }
        catch (NullPointerException ex) {
            throw new PrestoException(RESOURCE_GROUP_NOT_FOUND, ex);
        }
        catch (PrestoException ex) {
            throw ex;
        }
        catch (Exception ex) {
            throw new PrestoException(GENERIC_RESOURCE_GROUP_ERROR, ex);
        }
    }

    private synchronized void updateSchedulingPolicy(ResourceGroupIdTemplate idTemplate, ResourceGroupSpec current, ResourceGroupSpec updated)
    {
        checkState(!ResourceGroupIdTemplate.parentOf(idTemplate).isPresent(), "resource group must be a root group");
        checkState(current.getName().equals(updated.getName()), "resource group specs do not match");
        Optional<SchedulingPolicy> currentPolicy = current.getSchedulingPolicy();
        Optional<SchedulingPolicy> updatedPolicy = updated.getSchedulingPolicy();
        if (!currentPolicy.equals(updatedPolicy)) {
            if (updatedPolicy.map(SchedulingPolicy::isRecursive).orElse(false) ||
                    currentPolicy.map(SchedulingPolicy::isRecursive).orElse(false)) {
                // set policy in db for all subgroups
                updateDbSchedulingPolicy(idTemplate, updated.getSchedulingPolicy());
            }
        }
    }

    private synchronized void updateDbSchedulingPolicy(ResourceGroupIdTemplate idTemplate, Optional<SchedulingPolicy> policy)
    {
        List<Long> ids = new ArrayList<>();
        LinkedList<ResourceGroupIdTemplate> stack = new LinkedList<>();
        stack.push(idTemplate);
        ids.add(requireNonNull(getSpecInfo().getDbId(idTemplate), "resource group spec does not exist"));
        while (!stack.isEmpty()) {
            ResourceGroupIdTemplate currentId = stack.poll();
            long currentDbId = requireNonNull(getSpecInfo().getDbId(currentId), "resource group spec does not exist");
            ids.add(currentDbId);
            ResourceGroupSpec spec = getSpecInfo().getSpec(currentId);
            for (ResourceGroupSpec subGroup : spec.getSubGroups()) {
                ResourceGroupIdTemplate subGroupId = ResourceGroupIdTemplate.forSubGroupNamed(currentId, subGroup.getName().toString());
                stack.push(subGroupId);
            }
        }
        dao.updateSchedulingPolicy(policy.map(SchedulingPolicy::name).orElse(null), ids);
    }

    synchronized void removeResourceGroup(String resourceGroupIdTemplate)
    {
        try {
            // Removes from db, disables by setting maxRunning/Queued to 0 since InternalResourceGroupManager holds a reference to the group
            ResourceGroupIdTemplate idTemplate = new ResourceGroupIdTemplate(resourceGroupIdTemplate);
            long dbId = requireNonNull(getSpecInfo().getDbId(idTemplate), "resource group spec does not exist");
            dao.deleteResourceGroup(dbId);
            load();
        }
        catch (NullPointerException ex) {
            throw new PrestoException(RESOURCE_GROUP_NOT_FOUND, ex);
        }
        catch (PrestoException ex) {
            throw ex;
        }
        catch (Exception ex) {
            throw new PrestoException(GENERIC_RESOURCE_GROUP_ERROR, ex);
        }
    }

    private synchronized void configureChangedGroups(Set<ResourceGroupIdTemplate> changedSpecs)
    {
        for (ResourceGroupIdTemplate resourceGroupIdTemplate : changedSpecs) {
            for (ResourceGroupId resourceGroupId : configuredGroups.getOrDefault(resourceGroupIdTemplate, ImmutableList.of())) {
                synchronized (getRootGroup(resourceGroupId)) {
                    configureGroup(groups.get(resourceGroupId), getSpecInfo().getSpec(resourceGroupIdTemplate));
                }
            }
        }
    }

    private synchronized void disableDeletedGroups(Set<ResourceGroupIdTemplate> deletedSpecs)
    {
        for (ResourceGroupIdTemplate resourceGroupIdTemplate : deletedSpecs) {
            for (ResourceGroupId resourceGroupId : configuredGroups.getOrDefault(resourceGroupIdTemplate, ImmutableList.of())) {
                disableGroup(groups.get(resourceGroupId));
            }
        }
    }

    private synchronized void disableGroup(ResourceGroup group)
    {
        // Disable groups that are removed from the db
        group.setMaxRunningQueries(0);
        group.setMaxQueuedQueries(0);
    }

    private ResourceGroup getRootGroup(ResourceGroupId groupId)
    {
        Optional<ResourceGroupId> parent = groupId.getParent();
        while (parent.isPresent()) {
            groupId = parent.get();
            parent = groupId.getParent();
        }
        // GroupId is guaranteed to be in groups: it is added before the first call to this method in configure()
        return groups.get(groupId);
    }
}
