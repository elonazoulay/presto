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

import com.facebook.presto.spi.Page;
import com.facebook.presto.spi.type.Type;
import com.facebook.presto.spi.block.Block;
import com.google.common.collect.ImmutableList;
import io.airlift.slice.Slice;

import java.util.List;

import static io.airlift.slice.SizeOf.SIZE_OF_BYTE;
import static io.airlift.slice.SizeOf.SIZE_OF_DOUBLE;
import static io.airlift.slice.SizeOf.SIZE_OF_LONG;
import static java.util.Objects.requireNonNull;

public class ExtractedColumn {
    private final int channel;
    private final Type type;
    private final List<Object> values;
    private final List<Integer> sizes;

    private ExtractedColumn(
            int channel,
            Type type,
            List<Object> values,
            List<Integer> sizes)
    {
        this.channel = channel;
        this.type = requireNonNull(type, "type is null");
        this.values = requireNonNull(values, "values is null");
        this.sizes = requireNonNull(sizes, "sizes is null");
    }

    public int getPositionCount()
    {
        return values.size();
    }

    public Object getValue(int position)
    {
        return values.get(position);
    }

    public static Builder builder(int channel, Type type)
    {
        return new Builder(channel, type);
    }

    public static class Builder
    {
        private final int channel;
        private final Type type;
        private final ImmutableList.Builder<Object> valuesBuilder;
        private final ImmutableList.Builder<Integer> offsetsBuilder;

        private Builder(int channel, Type type)
        {
            this.channel = channel;
            this.type = type;
            this.valuesBuilder = ImmutableList.builder();
            this.offsetsBuilder = ImmutableList.builder();
        }

        public ExtractedColumn build()
        {
            return new ExtractedColumn(channel, type, valuesBuilder.build(), offsetsBuilder.build());
        }

        public void extractColumnFromPage(Page page)
        {
            Block block = page.getBlock(channel);
            for (int position = 0; position < page.getPositionCount(); position++) {
                Object value = getNativeContainerValue(block, position);
                valuesBuilder.add(value);
                offsetsBuilder.add(getSize(value));
            }
        }

        private Object getNativeContainerValue(Block block, int position)
        {
            // COPIED FROM RAPTOR: com.facebook.presto.raptor.storage
            if (block.isNull(position)) {
                return null;
            }
            else if (type.getJavaType() == boolean.class) {
                return type.getBoolean(block, position);
            }
            else if (type.getJavaType() == long.class) {
                return type.getLong(block, position);
            }
            else if (type.getJavaType() == double.class) {
                return type.getDouble(block, position);
            }
            else if (type.getJavaType() == Slice.class) {
                return type.getSlice(block, position);
            }
            else if (type.getJavaType() == Block.class) {
                return type.getObject(block, position);
            }
            else {
                throw new AssertionError("Unimplemented type: " + type);
            }
        }

        private int getSize(Object value)
        {
            // COPIED FROM RAPTOR Row.java
            int size;
            if (value == null) {
                size = SIZE_OF_BYTE;
            }
            else if (type.getJavaType() == boolean.class) {
                size = SIZE_OF_BYTE;
            }
            else if (type.getJavaType() == long.class) {
                size = SIZE_OF_LONG;
            }
            else if (type.getJavaType() == double.class) {
                size = SIZE_OF_DOUBLE;
            }
            else if (type.getJavaType() == Slice.class) {
                size = ((Slice) value).length();
            }
            else if (type.getJavaType() == Block.class) {
                size = ((Block) value).getSizeInBytes();
            }
            else {
                throw new AssertionError("Unimplemented type: " + type);
            }
            return size;
        }
    }
}
