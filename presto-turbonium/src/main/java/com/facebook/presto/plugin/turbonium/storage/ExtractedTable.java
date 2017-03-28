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
import com.facebook.presto.spi.block.BlockBuilder;
import com.facebook.presto.spi.type.FixedWidthType;
import com.facebook.presto.spi.type.Type;
import com.google.common.collect.ImmutableList;
import io.airlift.log.Logger;
import io.airlift.slice.Slice;

import java.util.List;
import java.util.stream.Collectors;

import static com.facebook.presto.spi.type.BooleanType.BOOLEAN;
import static com.facebook.presto.spi.type.DoubleType.DOUBLE;
import static com.facebook.presto.spi.type.VarcharType.VARCHAR;
import static java.util.Objects.requireNonNull;

public class ExtractedTable {
    private static final Logger log = Logger.get(ExtractedTable.class);
    private final List<Type> types;
    private final List<ExtractedColumn> columns;

    private ExtractedTable(List<Type> types, List<ExtractedColumn> columns)
    {
        this.types = requireNonNull(types, "types is null");
        this.columns = requireNonNull(columns, "columns is null");
    }

    public List<Page> getPages(int partNumber, int totalParts, List<Integer> columnIndexes)
    {
        ImmutableList.Builder<Type> typesBuilder = ImmutableList.builder();
        for (int index = 0; index < columnIndexes.size(); index++) {
            // log.info("The type is %s %s", types.get(index).getJavaType(), types.get(index).getTypeSignature());
            if (types.get(index) instanceof FixedWidthType) {
                log.info("Fixed width type %s is %s", types.get(index), ((FixedWidthType) types.get(index)).getFixedSize());
            }
            else {
                log.info("Variable width type %s", types.get(index));
            }
            typesBuilder.add(types.get(index));
        }
        List<Type> columnTypes = typesBuilder.build();
        PageListBuilder builder = new PageListBuilder(columnTypes);
        int positions = columns.get(0).getPositionCount();
        for (int position = partNumber; position < positions; position += totalParts) {
            builder.beginRow();
            for (int channel = 0; channel < columnIndexes.size(); channel++) {
                Type type = types.get(channel);
                int columnIndex = columnIndexes.get(channel);
                BlockBuilder blockBuilder = builder.nextBlockBuilder();
                if (position < 20) {
                    log.info("INFO for channel %s: Blockbuilder %s type %s", channel, blockBuilder, type);
                }
                Object value = columns.get(columnIndex).getValue(position);
                type.getTypeSignature();
                if (value == null) {
                    blockBuilder.appendNull();
                    continue;
                }
                if (type.getJavaType() == boolean.class) {
                    boolean boolValue = (Boolean) value;
                    BOOLEAN.writeBoolean(blockBuilder, boolValue);
                }
                else if (type.getJavaType() == long.class) {
                    long longValue = (Long) value;
                    if (type.getTypeSignature().toString().equals("integer")) {
                        blockBuilder.writeInt(Math.toIntExact(longValue));
                    }
                    else if (type.getTypeSignature().toString().equals("date")) {
                        blockBuilder.writeInt(Math.toIntExact(longValue));
                    }
                    else {
                        blockBuilder.writeLong(longValue);
                    }
                }
                else if (type.getJavaType() == double.class) {
                    double doubleValue = (Double) value;
                    DOUBLE.writeDouble(blockBuilder, doubleValue);
                }
                else if (type.getJavaType() == Slice.class) {
                    Slice sliceValue = (Slice) value;
                    VARCHAR.writeSlice(blockBuilder, sliceValue);
                }
            }
            /*
            for (int columnIndex : columnIndexes) {
                builder.nextBlockBuilder().writeObject(columns.get(columnIndex).getValue(position));
            }
            */
        }
        return builder.build();
    }

    public static Builder builder(List<Type> types)
    {
        return new Builder(types);
    }
    public static class Builder
    {
        private List<Type> types;
        private List<ExtractedColumn.Builder> columns;

        public Builder(List<Type> types)
        {
            this.types = requireNonNull(ImmutableList.copyOf(types), "types is null");
            ImmutableList.Builder<ExtractedColumn.Builder> builder = ImmutableList.builder();
            for (int channel = 0; channel < types.size(); channel++) {
                builder.add(ExtractedColumn.builder(channel, types.get(channel)));
            }
            this.columns = builder.build();
        }

        public void appendPage(Page page)
        {
            for (int channel = 0; channel < types.size(); channel++) {
                ExtractedColumn.Builder builder = columns.get(channel);
                builder.extractColumnFromPage(page);
            }
        }

        public ExtractedTable build()
        {
            return new ExtractedTable(types, columns.stream().map(ExtractedColumn.Builder::build).collect(Collectors.toList()));
        }
    }
}
