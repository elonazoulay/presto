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
package com.facebook.presto.plugin.memory;

import com.facebook.presto.plugin.memory.config.MemoryConfigManager;
import com.facebook.presto.plugin.memory.config.UpdateMaxDataPerNodeProcedure;
import com.facebook.presto.plugin.memory.config.UpdateMaxTableSizePerNodeProcedure;
import com.facebook.presto.plugin.memory.config.UpdateSplitsPerNodeProcedure;
import com.facebook.presto.plugin.memory.systemtables.MemoryConfigSystemTable;
import com.facebook.presto.plugin.memory.systemtables.MemoryInfoSystemTable;
import com.facebook.presto.spi.NodeManager;
import com.facebook.presto.spi.SystemTable;
import com.facebook.presto.spi.procedure.Procedure;
import com.facebook.presto.spi.type.Type;
import com.facebook.presto.spi.type.TypeManager;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.FromStringDeserializer;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.MultibindingsScanner;
import com.google.inject.multibindings.ProvidesIntoSet;

import javax.inject.Inject;

import static com.facebook.presto.spi.type.TypeSignature.parseTypeSignature;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static io.airlift.configuration.ConfigBinder.configBinder;
import static java.util.Objects.requireNonNull;

public class MemoryModule
        implements Module
{
    private final String connectorId;
    private final TypeManager typeManager;
    private final NodeManager nodeManager;

    public MemoryModule(String connectorId, TypeManager typeManager, NodeManager nodeManager)
    {
        this.connectorId = requireNonNull(connectorId, "connector id is null");
        this.typeManager = requireNonNull(typeManager, "typeManager is null");
        this.nodeManager = requireNonNull(nodeManager, "nodeManager is null");
    }

    @Override
    public void configure(Binder binder)
    {
        binder.install(MultibindingsScanner.asModule());
        binder.bind(TypeManager.class).toInstance(typeManager);
        binder.bind(NodeManager.class).toInstance(nodeManager);
        binder.bind(MemoryConnector.class).in(Scopes.SINGLETON);
        binder.bind(MemoryConnectorId.class).toInstance(new MemoryConnectorId(connectorId));
        binder.bind(MemoryMetadata.class).in(Scopes.SINGLETON);
        binder.bind(MemorySplitManager.class).in(Scopes.SINGLETON);
        binder.bind(MemoryPagesStore.class).in(Scopes.SINGLETON);
        binder.bind(MemoryPageSourceProvider.class).in(Scopes.SINGLETON);
        binder.bind(MemoryPageSinkProvider.class).in(Scopes.SINGLETON);
        configBinder(binder).bindConfig(MemoryConfig.class);
        binder.bind(MemoryConfigManager.class).in(Scopes.SINGLETON);
        Multibinder<SystemTable> tableBinder = newSetBinder(binder, SystemTable.class);
        tableBinder.addBinding().to(MemoryInfoSystemTable.class).in(Scopes.SINGLETON);
        tableBinder.addBinding().to(MemoryConfigSystemTable.class).in(Scopes.SINGLETON);
        Multibinder.newSetBinder(binder, Procedure.class);
        binder.bind(UpdateMaxDataPerNodeProcedure.class).in(Scopes.SINGLETON);
        binder.bind(UpdateMaxTableSizePerNodeProcedure.class).in(Scopes.SINGLETON);
        binder.bind(UpdateSplitsPerNodeProcedure.class).in(Scopes.SINGLETON);
    }

    @ProvidesIntoSet
    public static Procedure getUpdateMaxDataPerNodeProcedure(UpdateMaxDataPerNodeProcedure procedure)
    {
        return procedure.getProcedure();
    }

    @ProvidesIntoSet
    public static Procedure getUpdateMaxTableSizePerNodeProcedure(UpdateMaxTableSizePerNodeProcedure procedure)
    {
        return procedure.getProcedure();
    }

    @ProvidesIntoSet
    public static Procedure getUpdateSplitsPerNodeProcedure(UpdateSplitsPerNodeProcedure procedure)
    {
        return procedure.getProcedure();
    }

    public static final class TypeDeserializer
            extends FromStringDeserializer<Type>
    {
        private final TypeManager typeManager;

        @Inject
        public TypeDeserializer(TypeManager typeManager)
        {
            super(Type.class);
            this.typeManager = requireNonNull(typeManager, "typeManager is null");
        }

        @Override
        protected Type _deserialize(String value, DeserializationContext context)
        {
            Type type = typeManager.getType(parseTypeSignature(value));
            checkArgument(type != null, "Unknown type %s", value);
            return type;
        }
    }
}
