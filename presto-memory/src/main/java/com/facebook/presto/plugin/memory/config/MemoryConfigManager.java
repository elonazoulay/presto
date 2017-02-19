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
import com.facebook.presto.plugin.memory.config.db.MemoryConfigSpec;
import com.facebook.presto.spi.NodeManager;
import com.google.common.base.Throwables;
import io.airlift.log.Logger;
import io.airlift.units.DataSize;

import javax.inject.Inject;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.collect.Iterables.getOnlyElement;
import static io.airlift.units.DataSize.Unit.BYTE;
import static java.lang.Math.toIntExact;

public class MemoryConfigManager
{
    private static final Logger log = Logger.get(MemoryConfigManager.class);
    private final AtomicReference<MemoryConfig> config = new AtomicReference<>();
    private final MemoryConfigDao dao;
    private final boolean canWrite;

    @Inject
    public MemoryConfigManager(MemoryConfigDao dao, MemoryConfig initialConfig, NodeManager nodeManager)
    {
        this.dao = dao;
        config.set(initialConfig);
        canWrite = nodeManager.getCurrentNode().isCoordinator();
        dao.createConfigTable();
        load();
    }

    public MemoryConfig getConfig()
    {
        return config.get();
    }

    public MemoryConfigSpec getStaticConfig()
    {
        MemoryConfig config = this.config.get();
        return new MemoryConfigSpec(config.getMaxDataPerNode().toBytes(),
                config.getMaxTableSizePerNode().toBytes(),
                config.getSplitsPerNode());
    }

    public void setFromConfig(MemoryConfigSpec config)
    {
        MemoryConfig newConfig = new MemoryConfig()
                .setMaxDataPerNode(new DataSize(config.getMaxDataPerNode(), BYTE))
                .setMaxTableSizePerNode(new DataSize(config.getMaxTableSizePerNode(), BYTE))
                .setSplitsPerNode(toIntExact(config.getSplitsPerNode()));

        this.config.set(newConfig);
    }

    private synchronized void load()
    {
        try {
            List<MemoryConfig> memoryConfig = dao.getMemoryConfig();
            if (memoryConfig.isEmpty()) {
                if (canWrite) {
                    MemoryConfig currentConfig = config.get();
                    dao.insertMemoryConfig(
                            currentConfig.getMaxDataPerNode().toBytes(),
                            currentConfig.getMaxTableSizePerNode().toBytes(),
                            currentConfig.getSplitsPerNode());
                }
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
        config.set(copyMemoryConfig().setMaxDataPerNode(new DataSize(maxDataSizePerNode, BYTE)));
        try {
            if (canWrite) {
                dao.updateMaxDataPerNode(maxDataSizePerNode);
            }
        }
        catch (Exception e) {
            log.error("Failed to update max data size per node: %s", e);
            Throwables.propagate(e);
        }
    }

    public synchronized void setMaxTableSizePerNode(long maxTableSizePerNode)
    {
        config.set(copyMemoryConfig().setMaxTableSizePerNode(new DataSize(maxTableSizePerNode, BYTE)));
        try {
            if (canWrite) {
                dao.updateMaxTableSizePerNode(maxTableSizePerNode);
            }
        }
        catch (Exception e) {
            log.error("Failed to update max table size per node: %s", e);
            Throwables.propagate(e);
        }
    }

    public synchronized void setSplitsPerNode(int splitsPerNode)
    {
        config.set(copyMemoryConfig().setSplitsPerNode(splitsPerNode));
        try {
            if (canWrite) {
                dao.updateSplitsPerNode(splitsPerNode);
            }
        }
        catch (Exception e) {
            log.error("Failed to update splits per node: %s", e);
            Throwables.propagate(e);
        }
    }

    private MemoryConfig copyMemoryConfig()
    {
        MemoryConfig newConfig = new MemoryConfig();
        MemoryConfig currentConfig = config.get();
        return newConfig.setMaxDataPerNode(currentConfig.getMaxDataPerNode())
                .setMaxTableSizePerNode(currentConfig.getMaxTableSizePerNode())
                .setSplitsPerNode(currentConfig.getSplitsPerNode());
    }
}
