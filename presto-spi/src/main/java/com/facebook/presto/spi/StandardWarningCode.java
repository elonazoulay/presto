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
package com.facebook.presto.spi;

public enum StandardWarningCode
        implements WarningCodeSupplier
{
    SHOW_PARTITIONS_DEPRECATED(0x0003_0000),
    /**/;

    // Warning codes can start at 0x0003_0000
    // See https://github.com/prestodb/presto/wiki/Error-Codes

    private final WarningCode warningCode;

    StandardWarningCode(int code)
    {
        this.warningCode = new WarningCode(code, name());
    }

    @Override
    public WarningCode toWarningCode()
    {
        return warningCode;
    }
}
