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
package com.facebook.presto.resourceGroups.systemtables;

import com.facebook.presto.spi.PrestoException;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import static com.facebook.presto.spi.StandardErrorCode.GENERIC_INTERNAL_ERROR;

public class Util
{
    private Util()
    {
    }

    public static MethodHandle methodHandle(Class<?> clazz, String name, Class<?>... parameterTypes)
    {
        // Copied from com.facebook.presto.util.Reflection
        try {
            return MethodHandles.lookup().unreflect(clazz.getMethod(name, parameterTypes));
        }
        catch (IllegalAccessException | NoSuchMethodException e) {
            throw new PrestoException(GENERIC_INTERNAL_ERROR, e);
        }
    }
}
