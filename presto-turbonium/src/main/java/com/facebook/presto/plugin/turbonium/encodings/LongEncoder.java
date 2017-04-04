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
package com.facebook.presto.plugin.turbonium.encodings;

import com.facebook.presto.plugin.turbonium.stats.Stats;
import com.facebook.presto.plugin.turbonium.storage.LongSegments;
import com.facebook.presto.plugin.turbonium.storage.LongSegments.AllValues;
import com.facebook.presto.plugin.turbonium.storage.LongSegments.Dictionary;
import com.facebook.presto.plugin.turbonium.storage.LongSegments.Rle;
import com.facebook.presto.plugin.turbonium.storage.LongSegments.RleWithNulls;
import com.facebook.presto.plugin.turbonium.storage.LongSegments.SortedDictionary;
import com.facebook.presto.plugin.turbonium.storage.NullSegment;
import com.facebook.presto.plugin.turbonium.storage.Segment;
import com.facebook.presto.spi.type.Type;

import java.util.BitSet;

import static com.facebook.presto.plugin.turbonium.encodings.DeltaValuesBuilder.buildLongValues;

public class LongEncoder
    extends AbstractEncoder<Long>
{
    private final long[] values;
    private final BitSet isNull;
    public LongEncoder(Stats<Long> stats, Type type, BitSet isNull, long[] values)
    {
        super(type, stats);
        this.values = values;
        this.isNull = isNull;
    }

    @Override
    public Segment encode()
    {
        switch (encoding) {
            case NONE:
                return new AllValues(type, isNull, values, stats.size());
            case NULL:
                return new NullSegment(stats.size());
            case RLE:
                return new Rle(stats.size(), stats.getSingleValue().get());
            case RLE_NULL:
                return new RleWithNulls(type, isNull, stats.getSingleValue().get(), stats.size());
            case DICTIONARY:
                return new Dictionary(type, isNull, stats.getDistinctValues().get(), stats.size());
            case SORTED_DICTIONARY:
                return new SortedDictionary(type, isNull, stats.getDistinctValues().get(), stats.size());
            case DELTA:
                return encodeDelta();
            default:
                throw new IllegalStateException("undefined encoding");
        }
    }

    private Segment encodeDelta()
    {
        return buildLongValues(stats.getMin().get(), stats.getDelta().get(), values, stats.size())
                .map(values -> (Segment) new LongSegments.Delta(type, isNull, stats.getMin().get(), values, stats.size()))
                .orElse(new AllValues(type, isNull, values, stats.size()));
    }
}
