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
package com.facebook.presto.analyzer;

import com.facebook.presto.block.BlockEncodingManager;
import com.facebook.presto.connector.ConnectorId;
import com.facebook.presto.connector.informationSchema.InformationSchemaConnector;
import com.facebook.presto.connector.system.SystemConnector;
import com.facebook.presto.metadata.Catalog;
import com.facebook.presto.metadata.CatalogManager;
import com.facebook.presto.metadata.InMemoryNodeManager;
import com.facebook.presto.metadata.InternalNodeManager;
import com.facebook.presto.metadata.Metadata;
import com.facebook.presto.metadata.MetadataManager;
import com.facebook.presto.metadata.SchemaPropertyManager;
import com.facebook.presto.metadata.SessionPropertyManager;
import com.facebook.presto.metadata.TablePropertyManager;
import com.facebook.presto.security.AccessControl;
import com.facebook.presto.security.AccessControlManager;
import com.facebook.presto.spi.connector.Connector;
import com.facebook.presto.spi.connector.ConnectorMetadata;
import com.facebook.presto.spi.connector.ConnectorSplitManager;
import com.facebook.presto.spi.connector.ConnectorTransactionHandle;
import com.facebook.presto.spi.transaction.IsolationLevel;
import com.facebook.presto.spi.type.TypeManager;
import com.facebook.presto.sql.analyzer.Analyzer;
import com.facebook.presto.sql.analyzer.FeaturesConfig;
import com.facebook.presto.sql.analyzer.SemanticException;
import com.facebook.presto.sql.parser.IdentifierSymbol;
import com.facebook.presto.sql.parser.ParsingOptions;
import com.facebook.presto.sql.parser.SqlParser;
import com.facebook.presto.sql.parser.SqlParserOptions;
import com.facebook.presto.sql.tree.Explain;
import com.facebook.presto.sql.tree.Join;
import com.facebook.presto.sql.tree.JoinUsing;
import com.facebook.presto.sql.tree.Statement;
import com.facebook.presto.sql.util.AstUtils;
import com.facebook.presto.testing.TestingMetadata;
import com.facebook.presto.transaction.TransactionId;
import com.facebook.presto.transaction.TransactionManager;
import com.facebook.presto.type.TypeRegistry;
import io.airlift.log.Logger;
import org.testng.annotations.Test;

import java.util.Optional;

import static com.facebook.presto.SystemSessionProperties.LEGACY_JOIN_USING;
import static com.facebook.presto.connector.ConnectorId.createInformationSchemaConnectorId;
import static com.facebook.presto.connector.ConnectorId.createSystemTablesConnectorId;
import static com.facebook.presto.testing.TestingSession.createBogusTestingCatalog;
import static com.facebook.presto.testing.TestingSession.testSessionBuilder;
import static com.facebook.presto.transaction.TransactionManager.createTestTransactionManager;
import static java.util.Collections.emptyList;

public class TestLegacyJoinUsing
{
    private static Logger log = Logger.get(TestLegacyJoinUsing.class);

    @Test
    public void test()
    {
        TestingRunner runner = new TestingRunner(
                getSemanticAnalyzer(),
                "queries.test",
                descriptor -> System.out.println(descriptor.getQueryId()));
        runner.run();
    }

    private static SemanticAnalyzer getSemanticAnalyzer()
    {
        SqlParserOptions options = new SqlParserOptions();
        options.allowIdentifierSymbol(IdentifierSymbol.COLON, IdentifierSymbol.AT_SIGN);
        SqlParser sqlParser = new SqlParser(options);
        ParsingOptions.DecimalLiteralTreatment parsingOptions = new SemanticAnalyzerConfig().getParsingOptions();

        TypeManager typeManager = new TypeRegistry();
        CatalogManager catalogManager = new CatalogManager();
        TransactionManager transactionManager = createTestTransactionManager(catalogManager);
        AccessControl accessControl = new AccessControlManager(transactionManager);

        TransactionId transactionId = transactionManager.beginTransaction(false);

        Catalog catalog = createBogusTestingCatalog("catalog");
        catalogManager.registerCatalog(catalog);
        Metadata metadata = new MetadataManager(
                new FeaturesConfig(),
                typeManager,
                new BlockEncodingManager(typeManager),
                new SessionPropertyManager(),
                new SchemaPropertyManager(),
                new TablePropertyManager(),
                transactionManager);

        Analyzer analyzer = new Analyzer(
                testSessionBuilder()
                        .setSystemProperty(LEGACY_JOIN_USING, "false")
                        .setTransactionId(transactionId)
                        .build(),
                metadata,
                sqlParser,
                accessControl,
                Optional.empty(),
                emptyList());

        return descriptor -> {
            Statement statement = sqlParser.createStatement(descriptor.getSql(), new ParsingOptions(parsingOptions));
            if (statement instanceof Explain) {
                return false;
            }
            if (!AstUtils.preOrder(statement)
                    .filter(Join.class::isInstance)
                    .map(join -> ((Join) join).getCriteria())
                    .filter(criteria -> criteria.isPresent())
                    .map(Optional::get)
                    .filter(JoinUsing.class::isInstance)
                    .findFirst()
                    .isPresent()) {
                return false;
            }

            try {
                analyzer.analyze(statement);
                return false;
            }
            catch (SemanticException e) {
                log.error("Query %s semantic exception: %s", descriptor.getQueryId(), e);
                return true;
            }
            catch (Throwable e) {
                log.error("Query %s analyze error: %s", descriptor.getQueryId(), e);
                throw e;
            }
        };
    }

    private Catalog createTestingCatalog(String catalogName, ConnectorId connectorId, Metadata metadata, AccessControl accessControl, TransactionManager transactionManager)
    {
        ConnectorId systemId = createSystemTablesConnectorId(connectorId);
        Connector connector = createTestingConnector();
        InternalNodeManager nodeManager = new InMemoryNodeManager();
        return new Catalog(
                catalogName,
                connectorId,
                connector,
                createInformationSchemaConnectorId(connectorId),
                new InformationSchemaConnector(catalogName, nodeManager, metadata, accessControl),
                systemId,
                new SystemConnector(
                        systemId,
                        nodeManager,
                        connector.getSystemTables(),
                        transactionId -> transactionManager.getConnectorTransaction(transactionId, connectorId)));
    }

    private static Connector createTestingConnector()
    {
        return new Connector()
        {
            private final ConnectorMetadata metadata = new TestingMetadata();

            @Override
            public ConnectorTransactionHandle beginTransaction(IsolationLevel isolationLevel, boolean readOnly)
            {
                return new ConnectorTransactionHandle() {};
            }

            @Override
            public ConnectorMetadata getMetadata(ConnectorTransactionHandle transaction)
            {
                return metadata;
            }

            @Override
            public ConnectorSplitManager getSplitManager()
            {
                throw new UnsupportedOperationException();
            }
        };
    }
}
