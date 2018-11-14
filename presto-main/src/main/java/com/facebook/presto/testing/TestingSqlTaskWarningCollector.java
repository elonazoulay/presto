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

import com.facebook.presto.execution.warnings.ClearingWarningCollector;
import com.facebook.presto.execution.warnings.WarningCollectorConfig;
import com.facebook.presto.spi.PrestoWarning;
import com.facebook.presto.spi.WarningCode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

@ThreadSafe
public class TestingSqlTaskWarningCollector
        implements ClearingWarningCollector
{
    @GuardedBy("this")
    private final Set<WarningCode> warningCodes = new HashSet<>();

    @GuardedBy("this")
    private ImmutableList.Builder<PrestoWarning> warningBuilder = ImmutableList.builder();

    private final WarningCollectorConfig config;

    private final boolean addWarnings;
    private final AtomicInteger warningCode = new AtomicInteger();

    public TestingSqlTaskWarningCollector(WarningCollectorConfig config, TestingWarningCollectorConfig testConfig)
    {
        this.config = requireNonNull(config, "config is null");
        requireNonNull(testConfig, "testConfig is null");
        addWarnings = testConfig.getAddWarnings();
        // Start warning codes at 1
        for (int warningCode = 1; warningCode <= testConfig.getPreloadedWarnings(); warningCode++) {
            add(createTestSqlTaskWarning(warningCode));
        }
        warningCode.set(testConfig.getPreloadedWarnings());
    }

    @Override
    public synchronized void add(PrestoWarning warning)
    {
        requireNonNull(warning, "warning is null");
        if (warningCodes.size() < config.getMaxWarnings() && warningCodes.add(warning.getWarningCode())) {
            warningBuilder.add(warning);
        }
    }

    @Override
    public synchronized List<PrestoWarning> getAndClearWarnings()
    {
        if (addWarnings) {
            add(createTestSqlTaskWarning(warningCode.incrementAndGet()));
        }
        List<PrestoWarning> warnings = warningBuilder.build();
        warningBuilder = ImmutableList.builder();
        return warnings;
    }

    @VisibleForTesting
    public static PrestoWarning createTestSqlTaskWarning(int code)
    {
        // format string below is a hack to construct a vendor specific SQLState value
        // 01 is the class of warning code and 5 is the first allowed vendor defined prefix character
        // See the SQL Standard ISO_IEC_9075-2E_2016 24.1: SQLState for more information
        return new PrestoWarning(new WarningCode(code, format("015%02d", code % 100)), "Test SqlTask warning " + code);
    }
}
