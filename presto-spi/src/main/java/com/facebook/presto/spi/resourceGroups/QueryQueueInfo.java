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
package com.facebook.presto.spi.resourceGroups;

import com.facebook.presto.spi.QueryId;

import java.util.ArrayList;
import java.util.List;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class QueryQueueInfo
{
    private final ResourceGroupId rootGroupId;
    private final List<QueryEntry> queryQueue;

    public QueryQueueInfo(ResourceGroupId rootGroupId, List<QueryEntry> queryQueue)
    {
        this.rootGroupId = requireNonNull(rootGroupId, "Root group id is null");
        this.queryQueue = new ArrayList<>(requireNonNull(queryQueue, "Query queue is null"));
    }

    public ResourceGroupId getRootGroupId()
    {
        return rootGroupId;
    }

    public List<QueryEntry> getQueryQueue()
    {
        return new ArrayList<>(queryQueue);
    }

    public static class QueryEntry
    {
        private final ResourceGroupId leafGroupId;
        private final QueryId queryId;
        private final int approximatePosition;
        private final boolean isQueued;

        public QueryEntry(ResourceGroupId leafGroupId, QueryId queryId, int approximatePosition, boolean isQueued)
        {
            this.leafGroupId = leafGroupId;
            this.queryId = queryId;
            this.approximatePosition = approximatePosition;
            this.isQueued = isQueued;
        }

        public ResourceGroupId getLeafGroupId()
        {
            return leafGroupId;
        }

        public QueryId getQueryId()
        {
            return queryId;
        }

        public int getApproximatePosition()
        {
            return approximatePosition;
        }

        public boolean getIsQueued()
        {
            return isQueued;
        }
    }

    public static class Builder
    {
        private ResourceGroupId rootGroupId = null;
        private final List<QueryEntry> queryQueue = new ArrayList<>();

        private Builder()
        {
        }

        public void setRootGroupId(ResourceGroupId rootGroupId)
        {
            this.rootGroupId = rootGroupId;
        }

        public void add(QueryEntry entry)
        {
            queryQueue.add(entry);
        }

        public void addFromBuilder(Builder builder)
        {
            // Sanity check that both builders pertain to the same root group
            if (!builder.rootGroupId.equals(rootGroupId)) {
                throw new IllegalStateException(format("Root group id mismatch"));
            }
            queryQueue.addAll(builder.queryQueue);
        }

        public QueryQueueInfo build()
        {
            return new QueryQueueInfo(rootGroupId, queryQueue);
        }
    }

    public static Builder builder()
    {
        return new Builder();
    }
}
