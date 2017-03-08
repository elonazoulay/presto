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

public enum SchedulingPolicy
{
    FAIR(false),
    WEIGHTED(false),
    QUERY_PRIORITY(true),
    WEIGHTED_FIFO(true);

    private final boolean isRecursive;
    SchedulingPolicy(boolean isRecursive)
    {
        this.isRecursive = isRecursive;
    }

    /*
     * This query policy is required to be shared from root to leaf resource group
     */
    public boolean isRecursive()
    {
        return isRecursive;
    }
}
