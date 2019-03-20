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
package com.facebook.presto.orc.stream.scan;

import com.facebook.presto.orc.OrcCorruptionException;
import com.facebook.presto.orc.OrcDecompressor;
import com.facebook.presto.orc.metadata.CompressionKind;
import com.facebook.presto.orc.stream.LongInputStream;
import com.facebook.presto.orc.stream.LongOutputStream;
import com.facebook.presto.orc.stream.LongOutputStreamV1;
import io.airlift.slice.Slice;

import java.util.Optional;

import static com.facebook.presto.memory.context.AggregatedMemoryContext.newSimpleAggregatedMemoryContext;
import static com.facebook.presto.orc.OrcDecompressor.createOrcDecompressor;
import static com.facebook.presto.orc.metadata.Stream.StreamKind.DATA;

public class TestBufferBackedLongStreamV1
        extends AbstractTestLongStream
{
    @Override
    protected LongOutputStream createValueOutputStream(CompressionKind kind)
    {
        return new LongOutputStreamV1(kind, COMPRESSION_BLOCK_SIZE, true, DATA);
    }

    @Override
    protected LongInputStream createValueStream(Slice slice, CompressionKind kind)
            throws OrcCorruptionException
    {
        Optional<OrcDecompressor> orcDecompressor = createOrcDecompressor(ORC_DATA_SOURCE_ID, kind, COMPRESSION_BLOCK_SIZE);
        OrcBufferIterator input = new OrcBufferIterator(ORC_DATA_SOURCE_ID, slice.getInput(), orcDecompressor, newSimpleAggregatedMemoryContext(), slice.getRetainedSize());
        return new BufferBackedLongInputStreamV1(input, true);
    }
}
