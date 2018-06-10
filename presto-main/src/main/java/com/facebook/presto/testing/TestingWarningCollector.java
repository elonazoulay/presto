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
package com.facebook.presto.testing;

import com.facebook.presto.execution.warnings.DefaultWarningCollector;
import com.facebook.presto.execution.warnings.WarningCollectorConfig;
import com.facebook.presto.spi.PrestoWarning;
import com.facebook.presto.spi.WarningCode;

import javax.annotation.concurrent.ThreadSafe;

import static java.util.Objects.requireNonNull;
import static java.util.stream.IntStream.range;

@ThreadSafe
public class TestingWarningCollector
        extends DefaultWarningCollector
{
    public TestingWarningCollector(WarningCollectorConfig config, TestingWarningCollectorConfig testConfig)
    {
        super(config);
        // Start warning codes at 1
        range(1, requireNonNull(testConfig, "testConfig is null").getTestWarnings() + 1)
                .mapToObj(warning -> new PrestoWarning(new WarningCode(warning, "warningCode" + warning), "Test warning " + warning))
                .forEach(super::add);
    }
}
