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

import com.facebook.presto.plugin.turbonium.encodings.IntEncoder;
import com.facebook.presto.plugin.turbonium.stats.IntStatsBuilder;
import com.facebook.presto.spi.block.Block;
import com.facebook.presto.spi.block.BlockBuilder;
import com.facebook.presto.spi.type.Type;
import org.openjdk.jol.info.ClassLayout;

import java.util.BitSet;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static io.airlift.slice.SizeOf.sizeOf;

public class IntSegments
{
    private IntSegments() {}
    public static class Rle
        implements Segment
    {
        private static final int INSTANCE_SIZE = ClassLayout.parseClass(Rle.class).instanceSize();
        private final int size;
        private final int value;

        public Rle(int size, int value)
        {
            this.size = size;
            this.value = value;
        }

        @Override
        public int size()
        {
            return size;
        }

        @Override
        public void write(BlockBuilder blockBuilder, int position)
        {
            blockBuilder.writeInt(value);
        }

        @Override
        public long getSizeBytes()
        {
            return INSTANCE_SIZE;
        }
    }

    public static class RleWithNulls
        extends AbstractSegment
    {
        private static final int INSTANCE_SIZE = ClassLayout.parseClass(RleWithNulls.class).instanceSize();
        private final int value;
        public RleWithNulls(Type type, BitSet isNull, int value, int size)
        {
            super(type, isNull, size);
            this.value = value;
        }

        @Override
        protected void writeValue(BlockBuilder blockBuilder, int position)
        {
            blockBuilder.writeInt(value);
        }

        @Override
        public long getSizeBytes()
        {
            return INSTANCE_SIZE + isNullSizeBytes();
        }
    }

    public static class Dictionary
        extends AbstractSegment
    {
        private static final int INSTANCE_SIZE = ClassLayout.parseClass(Dictionary.class).instanceSize();
        private final int[] dictionary;
        private final byte[] values;
        public Dictionary(Type type, BitSet isNull, Map<Integer, List<Integer>> distinctValues, int size)
        {
            super(type, isNull, size);
            dictionary = new int[distinctValues.size()];
            values = new byte[size];
            int dictionaryId = 0;
            for (Map.Entry<Integer, List<Integer>> entry : distinctValues.entrySet()) {
                dictionary[dictionaryId] = entry.getKey();
                for (int position : entry.getValue()) {
                    values[position] = (byte) dictionaryId;
                }
                dictionaryId++;
            }
        }

        @Override
        protected void writeValue(BlockBuilder blockBuilder, int position)
        {
            blockBuilder.writeInt(dictionary[values[position] & 0xff]);
        }

        @Override
        public long getSizeBytes()
        {
            return INSTANCE_SIZE + isNullSizeBytes() + sizeOf(dictionary) + sizeOf(values);
        }
    }

    public static class SortedDictionary
        extends AbstractSegment
    {
        private static final int INSTANCE_SIZE = ClassLayout.parseClass(SortedDictionary.class).instanceSize();
        private final int[] dictionary;
        private final byte[] values;
        public SortedDictionary(Type type, BitSet isNull, Map<Integer, List<Integer>> distinctValues, int size)
        {
            super(type, isNull, size);
            dictionary = new int[distinctValues.size()];
            values = new byte[size];
            int dictionaryId = 0;
            for (Iterator<Map.Entry<Integer, List<Integer>>> iterator = distinctValues.entrySet().stream()
                    .sorted(Comparator.comparing(Map.Entry::getKey)).iterator(); iterator.hasNext(); ) {
                Map.Entry<Integer, List<Integer>> entry = iterator.next();
                dictionary[dictionaryId] = entry.getKey();
                for (int position : entry.getValue()) {
                    values[position] = (byte) dictionaryId;
                }
                dictionaryId++;
            }
        }
        @Override
        protected void writeValue(BlockBuilder blockBuilder, int position)
        {
            blockBuilder.writeInt(dictionary[values[position] & 0xff]);
        }

        @Override
        public long getSizeBytes()
        {
            return INSTANCE_SIZE + isNullSizeBytes() + sizeOf(dictionary) + sizeOf(values);
        }
    }

    public static class Delta
        extends AbstractSegment
    {
        private static final int INSTANCE_SIZE = ClassLayout.parseClass(Delta.class).instanceSize();
        private final int offset;
        private final Values values;
        public Delta(Type type, BitSet isNull, int offset, Values values, int size)
        {
            super(type, isNull, size);
            this.offset = offset;
            this.values = values;
        }
        @Override
        protected void writeValue(BlockBuilder blockBuilder, int position)
        {
            blockBuilder.writeInt(offset + values.getInt(position));
        }
        @Override
        public long getSizeBytes()
        {
            return INSTANCE_SIZE + isNullSizeBytes() + values.getSizeBytes();
        }
    }

    public static class AllValues
            extends AbstractSegment
    {
        private static final int INSTANCE_SIZE = ClassLayout.parseClass(AllValues.class).instanceSize();
        private final int[] values;

        public AllValues(Type type, BitSet isNull, int[] values, int size)
        {
            super(type, isNull, size);
            this.values = values;
        }

        @Override
        protected void writeValue(BlockBuilder blockBuilder, int position)
        {
            blockBuilder.writeInt(values[position]);
        }

        @Override
        public long getSizeBytes()
        {
            return INSTANCE_SIZE + isNullSizeBytes() + sizeOf(values);
        }
    }

    public static Builder builder(int channel, Type type)
    {
        return new Builder(channel, type);
    }

    public static class Builder
            extends AbstractSegmentBuilder
    {
        private final int[] values = new int[DEFAULT_SEGMENT_SIZE];
        private int internalPosition;
        private final BitSet isNull = new BitSet(DEFAULT_SEGMENT_SIZE);
        private final IntStatsBuilder statsBuilder = new IntStatsBuilder();

        private Builder(int channel, Type type)
        {
            super(channel, type);
        }

        @Override
        public void append(Block block, int position)
        {
            if (block.isNull(position)) {
                isNull.set(internalPosition);
                statsBuilder.add(null, internalPosition);
            }
            else {
                appendValue(block, position);
            }
            internalPosition++;
        }

        private void appendValue(Block block, int position)
        {
            int extractedValue = block.getInt(position, 0);
            values[internalPosition] = extractedValue;
            statsBuilder.add(extractedValue, internalPosition);
        }

        @Override
        public int size()
        {
            return internalPosition;
        }

        @Override
        public Segment build()
        {
            return new IntEncoder(statsBuilder.build(), getType(), isNull, values).encode();
        }
    }
}
