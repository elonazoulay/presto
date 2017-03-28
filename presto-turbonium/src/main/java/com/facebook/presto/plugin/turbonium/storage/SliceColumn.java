package com.facebook.presto.plugin.turbonium.storage;

import com.facebook.presto.spi.block.Block;
import com.facebook.presto.spi.block.BlockBuilder;
import com.facebook.presto.spi.type.Type;
import com.google.common.primitives.Booleans;
import com.google.common.primitives.Shorts;
import io.airlift.slice.Slice;
import io.airlift.slice.Slices;

import java.util.ArrayList;
import java.util.List;

public class SliceColumn
    extends AbstractColumn
{
    private final Slice[] values;

    public SliceColumn(Type type, Slice[] values, boolean[] valueIsNull)
    {
        super(type, valueIsNull);
        this.values = values;
    }

    @Override
    protected void writeNonNull(BlockBuilder blockBuilder, int position)
    {
        getType().writeSlice(blockBuilder, values[position]);
    }

    public static class Builder
        extends AbstractColumnBuilder
    {
        private final List<Slice> values = new ArrayList<>();
        private final List<Boolean> valueIsNull = new ArrayList<>();

        Builder(int channel, Type type) {
            super(channel, type);
        }

        @Override
        protected void appendValue(Block block, int position) {
            if (block.isNull(position)) {
                valueIsNull.add(true);
                values.add(Slices.EMPTY_SLICE);
            } else {
                valueIsNull.add(false);
                values.add(getType().getSlice(block,position));
            }
        }

        @Override
        public Column build() {
            return new SliceColumn(getType(), values.toArray(new Slice[0]), Booleans.toArray(valueIsNull));
        }
    }
}
