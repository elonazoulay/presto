package com.facebook.presto.plugin.turbonium.storage;

import com.facebook.presto.spi.block.Block;
import com.facebook.presto.spi.block.BlockBuilder;
import com.facebook.presto.spi.type.Type;
import com.google.common.primitives.Booleans;
import com.google.common.primitives.Doubles;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Double.doubleToLongBits;

public class DoubleColumn
    extends AbstractColumn
{
    private final double[] values;

    public DoubleColumn(Type type, double[] values, boolean[] valueIsNull)
    {
        super(type, valueIsNull);
        this.values = values;
    }

    @Override
    protected void writeNonNull(BlockBuilder blockBuilder, int position)
    {
        blockBuilder.writeLong(doubleToLongBits(values[position]));
    }

    public static class Builder
        extends AbstractColumnBuilder
    {
        private final List<Double> values = new ArrayList<>();
        private final List<Boolean> valueIsNull = new ArrayList<>();

        Builder(int channel, Type type) {
            super(channel, type);
        }

        @Override
        protected void appendValue(Block block, int position) {
            if (block.isNull(position)) {
                valueIsNull.add(true);
                values.add(0D);
            } else {
                valueIsNull.add(false);
                values.add(getType().getDouble(block, position));
            }
        }

        @Override
        public Column build() {
            return new DoubleColumn(getType(), Doubles.toArray(values), Booleans.toArray(valueIsNull));
        }
    }
}
