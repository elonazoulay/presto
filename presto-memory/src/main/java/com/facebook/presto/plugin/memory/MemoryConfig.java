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
package com.facebook.presto.plugin.memory;

import io.airlift.configuration.Config;
import io.airlift.units.DataSize;

import javax.validation.constraints.NotNull;

public class MemoryConfig
{
    private int splitsPerNode = Runtime.getRuntime().availableProcessors();
    private DataSize maxDataPerNode = new DataSize(128, DataSize.Unit.MEGABYTE);
    private DataSize maxDataPerTablePerNode = new DataSize(32, DataSize.Unit.MEGABYTE);

    @NotNull
    public int getSplitsPerNode()
    {
        return splitsPerNode;
    }

    @Config("metadata-uri")
    public MemoryConfig setSplitsPerNode(int splitsPerNode)
    {
        this.splitsPerNode = splitsPerNode;
        return this;
    }

    @NotNull
    public DataSize getMaxDataPerNode()
    {
        return maxDataPerNode;
    }

    @Config("max-data-per-node")
    public MemoryConfig setMaxDataPerNode(DataSize maxDataPerNode)
    {
        this.maxDataPerNode = maxDataPerNode;
        return this;
    }

    @NotNull
    public DataSize getMaxDataPerTablePerNode()
    {
        return maxDataPerTablePerNode;
    }

    @Config("max-data-per-table-per-node")
    public MemoryConfig setMaxDataPerTablePerNode(DataSize maxDataPerTablePerNode)
    {
        this.maxDataPerTablePerNode = maxDataPerTablePerNode;
        return this;
    }
}
