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
package com.facebook.presto;

import com.facebook.presto.execution.DedupingWarningCollector;
import com.facebook.presto.execution.WarningCollector;
import com.facebook.presto.spi.PrestoWarning;
import com.facebook.presto.spi.QueryId;
import com.facebook.presto.spi.WarningCode;
import com.facebook.presto.sql.analyzer.SemanticException;
import com.facebook.presto.sql.planner.LogicalPlanner;
import com.facebook.presto.testing.LocalQueryRunner;
import com.facebook.presto.testing.MaterializedResult;
import com.facebook.presto.testing.QueryRunner;
import org.intellij.lang.annotations.Language;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.facebook.presto.testing.TestingSession.testSessionBuilder;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static org.testng.Assert.fail;

public class TestWarningsUtil
{
    private TestWarningsUtil()
    { }

    public static void assertWarnings(QueryRunner queryRunner, @Language("SQL") String sql, Map<String, String> sessionProperties, List<WarningCode> expectedWarnings)
    {
        Session.SessionBuilder sessionBuilder = testSessionBuilder()
                .setCatalog(queryRunner.getDefaultSession().getCatalog().get())
                .setSchema(queryRunner.getDefaultSession().getSchema().get());
        sessionProperties.forEach(sessionBuilder::setSystemProperty);
        Session testingSession = sessionBuilder.build();
        MaterializedResult result = queryRunner.execute(testingSession, sql);
        Set<WarningCode> warnings = result.getWarnings().stream()
                .map(PrestoWarning::getWarningCode)
                .collect(toImmutableSet());
        for (WarningCode expectedWarning : expectedWarnings) {
            if (!warnings.contains(expectedWarning)) {
                fail("Expected warning: " + expectedWarning);
            }
        }
    }
    public static void assertPlannerWarnings(LocalQueryRunner queryRunner, @Language("SQL") String sql, Map<String, String> sessionProperties, List<WarningCode> expectedWarnings)
    {
        Session.SessionBuilder sessionBuilder = testSessionBuilder()
                .setCatalog(queryRunner.getDefaultSession().getCatalog().get())
                .setSchema(queryRunner.getDefaultSession().getSchema().get());
        sessionProperties.forEach(sessionBuilder::setSystemProperty);
        WarningCollector warningCollector = new DedupingWarningCollector(new QueryId("test"));
        try {
            queryRunner.inTransaction(sessionBuilder.build(), transactionSession -> {
                queryRunner.createPlan(transactionSession, sql, LogicalPlanner.Stage.CREATED, false, warningCollector);
                return null;
            });
        }
        catch (SemanticException e) {
            // ignore
        }
        Set<WarningCode> warnings = warningCollector.getWarnings().stream()
                .map(PrestoWarning::getWarningCode)
                .collect(toImmutableSet());
        for (WarningCode expectedWarning : expectedWarnings) {
            if (!warnings.contains(expectedWarning)) {
                fail("Expected warning: " + expectedWarning);
            }
        }
    }
}
