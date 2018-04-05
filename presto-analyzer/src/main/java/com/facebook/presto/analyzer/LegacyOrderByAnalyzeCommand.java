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
package com.facebook.presto.analyzer;

import com.google.inject.Injector;
import io.airlift.airline.Arguments;
import io.airlift.airline.Command;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.bootstrap.LifeCycleManager;

import static com.google.common.base.Throwables.throwIfUnchecked;

@Command(name = "semantic-analyzer", description = "Legacy order by analyzer")
public class LegacyOrderByAnalyzeCommand
        implements Runnable
{
    @Arguments(description = "Config filename")
    public String configFilename;

    public void run()
    {
        if (configFilename != null) {
            // Read
            System.setProperty("config", configFilename);
        }
        Injector injector;
        try {
            Bootstrap app = new Bootstrap(new PrestoLegacyOrderByAnalyzerModule());

            injector = app
                    .strictConfig()
                    .initialize();
        }
        catch (Exception e) {
            throwIfUnchecked(e);
            throw new RuntimeException(e);
        }

        try {
            injector.getInstance(LegacyOrderByRunner.class).run();
            System.exit(0);
        }
        catch (Exception e) {
            throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
        finally {
            try {
                injector.getInstance(LifeCycleManager.class).stop();
            }
            catch (Exception e) {
                throwIfUnchecked(e);
                throw new RuntimeException(e);
            }
        }
    }
}
