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

package com.facebook.presto.plugin.turbonium;

import com.facebook.presto.spi.ColumnHandle;
import com.facebook.presto.spi.ConnectorSession;
import com.facebook.presto.spi.ConnectorSplit;
import com.facebook.presto.spi.ConnectorSplitSource;
import com.facebook.presto.spi.ConnectorTableLayoutHandle;
import com.facebook.presto.spi.FixedSplitSource;
import com.facebook.presto.spi.HostAddress;
import com.facebook.presto.spi.connector.ConnectorSplitManager;
import com.facebook.presto.spi.connector.ConnectorTransactionHandle;
import com.facebook.presto.spi.predicate.TupleDomain;
import com.google.common.collect.ImmutableList;

import java.util.List;

import static java.lang.Math.toIntExact;

public final class TurboniumSplitManager
        implements ConnectorSplitManager
{
    @Override
    public ConnectorSplitSource getSplits(ConnectorTransactionHandle transactionHandle, ConnectorSession session, ConnectorTableLayoutHandle layoutHandle)
    {
        TurboniumTableLayoutHandle layout = (TurboniumTableLayoutHandle) layoutHandle;
        List<HostAddress> hosts = layout.getTable().getHosts();
        ImmutableList.Builder<ConnectorSplit> splits = ImmutableList.builder();
        int splitsPerNode = toIntExact(layout.getTable().getSplitsPerWorker());
        for (int bucket = 0; bucket < hosts.size(); bucket++) {
            HostAddress host = hosts.get(bucket);
            for (int i = 0; i < splitsPerNode; i++) {
                splits.add(
                        new TurboniumSplit(
                                layout.getTable(),
                                i,
                                splitsPerNode,
                                toTurboniumColumnHandle(layout.getConstraint()),
                                ImmutableList.of(host),
                                bucket));
            }
        }
        return new FixedSplitSource(splits.build());
    }

    private static TupleDomain<TurboniumColumnHandle> toTurboniumColumnHandle(TupleDomain<ColumnHandle> tupleDomain)
    {
        return tupleDomain.transform(handle -> (TurboniumColumnHandle) handle);
    }
}
