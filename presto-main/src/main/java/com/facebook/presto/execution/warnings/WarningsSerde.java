package com.facebook.presto.execution.warnings;

import com.facebook.presto.execution.buffer.PagesSerde;
import com.facebook.presto.execution.buffer.SerializedPage;
import com.facebook.presto.spi.Page;
import com.facebook.presto.spi.PageBuilder;
import com.facebook.presto.spi.PrestoWarning;
import com.facebook.presto.spi.WarningCode;
import com.facebook.presto.spi.block.BlockBuilder;
import com.facebook.presto.spi.type.Type;
import com.google.common.collect.ImmutableList;

import java.util.List;

import static com.facebook.presto.spi.type.BigintType.BIGINT;
import static com.facebook.presto.spi.type.VarcharType.VARCHAR;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.slice.Slices.utf8Slice;
import static java.util.Objects.requireNonNull;

public class WarningsSerde
{
    private final PagesSerde pagesSerde;

    public WarningsSerde(PagesSerde pagesSerde)
    {
        this.pagesSerde = requireNonNull(pagesSerde, "pagesSerde is null");
    }

    public List<SerializedPage> serialize(List<PrestoWarning> warnings)
    {
        requireNonNull(warnings, "warnings is null");
        PageListBuilder pageListBuilder = new PageListBuilder(ImmutableList.of(BIGINT, VARCHAR, VARCHAR));
        for (PrestoWarning warning : warnings) {
            pageListBuilder.beginRow();
            BIGINT.writeLong(pageListBuilder.nextBlockBuilder(), warning.getWarningCode().getCode());
            VARCHAR.writeSlice(pageListBuilder.nextBlockBuilder(), utf8Slice(warning.getWarningCode().getName()));
            VARCHAR.writeSlice(pageListBuilder.nextBlockBuilder(), utf8Slice(warning.getMessage()));
        }

        return pageListBuilder.build().stream()
                .map(pagesSerde::serialize)
                .collect(toImmutableList());
    }

    public List<PrestoWarning> deserialize(List<SerializedPage> pages)
    {
        requireNonNull(pages, "pages is null");
        ImmutableList.Builder<PrestoWarning> builder = ImmutableList.builder();
        for (SerializedPage serializedPage : pages) {
            Page page = pagesSerde.deserialize(serializedPage);
            for (int position = 0; position < page.getPositionCount(); position++) {
                WarningCode warningCode = new WarningCode(Math.toIntExact(page.getBlock(0).getLong(position, 0)),
                        page.getBlock(1).getSlice(position, 0, page.getBlock(1).getSliceLength(position)).toStringUtf8());
                String message = page.getBlock(2).getSlice(position, 0, page.getBlock(2).getSliceLength(position)).toStringUtf8();
                builder.add(new PrestoWarning(warningCode, message));
            }
        }
        return builder.build();
    }

    private static class PageListBuilder
    {
        // Copied from com.facebook.presto.raptor.systemtables.PageListBuilder
        private final PageBuilder pageBuilder;
        private final ImmutableList.Builder<Page> pages = ImmutableList.builder();
        private int channel;

        public PageListBuilder(List<Type> types)
        {
            this.pageBuilder = new PageBuilder(types);
        }

        public List<Page> build()
        {
            if (!pageBuilder.isEmpty()) {
                pages.add(pageBuilder.build());
                pageBuilder.reset();
            }
            return pages.build();
        }

        public void beginRow()
        {
            if (pageBuilder.isFull()) {
                pages.add(pageBuilder.build());
                pageBuilder.reset();
            }
            pageBuilder.declarePosition();
            channel = 0;
        }

        public BlockBuilder nextBlockBuilder()
        {
            int currentChannel = channel;
            channel++;
            return pageBuilder.getBlockBuilder(currentChannel);
        }
    }
}
