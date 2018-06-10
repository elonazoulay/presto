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

import io.airlift.configuration.Config;

import static com.google.common.base.Preconditions.checkArgument;

public class WarningCollectorConfig
{
    private int maxWarnings = Integer.MAX_VALUE;
    private int maxDistinctWarningCodes = Integer.MAX_VALUE;
    private int maxWarningsPerWarningCode = Integer.MAX_VALUE;

    @Config("warning-collector.max-warnings")
    public WarningCollectorConfig setMaxWarnings(int maxWarnings)
    {
        checkArgument(maxWarnings >= 0, "maxWarnings must be >= 0");
        this.maxWarnings = maxWarnings;
        return this;
    }

    public int getMaxWarnings()
    {
        return maxWarnings;
    }

    @Config("warning-collector.max-distinct-warning-codes")
    public WarningCollectorConfig setMaxDistinctWarningCodes(int maxDistinctWarningCodes)
    {
        checkArgument(maxDistinctWarningCodes >= 0, "maxDistinctWarningCodes must be >= 0");
        this.maxDistinctWarningCodes = maxDistinctWarningCodes;
        return this;
    }

    public int getMaxDistinctWarningCodes()
    {
        return maxDistinctWarningCodes;
    }

    @Config("warning-collector.max-warnings-per-warning-code")
    public WarningCollectorConfig setMaxWarningsPerWarningCode(int maxWarningsPerWarningCode)
    {
        checkArgument(maxWarningsPerWarningCode >= 0, "maxWarningsPerWarningCode must be >= 0");
        this.maxWarningsPerWarningCode = maxWarningsPerWarningCode;
        return this;
    }

    public int getMaxWarningsPerWarningCode()
    {
        return maxWarningsPerWarningCode;
    }
}
