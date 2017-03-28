package com.facebook.presto.plugin.turbonium.storage;

import com.facebook.presto.spi.Page;
import com.facebook.presto.spi.block.BlockBuilder;
import com.facebook.presto.spi.type.Type;
import com.google.common.collect.ImmutableList;
import io.airlift.log.Logger;

import java.util.List;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

public class Table {
    private static final Logger log = Logger.get(Table.class);
    private final List<Type> types;
    private final List<Column> columns;
    private Table(List<Type> types, List<Column> columns)
    {
        this.types = requireNonNull(types, "types is null");
        this.columns = requireNonNull(columns, "columns is null");
    }

    public List<Page> getPages(int partNumber, int totalParts, List<Integer> columnIndexes)
    {
        ImmutableList.Builder<Type> typesBuilder = ImmutableList.builder();
        for (int index = 0; index < columnIndexes.size(); index++) {
            typesBuilder.add(types.get(index));
        }
        List<Type> columnTypes = typesBuilder.build();
        PageListBuilder builder = new PageListBuilder(columnTypes);
        int positions = columns.get(0).size();
        for (int position = partNumber; position < positions; position += totalParts) {
            builder.beginRow();
            for (int channel = 0; channel < columnIndexes.size(); channel++) {
                int columnIndex = columnIndexes.get(channel);
                BlockBuilder blockBuilder = builder.nextBlockBuilder();
                Column column = columns.get(columnIndex);
                column.write(blockBuilder, position);
            }
        }
        return builder.build();
    }
    public static Builder builder(List<Type> types)
    {
        return new Builder(types);
    }
    public static class Builder
    {
        private final List<Type> types;
        private final List<ColumnBuilder> columnBuilders;

        private Builder(List<Type> types)
        {
            this.types = ImmutableList.copyOf(types);
            ImmutableList.Builder<ColumnBuilder> builder = ImmutableList.builder();
            for (int channel = 0; channel < types.size(); channel++) {
                builder.add(ColumnBuilder.create(channel, types.get(channel)));
            }
            this.columnBuilders = builder.build();
        }

        public void appendPage(Page page)
        {
            for (int channel = 0; channel < types.size(); channel++) {
                columnBuilders.get(channel).appendFromPage(page);
            }
        }

        public Table build()
        {
            return new Table(types, columnBuilders.stream().map(ColumnBuilder::build).collect(Collectors.toList()));
        }
    }
}
