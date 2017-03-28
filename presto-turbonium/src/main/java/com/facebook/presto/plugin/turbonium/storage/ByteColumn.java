package com.facebook.presto.plugin.turbonium.storage;

import com.facebook.presto.spi.block.Block;
import com.facebook.presto.spi.block.BlockBuilder;
import com.facebook.presto.spi.type.Type;
import com.google.common.primitives.Booleans;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;

import java.util.ArrayList;
import java.util.List;

public class ByteColumn
    extends AbstractColumn
{
    private final byte[] values;

    public ByteColumn(Type type, byte[] values, boolean[] valueIsNull)
    {
        super(type, valueIsNull);
        this.values = values;
    }

    @Override
    protected void writeNonNull(BlockBuilder blockBuilder, int position)
    {
        blockBuilder.writeByte(values[position]);
    }

    public static class Builder
        extends AbstractColumnBuilder
    {
        private final List<Byte> values = new ArrayList<>();
        private final List<Boolean> valueIsNull = new ArrayList<>();

        Builder(int channel, Type type) {
            super(channel, type);
        }

        @Override
        protected void appendValue(Block block, int position) {
            if (block.isNull(position)) {
                valueIsNull.add(true);
                values.add((byte) 0);
            } else {
                valueIsNull.add(false);
                values.add(block.getByte(position, 0));
            }
        }

        @Override
        public Column build() {
            return new ByteColumn(getType(), Bytes.toArray(values), Booleans.toArray(valueIsNull));
        }
    }
}
