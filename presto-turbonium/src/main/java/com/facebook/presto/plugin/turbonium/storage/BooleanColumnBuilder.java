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
package com.facebook.presto.plugin.turbonium.storage;

import com.facebook.presto.spi.block.Block;
import com.facebook.presto.spi.block.BlockBuilder;
import com.facebook.presto.spi.type.Type;
import org.openjdk.jol.info.ClassLayout;

import java.util.BitSet;

import static com.facebook.presto.spi.type.BooleanType.BOOLEAN;
import static io.airlift.slice.SizeOf.sizeOf;

public class BooleanColumnBuilder
        extends AbstractColumnBuilder
{
    public BooleanColumnBuilder(int channel, Type type)
    {
        super(channel, type);
    }

    @Override
    protected SegmentBuilder createSegmentBuilder()
    {
        return new BooleanSegment.Builder(getChannel(), getType());
    }

    public static class BooleanSegment
            extends AbstractSegment
    {
        private static final int INSTANCE_SIZE = ClassLayout.parseClass(BooleanSegment.class).instanceSize();
        private final boolean[] values;

        public BooleanSegment(Type type, BitSet isNull, boolean[] values, int size)
        {
            super(type, isNull, size);
            this.values = values;
        }

        @Override
        protected void writeValue(BlockBuilder blockBuilder, int position)
        {
            BOOLEAN.writeBoolean(blockBuilder, values[position]);
        }

        @Override
        public long getSizeBytes()
        {
            return INSTANCE_SIZE + isNullSizeBytes() + sizeOf(values);
        }

        public static class Builder
                extends AbstractSegmentBuilder
        {
            private final boolean[] values = new boolean[DEFAULT_SEGMENT_SIZE];
            private int internalPosition;
            private final BitSet isNull = new BitSet(DEFAULT_SEGMENT_SIZE);

            Builder(int channel, Type type)
            {
                super(channel, type);
            }

            private void appendValue(Block block, int position)
            {
                values[size()] = getType().getBoolean(block, position);
            }
            @Override
            public void append(Block block, int position)
            {
                if (block.isNull(position)) {
                    isNull.set(internalPosition);
                }
                else {
                    appendValue(block, position);
                }
                internalPosition++;
            }

            @Override
            public int size()
            {
                return internalPosition;
            }

            @Override
            public Segment build()
            {
                return new BooleanSegment(getType(), isNull, values, size());
            }
        }
    }
}
