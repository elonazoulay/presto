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
package com.facebook.presto.orc.stream;

import com.facebook.presto.orc.checkpoint.LongStreamCheckpoint;
import com.facebook.presto.orc.checkpoint.LongStreamDwrfCheckpoint;
import com.facebook.presto.orc.metadata.OrcType.OrcTypeKind;

import java.io.IOException;
import java.util.Optional;

import static java.lang.Math.toIntExact;

public class OptimizedLongInputStreamDwrf
        implements LongInputStream
{
    private final BufferConsumer bufferConsumer;

    // Position of the first value of the run in literals from the checkpoint.
    private int currentRunOffset;

    private final OrcInputStream input;
    private final OrcTypeKind orcTypeKind;
    private final boolean signed;
    private final boolean usesVInt;

    public OptimizedLongInputStreamDwrf(OrcInputStream input, OrcTypeKind type, boolean signed, boolean usesVInt)
    {
        this.bufferConsumer = new BufferConsumer(input, signed, Optional.of(type));
        this.input = input;
        this.orcTypeKind = type;
        this.signed = signed;
        this.usesVInt = usesVInt;
    }

    @Override
    public Class<LongStreamDwrfCheckpoint> getCheckpointType()
    {
        return LongStreamDwrfCheckpoint.class;
    }

    @Override
    public void seekToCheckpoint(LongStreamCheckpoint checkpoint)
            throws IOException
    {
        LongStreamDwrfCheckpoint dwrfCheckpoint = (LongStreamDwrfCheckpoint) checkpoint;
        bufferConsumer.seekToCheckpoint(dwrfCheckpoint.getInputStreamCheckpoint());
        currentRunOffset = 0;
    }

    @Override
    public void skip(long items)
            throws IOException
    {
        if (usesVInt) {
            bufferConsumer.skipVarints(items);
        }
        else {
            bufferConsumer.skipFixedWidthType(items);
        }
        currentRunOffset += toIntExact(items);
    }

    @Override
    public long next()
            throws IOException
    {
        currentRunOffset++;
        if (bufferConsumer.available() == 0) {
            bufferConsumer.refresh();
        }
        if (usesVInt) {
            return bufferConsumer.decodeVarint();
        }
        else {
            return bufferConsumer.readFixedWidthType();
        }
    }
}