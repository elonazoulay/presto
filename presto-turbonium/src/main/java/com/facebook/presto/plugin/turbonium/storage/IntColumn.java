package com.facebook.presto.plugin.turbonium.storage;

import com.facebook.presto.spi.block.Block;
import com.facebook.presto.spi.block.BlockBuilder;
import com.facebook.presto.spi.type.Type;
import com.google.common.primitives.Booleans;
import com.google.common.primitives.Ints;

import java.util.ArrayList;
import java.util.List;

import static com.facebook.presto.spi.type.BooleanType.BOOLEAN;

public class IntColumn
    extends AbstractColumn
{
    private final int[] values;

    public IntColumn(Type type, int[] values, boolean[] valueIsNull)
    {
        super(type, valueIsNull);
        this.values = values;
    }

    @Override
    protected void writeNonNull(BlockBuilder blockBuilder, int position)
    {
        blockBuilder.writeInt(values[position]);
    }

    public static class Builder
        extends AbstractColumnBuilder
    {
        private final List<Integer> values = new ArrayList<>();
        private final List<Boolean> valueIsNull = new ArrayList<>();

        Builder(int channel, Type type) {
            super(channel, type);
        }

        @Override
        protected void appendValue(Block block, int position) {
            if (block.isNull(position)) {
                valueIsNull.add(true);
                values.add(0);
            } else {
                valueIsNull.add(false);
                values.add(Math.toIntExact(getType().getLong(block, position)));
            }
        }

        @Override
        public Column build() {
            return new IntColumn(getType(), Ints.toArray(values), Booleans.toArray(valueIsNull));
        }
    }
}
