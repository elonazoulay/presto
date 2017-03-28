package com.facebook.presto.plugin.turbonium.storage;

import com.facebook.presto.spi.block.Block;
import com.facebook.presto.spi.block.BlockBuilder;
import com.facebook.presto.spi.type.Type;
import com.google.common.primitives.Booleans;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Shorts;

import java.util.ArrayList;
import java.util.List;

public class ShortColumn
    extends AbstractColumn
{
    private final short[] values;

    public ShortColumn(Type type, short[] values, boolean[] valueIsNull)
    {
        super(type, valueIsNull);
        this.values = values;
    }

    @Override
    protected void writeNonNull(BlockBuilder blockBuilder, int position)
    {
        blockBuilder.writeShort(values[position]);
    }

    public static class Builder
        extends AbstractColumnBuilder
    {
        private final List<Short> values = new ArrayList<>();
        private final List<Boolean> valueIsNull = new ArrayList<>();

        Builder(int channel, Type type) {
            super(channel, type);
        }

        @Override
        protected void appendValue(Block block, int position) {
            if (block.isNull(position)) {
                valueIsNull.add(true);
                values.add((short) 0);
            } else {
                valueIsNull.add(false);
                values.add(block.getShort(position, 0));
            }
        }

        @Override
        public Column build() {
            return new ShortColumn(getType(), Shorts.toArray(values), Booleans.toArray(valueIsNull));
        }
    }
}
