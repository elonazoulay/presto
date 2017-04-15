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
import com.facebook.presto.spi.predicate.Domain;
import com.facebook.presto.spi.type.Type;
import io.airlift.slice.Slice;
import org.openjdk.jol.info.ClassLayout;

import java.util.BitSet;

import static io.airlift.slice.SizeOf.sizeOf;

public class SliceColumnBuilder
        extends AbstractColumnBuilder
{
    public SliceColumnBuilder(int channel, Type type)
    {
        super(channel, type);
    }

    @Override
    protected SegmentBuilder createSegmentBuilder()
    {
        return new SliceSegment.Builder(getChannel(), getType());
    }

    public static class SliceSegment
            extends AbstractSegment
    {
        private static final int INSTANCE_SIZE = ClassLayout.parseClass(SliceSegment.class).instanceSize();
        private final Slice[] values;
        private final Domain domain;

        public SliceSegment(Type type, BitSet isNull, Slice[] values, int size)
        {
            super(type, isNull, size);
            this.values = values;
            this.domain = Domain.all(type);
        }

        @Override
        protected void writeValue(BlockBuilder blockBuilder, int position)
        {
            getType().writeSlice(blockBuilder, values[position]);
        }

        @Override
        public long getSizeBytes()
        {
            return INSTANCE_SIZE + isNullSizeBytes() + sizeOf(values);
        }

        @Override
        public Domain getDomain()
        {
            return domain;
        }

        public static class Builder
                extends AbstractSegmentBuilder
        {
            private final Slice[] values = new Slice[DEFAULT_SEGMENT_SIZE];
            private int internalPosition;
            private final BitSet isNull = new BitSet(DEFAULT_SEGMENT_SIZE);

            Builder(int channel, Type type)
            {
                super(channel, type);
            }

            private void appendValue(Block block, int position)
            {
                values[size()] = getType().getSlice(block, position);
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
                return new SliceSegment(getType(), isNull, values, size());
            }
        }
    }
}
