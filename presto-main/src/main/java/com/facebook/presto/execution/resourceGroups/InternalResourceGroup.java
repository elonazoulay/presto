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
package com.facebook.presto.execution.resourceGroups;

import com.facebook.presto.execution.QueryExecution;
import com.facebook.presto.execution.QueryInfo;
import com.facebook.presto.execution.QueryState;
import com.facebook.presto.spi.PrestoException;
import com.facebook.presto.spi.resourceGroups.ResourceGroup;
import com.facebook.presto.spi.resourceGroups.ResourceGroupId;
import com.facebook.presto.spi.resourceGroups.SchedulingPolicy;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.units.DataSize;
import io.airlift.units.Duration;
import org.weakref.jmx.Managed;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static com.facebook.presto.SystemSessionProperties.getQueryPriority;
import static com.facebook.presto.spi.ErrorType.USER_ERROR;
import static com.facebook.presto.spi.StandardErrorCode.QUERY_QUEUE_FULL;
import static com.facebook.presto.spi.resourceGroups.SchedulingPolicy.FAIR;
import static com.facebook.presto.spi.resourceGroups.SchedulingPolicy.QUERY_PRIORITY;
import static com.facebook.presto.spi.resourceGroups.SchedulingPolicy.WEIGHTED;
import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static io.airlift.units.DataSize.Unit.BYTE;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

@ThreadSafe
public class InternalResourceGroup
        implements ResourceGroup
{
    public static final int DEFAULT_WEIGHT = 1;

    private final InternalResourceGroup root;
    private final Optional<InternalResourceGroup> parent;
    private final ResourceGroupId id;
    private final BiConsumer<InternalResourceGroup, Boolean> jmxExportListener;
    private final Executor executor;

    @GuardedBy("root")
    private final Map<String, InternalResourceGroup> subGroups = new HashMap<>();
    // Sub groups with queued queries, that have capacity to run them
    // That is, they must return true when internalStartNext() is called on them
    @GuardedBy("root")
    private UpdateablePriorityQueue<InternalResourceGroup> eligibleSubGroups = new FifoQueue<>();
    @GuardedBy("root")
    private final Set<InternalResourceGroup> dirtySubGroups = new HashSet<>();
    @GuardedBy("root")
    private long softMemoryLimitBytes;
    @GuardedBy("root")
    private int maxRunningQueries;
    @GuardedBy("root")
    private int maxQueuedQueries;
    @GuardedBy("root")
    private long softCpuLimitMillis = Long.MAX_VALUE;
    @GuardedBy("root")
    private long hardCpuLimitMillis = Long.MAX_VALUE;
    @GuardedBy("root")
    private long cpuUsageMillis;
    @GuardedBy("root")
    private long cpuQuotaGenerationMillisPerSecond = Long.MAX_VALUE;
    @GuardedBy("root")
    private int descendantRunningQueries;
    @GuardedBy("root")
    private int descendantQueuedQueries;
    @GuardedBy("root")
    private long cachedMemoryUsageBytes;
    @GuardedBy("root")
    private int schedulingWeight = DEFAULT_WEIGHT;
    @GuardedBy("root")
    private UpdateablePriorityQueue<QueryExecution> queuedQueries = new FifoQueue<>();
    @GuardedBy("root")
    private final Set<QueryExecution> runningQueries = new HashSet<>();
    @GuardedBy("root")
    private SchedulingPolicy schedulingPolicy = FAIR;
    @GuardedBy("root")
    private boolean jmxExport;

    protected InternalResourceGroup(Optional<InternalResourceGroup> parent, String name, BiConsumer<InternalResourceGroup, Boolean> jmxExportListener, Executor executor)
    {
        this.parent = requireNonNull(parent, "parent is null");
        this.jmxExportListener = requireNonNull(jmxExportListener, "jmxExportListener is null");
        this.executor = requireNonNull(executor, "executor is null");
        requireNonNull(name, "name is null");
        if (parent.isPresent()) {
            id = new ResourceGroupId(parent.get().id, name);
            root = parent.get().root;
        }
        else {
            id = new ResourceGroupId(name);
            root = this;
        }
    }

    public ResourceGroupInfo getInfo()
    {
        synchronized (root) {
            List<ResourceGroupInfo> infos = subGroups.values().stream()
                    .map(InternalResourceGroup::getInfo)
                    .collect(Collectors.toList());
            return new ResourceGroupInfo(
                    id,
                    new DataSize(softMemoryLimitBytes, BYTE),
                    maxRunningQueries,
                    maxQueuedQueries,
                    runningQueries.size() + descendantRunningQueries,
                    queuedQueries.size() + descendantQueuedQueries,
                    new DataSize(cachedMemoryUsageBytes, BYTE),
                    infos);
        }
    }

    @Override
    public ResourceGroupId getId()
    {
        return id;
    }

    @Managed
    public int getRunningQueries()
    {
        synchronized (root) {
            return runningQueries.size() + descendantRunningQueries;
        }
    }

    @Managed
    public int getQueuedQueries()
    {
        synchronized (root) {
            return queuedQueries.size() + descendantQueuedQueries;
        }
    }

    @Override
    public DataSize getSoftMemoryLimit()
    {
        synchronized (root) {
            return new DataSize(softMemoryLimitBytes, BYTE);
        }
    }

    @Override
    public void setSoftMemoryLimit(DataSize limit)
    {
        synchronized (root) {
            boolean oldCanRun = canRunMore();
            this.softMemoryLimitBytes = limit.toBytes();
            if (canRunMore() != oldCanRun) {
                updateEligiblility();
            }
        }
    }

    @Override
    public Duration getSoftCpuLimit()
    {
        synchronized (root) {
            return new Duration(softCpuLimitMillis, MILLISECONDS);
        }
    }

    @Override
    public void setSoftCpuLimit(Duration limit)
    {
        synchronized (root) {
            if (limit.toMillis() > hardCpuLimitMillis) {
                setHardCpuLimit(limit);
            }
            boolean oldCanRun = canRunMore();
            this.softCpuLimitMillis = limit.toMillis();
            if (canRunMore() != oldCanRun) {
                updateEligiblility();
            }
        }
    }

    @Override
    public Duration getHardCpuLimit()
    {
        synchronized (root) {
            return new Duration(hardCpuLimitMillis, MILLISECONDS);
        }
    }

    @Override
    public void setHardCpuLimit(Duration limit)
    {
        synchronized (root) {
            if (limit.toMillis() < softCpuLimitMillis) {
                setSoftCpuLimit(limit);
            }
            boolean oldCanRun = canRunMore();
            this.hardCpuLimitMillis = limit.toMillis();
            if (canRunMore() != oldCanRun) {
                updateEligiblility();
            }
        }
    }

    @Override
    public long getCpuQuotaGenerationMillisPerSecond()
    {
        synchronized (root) {
            return cpuQuotaGenerationMillisPerSecond;
        }
    }

    @Override
    public void setCpuQuotaGenerationMillisPerSecond(long rate)
    {
        checkArgument(rate > 0, "Cpu quota generation must be positive");
        synchronized (root) {
            cpuQuotaGenerationMillisPerSecond = rate;
        }
    }

    @Managed
    @Override
    public int getMaxRunningQueries()
    {
        synchronized (root) {
            return maxRunningQueries;
        }
    }

    @Managed
    @Override
    public void setMaxRunningQueries(int maxRunningQueries)
    {
        checkArgument(maxRunningQueries >= 0, "maxRunningQueries is negative");
        synchronized (root) {
            boolean oldCanRun = canRunMore();
            this.maxRunningQueries = maxRunningQueries;
            if (canRunMore() != oldCanRun) {
                updateEligiblility();
            }
        }
    }

    @Managed
    @Override
    public int getMaxQueuedQueries()
    {
        synchronized (root) {
            return maxQueuedQueries;
        }
    }

    @Managed
    @Override
    public void setMaxQueuedQueries(int maxQueuedQueries)
    {
        checkArgument(maxQueuedQueries >= 0, "maxQueuedQueries is negative");
        synchronized (root) {
            this.maxQueuedQueries = maxQueuedQueries;
        }
    }

    @Override
    public int getSchedulingWeight()
    {
        synchronized (root) {
            return schedulingWeight;
        }
    }

    @Override
    public void setSchedulingWeight(int weight)
    {
        checkArgument(weight > 0, "weight must be positive");
        synchronized (root) {
            this.schedulingWeight = weight;
            if (parent.isPresent() && parent.get().schedulingPolicy == WEIGHTED && parent.get().eligibleSubGroups.contains(this)) {
                parent.get().eligibleSubGroups.addOrUpdate(this, weight);
            }
        }
    }

    @Override
    public SchedulingPolicy getSchedulingPolicy()
    {
        synchronized (root) {
            return schedulingPolicy;
        }
    }

    @Override
    public void setSchedulingPolicy(SchedulingPolicy policy)
    {
        synchronized (root) {
            if (policy == schedulingPolicy) {
                return;
            }

            if (parent.isPresent() && parent.get().schedulingPolicy == QUERY_PRIORITY) {
                checkArgument(policy == QUERY_PRIORITY, "Parent of %s uses query priority scheduling, so %s must also", id, id);
            }

            if (policy == QUERY_PRIORITY) {
                for (InternalResourceGroup group : subGroups.values()) {
                    group.setSchedulingPolicy(QUERY_PRIORITY);
                }
            }
            UpdateablePriorityQueue<InternalResourceGroup> queue = createEligibleSubgroupsQueue(policy);
            UpdateablePriorityQueue<QueryExecution> queryQueue = createQueryQueue(policy);
            while (!eligibleSubGroups.isEmpty()) {
                InternalResourceGroup group = eligibleSubGroups.poll();
                queue.addOrUpdate(group, getSubGroupSchedulingPriority(policy, group));
            }
            eligibleSubGroups = queue;
            while (!queuedQueries.isEmpty()) {
                QueryExecution query = queuedQueries.poll();
                queryQueue.addOrUpdate(query, getQueryPriority(query.getSession()));
            }
            queuedQueries = queryQueue;
            schedulingPolicy = policy;
        }
    }

    // Return the eligible subgroup queue for the given scheduling policy
    // This may be different than the query queue type
    protected <T> UpdateablePriorityQueue<T> createEligibleSubgroupsQueue(SchedulingPolicy policy)
    {
        UpdateablePriorityQueue<T> queue;
        switch (policy) {
            case FAIR:
                queue = new FifoQueue<>();
                break;
            case WEIGHTED:
                queue = new StochasticPriorityQueue<>();
                break;
            case QUERY_PRIORITY:
                queue = new IndexedPriorityQueue<>();
                break;
            default:
                throw new UnsupportedOperationException("Unsupported scheduling policy: " + policy);
        }
        return queue;
    }

    // Return the query queue for the given scheduling policy
    // This may be different than the eligible subgroup queue type
    protected <T> UpdateablePriorityQueue<T> createQueryQueue(SchedulingPolicy policy)
    {
        UpdateablePriorityQueue<T> queue;
        switch (policy) {
            case FAIR:
                queue = new FifoQueue<>();
                break;
            case WEIGHTED:
                queue = new StochasticPriorityQueue<>();
                break;
            case QUERY_PRIORITY:
                queue = new IndexedPriorityQueue<>();
                break;
            default:
                throw new UnsupportedOperationException("Unsupported scheduling policy: " + policy);
        }
        return queue;
    }

    // Utility class for viewing currently running queries
    private static class RunningQueryInfo
    {
        ResourceGroupId id;
        List<QueryInfo> runningQueries;

        public RunningQueryInfo(ResourceGroupId id, List<QueryInfo> runningQueries)
        {
            this.id = id;
            this.runningQueries = runningQueries;
        }
    }

    public Map<ResourceGroupId, List<QueryInfo>> getRunningQueryInfo()
    {
        synchronized (root) {
            // Only return information from root groups that contain queued or running queries or subgroups
            if (parent.isPresent() ||
                    (descendantQueuedQueries == 0 && descendantRunningQueries == 0 && queuedQueries.isEmpty() && runningQueries.isEmpty())) {
                return ImmutableMap.of();
            }
            Map<ResourceGroupId, List<QueryInfo>> runningQueries = new HashMap<>();
            LinkedList<InternalResourceGroup> stack = new LinkedList<>();
            stack.push(this);
            while (!stack.isEmpty()) {
                InternalResourceGroup group = stack.poll();
                if (!group.runningQueries.isEmpty()) {
                    runningQueries.computeIfAbsent(group.getId(), k -> new LinkedList<>()).addAll(group.runningQueries.stream().map(QueryExecution::getQueryInfo).collect(Collectors.toList()));
                } else {
                    if (!group.dirtySubGroups.isEmpty()) {
                        stack.addAll(group.dirtySubGroups);
                    }
                }
            }
            return runningQueries;
        }
    }
    // Utility class for viewing the currently queued queries
    private static class QueueInfo
    {
        ResourceGroupId id;
        Optional<QueueInfo> parent;
        SchedulingPolicy policy;
        int priority;
        UpdateablePriorityQueue<QueueInfo> eligibleSubGroups;
        UpdateablePriorityQueue<Entry> queuedQueries;

        public QueueInfo(ResourceGroupId id, Optional<QueueInfo> parent, SchedulingPolicy policy, int priority, UpdateablePriorityQueue<QueueInfo> eligibleSubGroups, UpdateablePriorityQueue<Entry> queuedQueries)
        {
            this.id = id;
            this.parent = parent;
            this.policy = policy;
            this.priority = priority;
            this.eligibleSubGroups = eligibleSubGroups;
            this.queuedQueries = queuedQueries;
            if (policy == QUERY_PRIORITY) {
                checkState(queuedQueries instanceof IndexedPriorityQueue, "Queued queries not ordered");
            }
        }

        Optional<QueryInfo> getNextQuery()
        {
            if (queuedQueries.isEmpty()) {
                return Optional.empty();
            }

            Entry queryEntry = queuedQueries.poll();
            // If the group uses QUERY_PRIORITY scheduling policy
            // set group priority to highest query priority
            if (policy == QUERY_PRIORITY && !queuedQueries.isEmpty()) {
                priority = queuedQueries.peek().priority;
            }
            return Optional.of(queryEntry.queryInfo);
        }

        boolean isEmpty()
        {
            return eligibleSubGroups.isEmpty() && queuedQueries.isEmpty();
        }

        private static class Entry
        {
            QueryInfo queryInfo;
            int priority;

            public Entry(QueryInfo queryInfo, int priority)
            {
                this.queryInfo = queryInfo;
                this.priority = priority;
            }

            public Entry(QueryExecution query)
            {
                this(query.getQueryInfo(), getQueryPriority(query.getSession()));
            }
        }
    }

    protected List<QueryInfo> getQueryQueueInfo()
    {
        synchronized (root) {
            // Only return information from root groups that contain queued or running queries or subgroups
            if (parent.isPresent() ||
                    (descendantQueuedQueries == 0 && descendantRunningQueries == 0 && queuedQueries.isEmpty() && runningQueries.isEmpty())) {
                return ImmutableList.of();
            }
            // Utility class for postorder traversal of root groups
            class StackFrame
            {
                InternalResourceGroup group;
                int stage;

                public StackFrame(InternalResourceGroup group, int stage)
                {
                    this.group = group;
                    this.stage = stage;
                }
            }
            Map<ResourceGroupId, QueueInfo> builder = new HashMap<>();
            LinkedList<StackFrame> stack = new LinkedList<>();
            stack.push(new StackFrame(this, 0));
            while (!stack.isEmpty()) {
                StackFrame frame = stack.poll();
                InternalResourceGroup group = frame.group;
                if (frame.stage == 0) {
                    // Create updateable priority queues with the same implementation as the resource groups
                    // So that queries will be dequeued in a similar order:
                    // This does not account for memory or cpu restrictions on subgroups since we do not know
                    // ahead of time
                    UpdateablePriorityQueue<QueueInfo> eligibleSubGroups = createEligibleSubgroupsQueue(group.schedulingPolicy);
                    UpdateablePriorityQueue<QueueInfo.Entry> queuedQueries = createQueryQueue(group.schedulingPolicy);
                    Optional<QueueInfo> parentEntry = (group.parent.isPresent()) ? Optional.of(builder.get(group.parent.get().id)) : Optional.empty();
                    QueueInfo entry = new QueueInfo(group.getId(), parentEntry, group.schedulingPolicy, getSubGroupSchedulingPriority(group.schedulingPolicy, group), eligibleSubGroups, queuedQueries);
                    // Add to builder map
                    builder.put(group.getId(), entry);
                    // Add to child parent map
                    // Push back onto stack with stage = 1
                    stack.push(new StackFrame(group, 1));
                    if (!group.queuedQueries.isEmpty()) {
                        // This is a leaf group
                        // Put queued query info in entry
                        // Add parent if present
                        UpdateablePriorityQueue<QueryExecution> newQueryQueue = createQueryQueue(group.schedulingPolicy);
                        while (!group.queuedQueries.isEmpty()) {
                            QueryExecution query = group.queuedQueries.poll();
                            QueueInfo.Entry queryEntry = new QueueInfo.Entry(query);
                            queuedQueries.addOrUpdate(queryEntry, queryEntry.priority);
                            newQueryQueue.addOrUpdate(query, getQueryPriority(query.getSession()));
                        }
                        group.queuedQueries = newQueryQueue;
                    }
                    else {
                        // This is a non-leaf group
                        // Put eligible subgroups onto stack
                        UpdateablePriorityQueue<InternalResourceGroup> newGroups = createEligibleSubgroupsQueue(group.schedulingPolicy);
                        while (!group.eligibleSubGroups.isEmpty()) {
                            InternalResourceGroup subGroup = group.eligibleSubGroups.poll();
                            newGroups.addOrUpdate(subGroup, getSubGroupSchedulingPriority(subGroup.schedulingPolicy, subGroup));
                            stack.push(new StackFrame(group, 0));
                        }
                        group.eligibleSubGroups = newGroups;
                    }
                }
                else if (frame.stage == 1) {
                    // entry is guaranteed to be in the builder map
                    QueueInfo entry = builder.get(group.getId());
                    // If parent is present add this entry to parent entry's eligible subgroups
                    Optional<QueueInfo> parentEntry = entry.parent;
                    parentEntry.map(parent -> parent.eligibleSubGroups.addOrUpdate(entry, entry.priority));
                }
            }
            return extractQueuedQueries(Optional.ofNullable(builder.get(id)));
        }
    }

    private List<QueryInfo> extractQueuedQueries(Optional<QueueInfo> queueInfoEntry)
    {
        checkState(Thread.holdsLock(root), "Must hold lock to extract queued queries");
        synchronized (root) {
            ImmutableList.Builder<QueryInfo> queuedQueries = ImmutableList.builder();
            if (!queueInfoEntry.isPresent()) {
                return queuedQueries.build();
            }
            class StackFrame
            {
                QueueInfo queueInfo;
                int stage;

                public StackFrame(QueueInfo queueInfo, int stage)
                {
                    this.queueInfo = queueInfo;
                    this.stage = stage;
                }
            }
            LinkedList<StackFrame> stack = new LinkedList<>();
            stack.push(new StackFrame(queueInfoEntry.get(), 0));

            while (!stack.isEmpty()) {
                StackFrame frame = stack.poll();
                QueueInfo entry = frame.queueInfo;
                // If entry has no leaf groups with queued queries ignore
                if (entry.isEmpty()) {
                    continue;
                }
                int stage = frame.stage;
                if (stage == 0) {
                    // This is the root group, only remove if empty
                    stack.push(frame);
                    if (!entry.queuedQueries.isEmpty()) {
                        // Is a root and leaf group
                        entry.getNextQuery().map(queryInfo -> queuedQueries.add(queryInfo));
                    }
                    else {
                        // Root group is guaranteed to have an eligible subgroup
                        QueueInfo subGroup = entry.eligibleSubGroups.poll();
                        stack.push(new StackFrame(subGroup, 1));
                    }
                }
                else if (stage == 1) {
                    // This is a non-empty subgroup
                    if (!entry.queuedQueries.isEmpty()) {
                        // Is a leaf group
                        entry.getNextQuery().map(queryInfo -> queuedQueries.add(queryInfo));
                        if (!entry.isEmpty()) {
                            entry.parent.map(parentEntry -> parentEntry.eligibleSubGroups.addOrUpdate(entry, entry.priority));
                        }
                    }
                    else {
                        stack.push(new StackFrame(entry, 2));
                        QueueInfo subGroup = entry.eligibleSubGroups.poll();
                        stack.push(new StackFrame(subGroup, 1));
                    }
                }
                else if (stage == 2) {
                    // Non empty intermediate group will be added back to parent
                    entry.parent.map(parentEntry -> parentEntry.eligibleSubGroups.addOrUpdate(entry, entry.priority));
                }
            }
            return queuedQueries.build();
        }
    }

    @Override
    public boolean getJmxExport()
    {
        synchronized (root) {
            return jmxExport;
        }
    }

    @Override
    public void setJmxExport(boolean export)
    {
        synchronized (root) {
            jmxExport = export;
        }
        jmxExportListener.accept(this, export);
    }

    public InternalResourceGroup getOrCreateSubGroup(String name)
    {
        requireNonNull(name, "name is null");
        synchronized (root) {
            checkArgument(runningQueries.isEmpty() && queuedQueries.isEmpty(), "Cannot add sub group to %s while queries are running", id);
            if (subGroups.containsKey(name)) {
                return subGroups.get(name);
            }
            InternalResourceGroup subGroup = new InternalResourceGroup(Optional.of(this), name, jmxExportListener, executor);
            // Sub group must use query priority to ensure ordering
            if (schedulingPolicy == QUERY_PRIORITY) {
                subGroup.setSchedulingPolicy(QUERY_PRIORITY);
            }
            subGroups.put(name, subGroup);
            return subGroup;
        }
    }

    public void run(QueryExecution query)
    {
        synchronized (root) {
            checkState(subGroups.isEmpty(), "Cannot add queries to %s. It is not a leaf group.", id);
            // Check all ancestors for capacity
            InternalResourceGroup group = this;
            boolean canQueue = true;
            boolean canRun = true;
            while (true) {
                canQueue &= group.canQueueMore();
                canRun &= group.canRunMore();
                if (!group.parent.isPresent()) {
                    break;
                }
                group = group.parent.get();
            }
            if (!canQueue && !canRun) {
                query.fail(new PrestoException(QUERY_QUEUE_FULL, format("Too many queued queries for \"%s\"", id)));
                return;
            }
            if (canRun) {
                startInBackground(query);
            }
            else {
                enqueueQuery(query);
            }
            query.addStateChangeListener(state -> {
                if (state.isDone()) {
                    queryFinished(query);
                }
            });
            if (query.getState().isDone()) {
                queryFinished(query);
            }
        }
    }

    private void enqueueQuery(QueryExecution query)
    {
        checkState(Thread.holdsLock(root), "Must hold lock to enqueue a query");
        synchronized (root) {
            queuedQueries.addOrUpdate(query, getQueryPriority(query.getSession()));
            InternalResourceGroup group = this;
            while (group.parent.isPresent()) {
                group.parent.get().descendantQueuedQueries++;
                group = group.parent.get();
            }
            updateEligiblility();
        }
    }

    private void updateEligiblility()
    {
        checkState(Thread.holdsLock(root), "Must hold lock to update eligibility");
        synchronized (root) {
            if (!parent.isPresent()) {
                return;
            }
            if (isEligibleToStartNext()) {
                parent.get().eligibleSubGroups.addOrUpdate(this, getSubGroupSchedulingPriority(parent.get().schedulingPolicy, this));
            }
            else {
                parent.get().eligibleSubGroups.remove(this);
            }
            parent.get().updateEligiblility();
        }
    }

    private void startInBackground(QueryExecution query)
    {
        checkState(Thread.holdsLock(root), "Must hold lock to start a query");
        synchronized (root) {
            runningQueries.add(query);
            InternalResourceGroup group = this;
            while (group.parent.isPresent()) {
                group.parent.get().descendantRunningQueries++;
                group.parent.get().dirtySubGroups.add(group);
                group = group.parent.get();
            }
            updateEligiblility();
            executor.execute(() -> query.start(Optional.of(id.toString())));
        }
    }

    private void queryFinished(QueryExecution query)
    {
        synchronized (root) {
            if (!runningQueries.contains(query) && !queuedQueries.contains(query)) {
                // Query has already been cleaned up
                return;
            }
            // Only count the CPU time if the query succeeded, or the failure was the fault of the user
            if (query.getState() == QueryState.FINISHED || query.getQueryInfo().getErrorType() == USER_ERROR) {
                InternalResourceGroup group = this;
                while (group != null) {
                    try {
                        group.cpuUsageMillis = Math.addExact(group.cpuUsageMillis, query.getTotalCpuTime().toMillis());
                    }
                    catch (ArithmeticException e) {
                        group.cpuUsageMillis = Long.MAX_VALUE;
                    }
                    group = group.parent.orElse(null);
                }
            }
            if (runningQueries.contains(query)) {
                runningQueries.remove(query);
                InternalResourceGroup group = this;
                while (group.parent.isPresent()) {
                    group.parent.get().descendantRunningQueries--;
                    group = group.parent.get();
                }
            }
            else {
                queuedQueries.remove(query);
                InternalResourceGroup group = this;
                while (group.parent.isPresent()) {
                    group.parent.get().descendantQueuedQueries--;
                    group = group.parent.get();
                }
            }
            updateEligiblility();
        }
    }

    protected void internalRefreshStats()
    {
        checkState(Thread.holdsLock(root), "Must hold lock to refresh stats");
        synchronized (root) {
            if (subGroups.isEmpty()) {
                cachedMemoryUsageBytes = 0;
                for (QueryExecution query : runningQueries) {
                    cachedMemoryUsageBytes += query.getTotalMemoryReservation();
                }
            }
            else {
                for (Iterator<InternalResourceGroup> iterator = dirtySubGroups.iterator(); iterator.hasNext(); ) {
                    InternalResourceGroup subGroup = iterator.next();
                    long oldMemoryUsageBytes = subGroup.cachedMemoryUsageBytes;
                    cachedMemoryUsageBytes -= oldMemoryUsageBytes;
                    subGroup.internalRefreshStats();
                    cachedMemoryUsageBytes += subGroup.cachedMemoryUsageBytes;
                    if (!subGroup.isDirty()) {
                        iterator.remove();
                    }
                    if (oldMemoryUsageBytes != subGroup.cachedMemoryUsageBytes) {
                        subGroup.updateEligiblility();
                    }
                }
            }
        }
    }

    protected void internalGenerateCpuQuota(long elapsedSeconds)
    {
        checkState(Thread.holdsLock(root), "Must hold lock to generate cpu quota");
        synchronized (root) {
            long newQuota;
            try {
                newQuota = Math.multiplyExact(elapsedSeconds, cpuQuotaGenerationMillisPerSecond);
            }
            catch (ArithmeticException e) {
                newQuota = Long.MAX_VALUE;
            }
            try {
                cpuUsageMillis = Math.subtractExact(cpuUsageMillis, newQuota);
            }
            catch (ArithmeticException e) {
                cpuUsageMillis = 0;
            }
            cpuUsageMillis = Math.max(0, cpuUsageMillis);
            for (InternalResourceGroup group : subGroups.values()) {
                group.internalGenerateCpuQuota(elapsedSeconds);
            }
        }
    }

    protected boolean internalStartNext()
    {
        checkState(Thread.holdsLock(root), "Must hold lock to find next query");
        synchronized (root) {
            if (!canRunMore()) {
                return false;
            }
            QueryExecution query = queuedQueries.poll();
            if (query != null) {
                startInBackground(query);
                return true;
            }

            // Remove even if the sub group still has queued queries, so that it goes to the back of the queue
            InternalResourceGroup subGroup = eligibleSubGroups.poll();
            if (subGroup == null) {
                return false;
            }
            boolean started = subGroup.internalStartNext();
            checkState(started, "Eligible sub group had no queries to run");
            descendantQueuedQueries--;
            // Don't call updateEligibility here, as we're in a recursive call, and don't want to repeatedly update our ancestors.
            if (subGroup.isEligibleToStartNext()) {
                eligibleSubGroups.addOrUpdate(subGroup, getSubGroupSchedulingPriority(schedulingPolicy, subGroup));
            }
            return true;
        }
    }

    private static int getSubGroupSchedulingPriority(SchedulingPolicy policy, InternalResourceGroup group)
    {
        if (policy == QUERY_PRIORITY) {
            return group.getHighestQueryPriority();
        }
        else {
            return group.getSchedulingWeight();
        }
    }

    private boolean isDirty()
    {
        checkState(Thread.holdsLock(root), "Must hold lock");
        synchronized (root) {
            return runningQueries.size() + descendantRunningQueries > 0;
        }
    }

    private boolean isEligibleToStartNext()
    {
        checkState(Thread.holdsLock(root), "Must hold lock");
        synchronized (root) {
            if (!canRunMore()) {
                return false;
            }
            return !queuedQueries.isEmpty() || !eligibleSubGroups.isEmpty();
        }
    }

    private int getHighestQueryPriority()
    {
        checkState(Thread.holdsLock(root), "Must hold lock");
        synchronized (root) {
            checkState(queuedQueries instanceof IndexedPriorityQueue, "Queued queries not ordered");
            if (queuedQueries.isEmpty()) {
                return 0;
            }
            return getQueryPriority(queuedQueries.peek().getSession());
        }
    }

    private boolean canQueueMore()
    {
        checkState(Thread.holdsLock(root), "Must hold lock");
        synchronized (root) {
            return descendantQueuedQueries + queuedQueries.size() < maxQueuedQueries;
        }
    }

    private boolean canRunMore()
    {
        checkState(Thread.holdsLock(root), "Must hold lock");
        synchronized (root) {
            if (cpuUsageMillis >= hardCpuLimitMillis) {
                return false;
            }

            int maxRunning = maxRunningQueries;
            if (cpuUsageMillis >= softCpuLimitMillis) {
                // Linear penalty between soft and hard limit
                double penalty = (cpuUsageMillis - softCpuLimitMillis) / (double) (hardCpuLimitMillis - softCpuLimitMillis);
                maxRunning = (int) Math.floor(maxRunning * (1 - penalty));
                // Always penalize by at least one
                maxRunning = Math.min(maxRunningQueries - 1, maxRunning);
                // Always allow at least one running query
                maxRunning = Math.max(1, maxRunning);
            }
            return runningQueries.size() + descendantRunningQueries < maxRunning &&
                    cachedMemoryUsageBytes < softMemoryLimitBytes;
        }
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("id", id)
                .toString();
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (!(o instanceof InternalResourceGroup)) {
            return false;
        }
        InternalResourceGroup that = (InternalResourceGroup) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(id);
    }

    @ThreadSafe
    public static final class RootInternalResourceGroup
            extends InternalResourceGroup
    {
        public RootInternalResourceGroup(String name, BiConsumer<InternalResourceGroup, Boolean> jmxExportListener, Executor executor)
        {
            super(Optional.empty(), name, jmxExportListener, executor);
        }

        public synchronized void processQueuedQueries()
        {
            internalRefreshStats();
            while (internalStartNext()) {
                // start all the queries we can
            }
        }

        public synchronized void generateCpuQuota(long elapsedSeconds)
        {
            if (elapsedSeconds > 0) {
                internalGenerateCpuQuota(elapsedSeconds);
            }
        }
    }
}
