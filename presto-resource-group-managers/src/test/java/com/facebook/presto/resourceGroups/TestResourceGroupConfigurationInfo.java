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
import com.facebook.presto.spi.resourceGroups.ResourceGroupConfigurationManager;
import io.airlift.units.Duration;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.Optional;

import static com.facebook.presto.resourceGroups.db.TestResourceGroupConfigurationInfo.getExpectedResourceGroupSpec;
import static com.google.common.collect.Iterables.getOnlyElement;
import static io.airlift.json.JsonCodec.jsonCodec;
import static org.testng.Assert.assertEquals;

public class TestResourceGroupConfigurationInfo
{
    @Test
    public void testResourceGroupConfigurationInfo()
    {
        ResourceGroupConfigurationInfo configurationInfo = new ResourceGroupConfigurationInfo();
        // Create resource group configuration manager just to populate configurationInfo
        parse("resource_groups_config.json", configurationInfo);
        assertEquals(configurationInfo.getCpuQuotaPeriod(), Optional.of(Duration.valueOf("1h")));
        assertEquals(
                getOnlyElement(configurationInfo.getSelectorSpecs()),
                new SelectorSpec(
                        Optional.empty(),
                        Optional.empty(),
                        new ResourceGroupIdTemplate("global")));
        Map<ResourceGroupIdTemplate, ResourceGroupSpec> specs = configurationInfo.getResourceGroupSpecs();
        assertEquals(specs.size(), 2);
        ResourceGroupSpec actual = specs.get(new ResourceGroupIdTemplate("global"));
        assertEquals(actual, getExpectedResourceGroupSpec());
    }

    private FileResourceGroupConfigurationManager parse(String fileName, ResourceGroupConfigurationInfo configurationInfo)
    {
        FileResourceGroupConfig config = new FileResourceGroupConfig();
        config.setConfigFile(getResourceFilePath(fileName));
        return new FileResourceGroupConfigurationManager(
                (poolId, listener) -> { },
                config,
                jsonCodec(ManagerSpec.class),
                configurationInfo);
    }

    private String getResourceFilePath(String fileName)
    {
        return this.getClass().getClassLoader().getResource(fileName).getPath();
    }
}
