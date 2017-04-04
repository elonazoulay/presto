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

import org.openjdk.jol.info.ClassLayout;

import java.util.BitSet;

public class Util
{
    private Util() {}

    private static final int BITSET_SIZE = ClassLayout.parseClass(BitSet.class).instanceSize();
    private static final int ADDRESS_BITS = 3;

    private static long longValuesBytes(long bits)
    {
        return bits >> ADDRESS_BITS;
    }

    public static long sizeOfBitSet(BitSet bitSet)
    {
        return BITSET_SIZE + longValuesBytes(bitSet.size());
    }
}
