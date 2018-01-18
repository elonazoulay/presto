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
package com.facebook.presto.connector.system;

import com.facebook.presto.connector.ConnectorManager;
import com.facebook.presto.spi.SystemTable;
import com.facebook.presto.spi.procedure.Procedure;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

import java.util.Set;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

@ThreadSafe
public class SystemConnectorRegistrar
{
    private final ConnectorManager manager;
    private final Set<Procedure> procedures;
    @GuardedBy("this")
    private final ImmutableSet.Builder<SystemTable> tablesBuilder = ImmutableSet.builder();
    @GuardedBy("this")
    private GlobalSystemConnectorFactory globalSystemConnectorFactory;

    @Inject
    public SystemConnectorRegistrar(ConnectorManager manager, Set<SystemTable> tables, Set<Procedure> procedures)
    {
        this.manager = requireNonNull(manager, "manager is null");
        tablesBuilder.addAll(requireNonNull(tables, "tables is null"));
        this.procedures = ImmutableSet.copyOf(requireNonNull(procedures, "procedures is null"));
    }

    public synchronized void registerSystemTables(Set<SystemTable> tables)
    {
        checkIfCreated();
        tablesBuilder.addAll(requireNonNull(tables, "tables is null"));
    }

    public void loadGlobalSystemConnector()
    {
        synchronized (this) {
            checkIfCreated();
            globalSystemConnectorFactory = new GlobalSystemConnectorFactory(tablesBuilder.build(), procedures);
        }
        manager.addConnectorFactory(globalSystemConnectorFactory);
        manager.createConnection(GlobalSystemConnector.NAME, GlobalSystemConnector.NAME, ImmutableMap.of());
    }

    private synchronized void checkIfCreated()
    {
        checkState(globalSystemConnectorFactory == null, "global system connector was already added");
    }
}
