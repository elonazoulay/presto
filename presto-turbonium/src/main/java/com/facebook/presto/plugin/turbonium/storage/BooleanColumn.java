package com.facebook.presto.plugin.turbonium.storage;

import com.facebook.presto.spi.block.Block;
import com.facebook.presto.spi.block.BlockBuilder;
import com.facebook.presto.spi.type.Type;
import com.google.common.primitives.Booleans;

import java.util.ArrayList;
import java.util.List;

import static com.facebook.presto.spi.type.BooleanType.BOOLEAN;

public class BooleanColumn
    extends AbstractColumn
{
    private final boolean[] values;

    public BooleanColumn(Type type, boolean[] values, boolean[] valueIsNull)
    {
        super(type, valueIsNull);
        this.values = values;
    }

    @Override
    protected void writeNonNull(BlockBuilder blockBuilder, int position)
    {
        BOOLEAN.writeBoolean(blockBuilder, values[position]);
    }

    public static class Builder
        extends AbstractColumnBuilder
    {
        private final List<Boolean> values = new ArrayList<>();
        private final List<Boolean> valueIsNull = new ArrayList<>();

        Builder(int channel, Type type) {
            super(channel, type);
        }

        @Override
        protected void appendValue(Block block, int position) {
            if (block.isNull(position)) {
                valueIsNull.add(true);
                values.add(false);
            } else {
                valueIsNull.add(false);
                values.add(getType().getBoolean(block, position));
            }
        }

        @Override
        public Column build() {
            return new BooleanColumn(getType(), Booleans.toArray(values), Booleans.toArray(valueIsNull));
        }
    }
}
