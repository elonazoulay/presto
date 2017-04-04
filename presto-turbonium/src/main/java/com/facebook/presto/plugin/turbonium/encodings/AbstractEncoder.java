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
import com.facebook.presto.spi.type.Type;

public abstract class AbstractEncoder<T>
    implements SegmentEncoder
{
    protected final Type type;
    protected final Stats<T> stats;
    protected final StandardEncodings encoding;

    public AbstractEncoder(Type type, Stats<T> stats)
    {
        this.type = type;
        this.stats = stats;
        this.encoding = determineEncoding();
    }

    private boolean doNull()
    {
        return stats.getNonNullCount() == 0 && stats.getNullCount() > 0;
    }

    private boolean doRle()
    {
        return stats.getSingleValue().isPresent() && stats.getNullCount() == 0;
    }

    private boolean doRleNull()
    {
        return stats.getSingleValue().isPresent() && stats.getNullCount() > 0;
    }

    private boolean doDictionary()
    {
        return stats.getDistinctValues().isPresent();
    }

    private boolean doDelta()
    {
        return stats.getDelta().isPresent();
    }

    private StandardEncodings determineEncoding()
    {
        if (doNull()) {
            return StandardEncodings.NULL;
        }
        else if (doRle()) {
            return StandardEncodings.RLE;
        }
        else if (doRleNull()) {
            return StandardEncodings.RLE_NULL;
        }
        else if (doDictionary()) {
            return StandardEncodings.DICTIONARY;
        }
        else if (doDelta()) {
            return StandardEncodings.DELTA;
        }
        else {
            return StandardEncodings.NONE;
        }
    }
}
