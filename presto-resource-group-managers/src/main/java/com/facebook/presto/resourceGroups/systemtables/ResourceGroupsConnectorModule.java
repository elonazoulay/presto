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

import com.facebook.presto.spi.SystemTable;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;

import static com.google.inject.multibindings.Multibinder.newSetBinder;

public class ResourceGroupsConnectorModule
    implements Module
{
    //private final QueryQueueCache queryQueueCache;

    public ResourceGroupsConnectorModule(/*QueryQueueCache queryQueueCache*/)
    {
        //this.queryQueueCache = queryQueueCache;
    }
    @Override
    public void configure(Binder binder)
    {
        //binder.bind(QueryQueueCache.class).toInstance(queryQueueCache);
        binder.bind(ResourceGroupsConnector.class).in(Scopes.SINGLETON);
        binder.bind(ResourceGroupsSplitManager.class).in(Scopes.SINGLETON);
        binder.bind(ResourceGroupsMetadata.class).in(Scopes.SINGLETON);
        binder.bind(ResourceGroupsRecordSetProvider.class).in(Scopes.SINGLETON);
        Multibinder<SystemTable> tableBinder = newSetBinder(binder, SystemTable.class);
        tableBinder.addBinding().to(ResourceGroupsInfoSystemTable.class).in(Scopes.SINGLETON);
    }
}
