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
package com.facebook.presto.execution.warnings;

import com.facebook.presto.spi.PrestoWarning;
import com.google.common.collect.ImmutableList;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

@ThreadSafe
public class DefaultWarningCollector
        implements WarningCollector
{
    @GuardedBy("this")
    private final ImmutableList.Builder<PrestoWarning> warnings = ImmutableList.builder();

    @GuardedBy("this")
    private final Map<Integer, Integer> warningCodeCounts = new HashMap<>();

    private final WarningCollectorConfig config;

    @GuardedBy("this")
    private int size;

    @GuardedBy("this")
    private int lastReadSize;

    // Minimize copying since reads will be more frequent than writes
    @GuardedBy("this")
    private ImmutableList<PrestoWarning> cachedWarnings = ImmutableList.of();

    public DefaultWarningCollector(WarningCollectorConfig config)
    {
        this.config = requireNonNull(config, "config is null");
    }

    @Override
    public synchronized void add(PrestoWarning warning)
    {
        requireNonNull(warning, "warning is null");
        if (size < config.getMaxWarnings() &&
                (warningCodeCounts.containsKey(warning.getWarningCode().getCode()) ?
                        warningCodeCounts.size() <= config.getMaxDistinctWarningCodes() :
                        warningCodeCounts.size() < config.getMaxDistinctWarningCodes())) {
            int warningCodeCount = warningCodeCounts.getOrDefault(warning.getWarningCode().getCode(), 0);
            if (warningCodeCount < config.getMaxWarningsPerWarningCode()) {
                warnings.add(warning);
                warningCodeCounts.put(warning.getWarningCode().getCode(), ++warningCodeCount);
                size++;
            }
        }
    }

    @Override
    public synchronized List<PrestoWarning> getWarnings()
    {
        if (lastReadSize != size) {
            cachedWarnings = warnings.build();
            lastReadSize = size;
        }
        return cachedWarnings;
    }
}
