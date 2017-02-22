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
package com.facebook.presto.plugin.memory.config;

import com.facebook.presto.plugin.memory.MemoryConfig;
import com.facebook.presto.plugin.memory.config.db.MemoryConfigDao;
import com.facebook.presto.spi.NodeManager;
import com.google.common.base.Throwables;
import io.airlift.log.Logger;

import javax.inject.Inject;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.collect.Iterables.getOnlyElement;

public class MemoryConfigManager
{
    private static final Logger log = Logger.get(MemoryConfigManager.class);
    private final AtomicReference<MemoryConfig> config = new AtomicReference<>();
    private final MemoryConfigDao dao;
    private final boolean isCoordinator;

    @Inject
    public MemoryConfigManager(MemoryConfigDao dao, MemoryConfig initialConfig, NodeManager nodeManager)
    {
        this.dao = dao;
        config.set(initialConfig);
        this.isCoordinator = nodeManager.getCurrentNode().isCoordinator();
        dao.createConfigTable();
        load();
    }

    public MemoryConfig getConfig()
    {
        return config.get();
    }

    private synchronized void load()
    {
        try {
            List<MemoryConfig> memoryConfig = dao.getMemoryConfig();
            if (memoryConfig.isEmpty() && isCoordinator) {
                MemoryConfig currentConfig = config.get();
                dao.insertMemoryConfig(
                        currentConfig.getMaxDataPerNode().toBytes(),
                        currentConfig.getMaxTableSizePerNode().toBytes(),
                        currentConfig.getSplitsPerNode());
            }
            else {
                config.set(getOnlyElement(memoryConfig));
            }
        }
        catch (Exception e) {
            log.error("Failed to load config: %s", e);
            e.printStackTrace();
        }
    }

    public synchronized void setMaxDataPerNode(long maxDataSizePerNode)
    {
        try {
            if (isCoordinator) {
                dao.updateMaxDataPerNode(maxDataSizePerNode);
            }
        }
        catch (Exception e) {
            log.error("Failed to update max data size per node: %s", e);
            Throwables.propagate(e);
        }
        load();
    }

    public void setMaxTableSizePerNode(long maxTableSizePerNode)
    {
        try {
            if (isCoordinator) {
                dao.updateMaxTableSizePerNode(maxTableSizePerNode);
            }
        }
        catch (Exception e) {
            log.error("Failed to update max table size per node: %s", e);
            Throwables.propagate(e);
        }
        load();
    }

    public void setSplitsPerNode(int splitsPerNode)
    {
        try {
            if (isCoordinator) {
                dao.updateSplitsPerNode(splitsPerNode);
            }
        }
        catch (Exception e) {
            log.error("Failed to update splits per node: %s", e);
            Throwables.propagate(e);
        }
        load();
    }
}
