package com.facebook.presto.plugin.turbonium.storage;

import com.facebook.presto.spi.block.Block;
import com.facebook.presto.spi.block.BlockBuilder;
import com.facebook.presto.spi.type.Type;
import com.google.common.primitives.Booleans;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import java.util.ArrayList;
import java.util.List;

public class LongColumn
    extends AbstractColumn
{
    private final long[] values;

    public LongColumn(Type type, long[] values, boolean[] valueIsNull)
    {
        super(type, valueIsNull);
        this.values = values;
    }

    @Override
    protected void writeNonNull(BlockBuilder blockBuilder, int position)
    {
        blockBuilder.writeLong(values[position]);
    }

    public static class Builder
        extends AbstractColumnBuilder
    {
        private final List<Long> values = new ArrayList<>();
        private final List<Boolean> valueIsNull = new ArrayList<>();

        Builder(int channel, Type type) {
            super(channel, type);
        }

        @Override
        protected void appendValue(Block block, int position) {
            if (block.isNull(position)) {
                valueIsNull.add(true);
                values.add(0L);
            } else {
                valueIsNull.add(false);
                values.add(getType().getLong(block, position));
            }
        }

        @Override
        public Column build() {
            return new LongColumn(getType(), Longs.toArray(values), Booleans.toArray(valueIsNull));
        }
    }
}
