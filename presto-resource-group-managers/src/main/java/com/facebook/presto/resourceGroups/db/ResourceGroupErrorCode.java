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
package com.facebook.presto.resourceGroups.db;

import com.facebook.presto.spi.ErrorCode;
import com.facebook.presto.spi.ErrorCodeSupplier;
import com.facebook.presto.spi.ErrorType;

import static com.facebook.presto.spi.ErrorType.USER_ERROR;

public enum ResourceGroupErrorCode
    implements ErrorCodeSupplier
{
    GENERIC_RESOURCE_GROUP_ERROR(0, USER_ERROR),
    RESOURCE_GROUP_NOT_FOUND(1, USER_ERROR),
    RESOURCE_GROUP_ALREADY_EXISTS(2, USER_ERROR),
    SCHEDULING_POLICY_MISMATCHES_PARENT(3, USER_ERROR),
    SELECTOR_NOT_FOUND(4, USER_ERROR),
    SELECTOR_ALREADY_EXISTS(5, USER_ERROR),
    INVALID_CPU_QUOTA_PERIOD(6, USER_ERROR);

    private final ErrorCode errorCode;
    ResourceGroupErrorCode(int code, ErrorType type)
    {
        errorCode = new ErrorCode(code + 0x0602_0000, name(), type);
    }

    @Override
    public ErrorCode toErrorCode()
    {
        return errorCode;
    }
}
