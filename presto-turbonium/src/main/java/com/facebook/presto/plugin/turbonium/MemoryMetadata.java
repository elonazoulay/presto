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

import com.facebook.presto.plugin.turbonium.config.MemoryConfigManager;
import com.facebook.presto.plugin.turbonium.config.db.MemoryConfigSpec;
import com.facebook.presto.spi.ColumnHandle;
import com.facebook.presto.spi.ColumnMetadata;
import com.facebook.presto.spi.ConnectorInsertTableHandle;
import com.facebook.presto.spi.ConnectorNewTableLayout;
import com.facebook.presto.spi.ConnectorOutputTableHandle;
import com.facebook.presto.spi.ConnectorSession;
import com.facebook.presto.spi.ConnectorTableHandle;
import com.facebook.presto.spi.ConnectorTableLayout;
import com.facebook.presto.spi.ConnectorTableLayoutHandle;
import com.facebook.presto.spi.ConnectorTableLayoutResult;
import com.facebook.presto.spi.ConnectorTableMetadata;
import com.facebook.presto.spi.Constraint;
import com.facebook.presto.spi.HostAddress;
import com.facebook.presto.spi.Node;
import com.facebook.presto.spi.NodeManager;
import com.facebook.presto.spi.PrestoException;
import com.facebook.presto.spi.SchemaTableName;
import com.facebook.presto.spi.SchemaTablePrefix;
import com.facebook.presto.spi.connector.ConnectorMetadata;
import com.facebook.presto.spi.connector.ConnectorOutputMetadata;
import com.facebook.presto.spi.predicate.TupleDomain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.airlift.log.Logger;
import io.airlift.slice.Slice;

import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static com.facebook.presto.spi.StandardErrorCode.NO_NODES_AVAILABLE;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Maps.uniqueIndex;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

@ThreadSafe
public class MemoryMetadata
        implements ConnectorMetadata
{
    private static final Logger log = Logger.get(MemoryMetadata.class);
    public static final String SCHEMA_NAME = "default";

    private final NodeManager nodeManager;
    private final String connectorId;
    private final AtomicLong nextTableId = new AtomicLong();
    private final Map<String, Long> tableIds = new ConcurrentHashMap<>();
    private final Map<Long, MemoryTableHandle> tables = new ConcurrentHashMap<>();
    private final MemoryConfigManager configManager;

    @Inject
    public MemoryMetadata(NodeManager nodeManager, MemoryConnectorId connectorId, MemoryConfigManager configManager)
    {
        this.nodeManager = requireNonNull(nodeManager, "nodeManager is null");
        this.connectorId = requireNonNull(connectorId, "connectorId is null").toString();
        this.configManager = requireNonNull(configManager, "configManager is null");
    }

    @Override
    public synchronized List<String> listSchemaNames(ConnectorSession session)
    {
        return ImmutableList.of(SCHEMA_NAME);
    }

    @Override
    public synchronized ConnectorTableHandle getTableHandle(ConnectorSession session, SchemaTableName tableName)
    {
        Long tableId = tableIds.get(tableName.getTableName());
        if (tableId == null) {
            return null;
        }
        return tables.get(tableId);
    }

    @Override
    public synchronized ConnectorTableMetadata getTableMetadata(ConnectorSession session, ConnectorTableHandle tableHandle)
    {
        MemoryTableHandle memoryTableHandle = (MemoryTableHandle) tableHandle;
        return memoryTableHandle.toTableMetadata();
    }

    @Override
    public synchronized List<SchemaTableName> listTables(ConnectorSession session, String schemaNameOrNull)
    {
        if (schemaNameOrNull != null && !schemaNameOrNull.equals(SCHEMA_NAME)) {
            return ImmutableList.of();
        }
        return tables.values().stream()
                .map(MemoryTableHandle::toSchemaTableName)
                .collect(toList());
    }

    @Override
    public synchronized Map<String, ColumnHandle> getColumnHandles(ConnectorSession session, ConnectorTableHandle tableHandle)
    {
        MemoryTableHandle memoryTableHandle = (MemoryTableHandle) tableHandle;
        return memoryTableHandle.getColumnHandles().stream()
                .collect(toMap(MemoryColumnHandle::getName, Function.identity()));
    }

    @Override
    public synchronized ColumnMetadata getColumnMetadata(ConnectorSession session, ConnectorTableHandle tableHandle, ColumnHandle columnHandle)
    {
        MemoryColumnHandle memoryColumnHandle = (MemoryColumnHandle) columnHandle;
        return memoryColumnHandle.toColumnMetadata();
    }

    @Override
    public synchronized Map<SchemaTableName, List<ColumnMetadata>> listTableColumns(ConnectorSession session, SchemaTablePrefix prefix)
    {
        return tables.values().stream()
                .filter(table -> prefix.matches(table.toSchemaTableName()))
                .collect(toMap(MemoryTableHandle::toSchemaTableName, handle -> handle.toTableMetadata().getColumns()));
    }

    @Override
    public synchronized void dropTable(ConnectorSession session, ConnectorTableHandle tableHandle)
    {
        MemoryTableHandle handle = (MemoryTableHandle) tableHandle;
        Long tableId = tableIds.remove(handle.getTableName());
        if (tableId != null) {
            tables.remove(tableId);
        }
    }

    @Override
    public synchronized void renameTable(ConnectorSession session, ConnectorTableHandle tableHandle, SchemaTableName newTableName)
    {
        MemoryTableHandle oldTableHandle = (MemoryTableHandle) tableHandle;
        MemoryTableHandle newTableHandle = new MemoryTableHandle(
                oldTableHandle.getConnectorId(),
                oldTableHandle.getSchemaName(),
                newTableName.getTableName(),
                oldTableHandle.getTableId(),
                oldTableHandle.getColumnHandles(),
                oldTableHandle.getHosts(),
                oldTableHandle.getSplitsPerWorker());
        tableIds.remove(oldTableHandle.getTableName());
        tableIds.put(newTableName.getTableName(), oldTableHandle.getTableId());
        tables.remove(oldTableHandle.getTableId());
        tables.put(oldTableHandle.getTableId(), newTableHandle);
    }

    @Override
    public synchronized void createTable(ConnectorSession session, ConnectorTableMetadata tableMetadata)
    {
        ConnectorOutputTableHandle outputTableHandle = beginCreateTable(session, tableMetadata, Optional.empty());
        finishCreateTable(session, outputTableHandle, ImmutableList.of());
    }

    @Override
    public synchronized MemoryOutputTableHandle beginCreateTable(ConnectorSession session, ConnectorTableMetadata tableMetadata, Optional<ConnectorNewTableLayout> layout)
    {
        long nextId = nextTableId.getAndIncrement();
        Set<Node> nodes = nodeManager.getRequiredWorkerNodes();
        checkState(!nodes.isEmpty(), "No Memory nodes available");
        Optional<MemoryPartitioningHandle> partitioningHandle = layout
                .map(ConnectorNewTableLayout::getPartitioning)
                .map(MemoryPartitioningHandle.class::cast);
        checkState(partitioningHandle.isPresent(), "Invalid table layout, no partitioning");
        long splitsPerWorker = partitioningHandle.get().getSplitsPerWorker();
        List<String> nodeIds = partitioningHandle.get().getBucketToNode();
        Map<String, Node> nodesById = uniqueIndex(nodes, Node::getNodeIdentifier);
        ImmutableList.Builder<HostAddress> hosts = ImmutableList.builder();
        for (String nodeId : nodeIds) {
            Node node = nodesById.get(nodeId);
            if (node == null) {
                throw new PrestoException(NO_NODES_AVAILABLE, "Node for bucket is offline: " + nodeId);
            }
            hosts.add(node.getHostAndPort());
        }
        tableIds.put(tableMetadata.getTable().getTableName(), nextId);
        MemoryConfigSpec config = configManager.getStaticConfig();
        MemoryTableHandle table = new MemoryTableHandle(
                connectorId,
                nextId,
                tableMetadata,
                hosts.build(),
                splitsPerWorker);
        tables.put(table.getTableId(), table);
        return new MemoryOutputTableHandle(table, ImmutableSet.copyOf(tableIds.values()), config);
    }

    @Override
    public synchronized Optional<ConnectorOutputMetadata> finishCreateTable(ConnectorSession session, ConnectorOutputTableHandle tableHandle, Collection<Slice> fragments)
    {
        return Optional.empty();
    }

    @Override
    public synchronized MemoryInsertTableHandle beginInsert(ConnectorSession session, ConnectorTableHandle tableHandle)
    {
        MemoryTableHandle memoryTableHandle = (MemoryTableHandle) tableHandle;
        // Preserve splitsPerWorker that table was created with
        MemoryConfigSpec spec = configManager.getStaticConfig();
        MemoryConfigSpec newSpec = new MemoryConfigSpec(spec.getMaxDataPerNode(), spec.getMaxTableSizePerNode(), memoryTableHandle.getSplitsPerWorker());
        return new MemoryInsertTableHandle(memoryTableHandle, ImmutableSet.copyOf(tableIds.values()), newSpec);
    }

    @Override
    public synchronized Optional<ConnectorOutputMetadata> finishInsert(ConnectorSession session, ConnectorInsertTableHandle insertHandle, Collection<Slice> fragments)
    {
        return Optional.empty();
    }

    @Override
    public synchronized List<ConnectorTableLayoutResult> getTableLayouts(
            ConnectorSession session,
            ConnectorTableHandle handle,
            Constraint<ColumnHandle> constraint,
            Optional<Set<ColumnHandle>> desiredColumns)
    {
        requireNonNull(handle, "handle is null");
        checkArgument(handle instanceof MemoryTableHandle);
        MemoryTableLayoutHandle layoutHandle = new MemoryTableLayoutHandle((MemoryTableHandle) handle);
        return ImmutableList.of(new ConnectorTableLayoutResult(getTableLayout(session, layoutHandle), constraint.getSummary()));
    }

    @Override
    public synchronized ConnectorTableLayout getTableLayout(ConnectorSession session, ConnectorTableLayoutHandle handle)
    {
        return new ConnectorTableLayout(
                handle,
                Optional.empty(),
                TupleDomain.all(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                ImmutableList.of());
    }

    @Override
    public synchronized Optional<ConnectorNewTableLayout> getNewTableLayout(ConnectorSession session, ConnectorTableMetadata tableMetadata)
    {
        ImmutableList.Builder<String> partitionColumns = ImmutableList.builder();
        for (ColumnMetadata column : tableMetadata.getColumns()) {
            partitionColumns.add(column.getName());
        }
        ImmutableList.Builder<String> bucketToNode = ImmutableList.builder();
        Set<Node> nodes = nodeManager.getRequiredWorkerNodes();
        int splitsPerNode = configManager.getConfig().getSplitsPerNode();
        List<Node> nodeList = new ArrayList<>(nodes);
        Collections.shuffle(nodeList);
        nodeList.stream().forEach(node -> bucketToNode.add(node.getNodeIdentifier()));
        return Optional.of(new ConnectorNewTableLayout(new MemoryPartitioningHandle(bucketToNode.build(), splitsPerNode), partitionColumns.build()));
    }

    public synchronized Map<String, Long> getTableIds()
    {
        return ImmutableMap.copyOf(tableIds);
    }
}
