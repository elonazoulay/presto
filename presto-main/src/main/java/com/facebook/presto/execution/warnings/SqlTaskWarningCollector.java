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
import com.facebook.presto.spi.WarningCode;
import com.google.common.collect.ImmutableList;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Objects.requireNonNull;

@ThreadSafe
public class SqlTaskWarningCollector
        implements ClearingWarningCollector
{
    @GuardedBy("this")
    private final Set<WarningCode> warningCodes = new HashSet<>();

    @GuardedBy("this")
    private ImmutableList.Builder<PrestoWarning> warningsBuilder = ImmutableList.builder();

    private final WarningCollectorConfig config;

    public SqlTaskWarningCollector(WarningCollectorConfig config)
    {
        this.config = requireNonNull(config, "config is null");
    }

    @Override
    public synchronized void add(PrestoWarning warning)
    {
        requireNonNull(warning, "warning is null");
        if (warningCodes.size() < config.getMaxWarnings() && warningCodes.add(warning.getWarningCode())) {
            warningsBuilder.add(warning);
        }
    }

    @Override
    public synchronized List<PrestoWarning> getAndClearWarnings()
    {
        List<PrestoWarning> warnings = warningsBuilder.build();
        warningsBuilder = ImmutableList.builder();
        return warnings;
    }
}
