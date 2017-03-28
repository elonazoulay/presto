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
import com.facebook.presto.spi.block.Block;
import com.facebook.presto.spi.block.BlockBuilder;
import com.facebook.presto.spi.type.Type;

public abstract class AbstractColumn
    implements Column
{
    private final Type type;
    private final boolean[] valueIsNull;

    AbstractColumn(Type type, boolean[] valueIsNull)
    {
        this.type = type;
        this.valueIsNull = valueIsNull;
    }

    @Override
    public Type getType()
    {
        return type;
    }

    abstract protected void writeNonNull(BlockBuilder blockBuilder, int position);

    @Override
    public void write(BlockBuilder blockBuilder, int position)
    {
        if (valueIsNull[position]) {
            blockBuilder.appendNull();
        }
        else {
            writeNonNull(blockBuilder, position);
        }
    }

    @Override
    public int size()
    {
        return valueIsNull.length;
    }
}
