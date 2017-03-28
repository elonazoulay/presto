package com.facebook.presto.plugin.turbonium.stats;

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
public class LongStats {
    private final long min;
    private final long max;
    private final int size;
    private final int distinctValues;
    private final int nullValues;
    private final boolean sorted;

    public LongStats(long min, long max, int size, int distinctValues, int nullValues, boolean sorted)
    {
        this.min = min;
        this.max = max;
        this.size = size;
        this.distinctValues = distinctValues;
        this.nullValues = nullValues;
        this.sorted = sorted;
    }



}
