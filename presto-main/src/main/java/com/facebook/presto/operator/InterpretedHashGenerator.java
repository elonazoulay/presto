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
package com.facebook.presto.operator;

import com.facebook.presto.operator.scalar.CombineHashFunction;
import com.facebook.presto.spi.Page;
import com.facebook.presto.spi.block.Block;
import com.facebook.presto.spi.block.BlockContents;
import com.facebook.presto.spi.block.LongArrayBlock;
import com.facebook.presto.spi.block.MapHolder;
import com.facebook.presto.spi.type.AbstractLongType;
import com.facebook.presto.spi.type.Type;
import com.facebook.presto.sql.planner.optimizations.HashGenerationOptimizer;
import com.facebook.presto.type.TypeUtils;
import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.function.IntFunction;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

public class InterpretedHashGenerator
        implements HashGenerator
{
    private final List<Type> hashChannelTypes;
    private final int[] hashChannels;
    private long[] hashes;
    private BlockContents contents;
    private MapHolder mapHolder;
    
    public InterpretedHashGenerator(List<Type> hashChannelTypes, List<Integer> hashChannels)
    {
        this(hashChannelTypes, requireNonNull(hashChannels).stream().mapToInt(i -> i).toArray());
    }
    
    public InterpretedHashGenerator(List<Type> hashChannelTypes, int[] hashChannels)
    {
        this.hashChannels = requireNonNull(hashChannels, "hashChannels is null");
        this.hashChannelTypes = ImmutableList.copyOf(requireNonNull(hashChannelTypes, "hashChannelTypes is null"));
        checkArgument(hashChannelTypes.size() == hashChannels.length);
    }

    @Override
    public long hashPosition(int position, Page page)
    {
        return hashPosition(position, page::getBlock);
    }

    public long hashPosition(int position, IntFunction<Block> blockProvider)
    {
        long result = HashGenerationOptimizer.INITIAL_HASH_VALUE;
        for (int i = 0; i < hashChannels.length; i++) {
            Type type = hashChannelTypes.get(i);
            result = CombineHashFunction.getHash(result, TypeUtils.hashPosition(type, blockProvider.apply(hashChannels[i]), position));
        }
        return result;
    }

    @Override
    public void getPartitions(int partitionCount, Page page, int[] partitionsOut)
    {
        int positionCount = page.getPositionCount();
        if (hashes == null || hashes.length < positionCount) {
            hashes = new long[positionCount];
        }
        long result ;
        for (int position = 0; position < positionCount; position++) {
            hashes[position] =         HashGenerationOptimizer.INITIAL_HASH_VALUE;
        }
        for (int i = 0; i < hashChannels.length; i++) {

            Type type = hashChannelTypes.get(i);
            Block block = page.getBlock(i);
            contents.decodeBlock(block, mapHolder);
            Block leafBlock = contents.leafBlock;
            if (leafBlock instanceof LongArrayBlock) {
                long[] longs = contents.longs;
                int[] longsMap = contents.rowNumberMap;
                boolean[] nulls = contents.valueIsNull;
                for (int position = 0; position < positionCount; position++) {
                    int valueIdx = longsMap[position];
                    hashes[position] = 
                        CombineHashFunction.getHash(hashes[position],
                                                    (nulls == null || !nulls[valueIdx])
                                                    ?  AbstractLongType.hash(longs[valueIdx])
                                                    : TypeUtils.NULL_HASH_CODE);
                }
            }
            else {
                for (int position = 0; position < positionCount; position++) {
                    hashes[position] = CombineHashFunction.getHash(hashes[position], TypeUtils.hashPosition(type, block, position));
                }
            }
        }   
    }
    
    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("hashChannelTypes", hashChannelTypes)
                .add("hashChannels", hashChannels)
                .toString();
    }
}
