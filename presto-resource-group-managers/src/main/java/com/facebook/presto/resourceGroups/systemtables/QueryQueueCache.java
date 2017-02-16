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

import com.facebook.presto.spi.resourceGroups.QueryQueueInfo;
import com.facebook.presto.spi.resourceGroups.ResourceGroup;
import com.facebook.presto.spi.resourceGroups.ResourceGroupId;
import com.google.common.collect.ImmutableList;
import io.airlift.log.Logger;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static io.airlift.concurrent.Threads.daemonThreadsNamed;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

public class QueryQueueCache
{
    private final Logger log = Logger.get(QueryQueueCache.class);
    private final Map<ResourceGroupId, QueryQueueInfo> queueInfo = new ConcurrentHashMap<>();
    private final ScheduledExecutorService refreshInfoExecutor = newSingleThreadScheduledExecutor(daemonThreadsNamed("QueryQueueInfo"));
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final Map<ResourceGroupId, ResourceGroup> rootGroups = new ConcurrentHashMap<>();
    private final AtomicLong counter = new AtomicLong();

    public void start()
    {
        if (started.compareAndSet(false, true)) {
            refreshInfoExecutor.scheduleWithFixedDelay(this::refreshCache, 1, 1, TimeUnit.SECONDS);
        }
    }

    public void destroy()
    {
        refreshInfoExecutor.shutdownNow();
    }

    private void refreshCache()
    {
        try {
            counter.incrementAndGet();
            for (ResourceGroupId rootGroupId : ImmutableList.copyOf(rootGroups.keySet())) {
                ResourceGroup rootGroup = rootGroups.get(rootGroupId);
                if (rootGroup == null) {
                    continue;
                }
                Optional<QueryQueueInfo> rootGroupInfo = rootGroup.getQueryQueueInfo();
                if (rootGroupInfo.isPresent()) {
                    queueInfo.put(rootGroupId, rootGroupInfo.get());
                }
                else {
                    queueInfo.put(rootGroupId, QueryQueueInfo.builder().setRootGroupId(rootGroupId).build());
                }
                //rootGroup.getQueryQueueInfo().map(queryQueueInfo -> queueInfo.put(rootGroupId, queryQueueInfo));
            }
        }
        catch (Exception ex) {
            log.error("Refresh query queue cache failed: %s", ex);
        }
    }

    public List<QueryQueueInfo> getQueueInfo()
    {
        return ImmutableList.copyOf(queueInfo.values());
    }

    public void addGroup(ResourceGroup group)
    {
        if (!group.getId().getParent().isPresent()) {
            // Only add root groups
            rootGroups.put(group.getId(), group);
        }
    }
}
