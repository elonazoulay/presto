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

import com.facebook.presto.analyzer.TSVSource.ResourceProvider;
import com.google.common.io.RecursiveDeleteOption;
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.google.common.io.MoreFiles.deleteRecursively;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestSemanticAnalyzer
{
    @Test
    public void testTsv()
            throws IOException
    {
        SemanticAnalyzerConfig config = new SemanticAnalyzerConfig();
        config.setSource("test.sql");
        config.setSourceType("resource");
        TSVReader tsvReader = new TSVReader(new TSVSource.ResourceProvider(config).get(),
                config.getDelimiter(),
                config.getEscape());
        for (List<String> row = tsvReader.readLine(); row != null; row = tsvReader.readLine()) {
            assertEquals(row.size(), 6);
        }
    }

    @Test
    public void testLegacyAnalyzer()
            throws IOException
    {
        SemanticAnalyzerConfig config = new SemanticAnalyzerConfig();
        config.setSource("test.sql");
        config.setSourceType("resource");
        config.setPattern(".*argus:(\\d+)$");
        config.setPrefix("argus");
        Path tmpDir = null;
        try {
            tmpDir = Files.createTempDirectory("tmp");
            config.setDirectory(tmpDir.toString());
            LegacyOrderByRunner command = new LegacyOrderByRunner(config);
            command.run();
            int i = 1 + 2;
        }
        finally {
            if (tmpDir != null) {
                deleteRecursively(tmpDir, RecursiveDeleteOption.ALLOW_INSECURE);
            }
        }
    }

    @Test
    public void testSql()
    {
        LegacyOrderByAnalyzer analyzer = new LegacyOrderByAnalyzer(new SemanticAnalyzerConfig());
        assertFalse(analyzer.test(getTestDescriptor("select ln(a) as a from t order by t.a")));
        assertTrue(analyzer.test(getTestDescriptor("select ln(a) as a from t order by ln(a)")));
        assertFalse(analyzer.test(
                getTestDescriptor("select a, sum(1) as count from t group by a order by count")));
        assertFalse(analyzer.test(
                getTestDescriptor("select a, sum(t.count) as count from t group by a order by count")));
        assertTrue(analyzer.test(
                getTestDescriptor("select a, sum(-count) as count from t group by a order by sum(-count)")));
        assertTrue(analyzer.test(
                getTestDescriptor("select a, sum(-count) as count from t group by a order by sum(count)")));
    }

    @Test
    public void testQualified()
            throws IOException
    {
        SemanticAnalyzerConfig config = new SemanticAnalyzerConfig();
        LegacyOrderByAnalyzer analyzer = new LegacyOrderByAnalyzer(config);
        assertTrue(analyzer.test(getDescriptorFromResource("query5_groupby_candidate.sql")));
        assertTrue(analyzer.test(getDescriptorFromResource("query1_candidate.sql")));
        assertTrue(analyzer.test(getDescriptorFromResource("query9_candidate.sql")));
        assertFalse(analyzer.test(getDescriptorFromResource("query8_cornercase_notcandidate.sql")));
        assertFalse(analyzer.test(getDescriptorFromResource("query7_notcandidate.sql")));
        assertFalse(analyzer.test(getDescriptorFromResource("query4_groupby_notcandidate.sql")));
        assertFalse(analyzer.test(getDescriptorFromResource("query6_groupby_notcandidate.sql")));
        assertTrue(analyzer.test(getDescriptorFromResource("query2_candidate.sql")));
        assertTrue(analyzer.test(getDescriptorFromResource("query3_candidate.sql")));
        assertFalse(analyzer.test(getDescriptorFromResource("query10_if_nocandidate.sql")));
        assertTrue(analyzer.test(getDescriptorFromResource("query11_candidate.sql")));
        assertFalse(analyzer.test(getDescriptorFromResource("query12_notcandidate.sql")));
        assertTrue(analyzer.test(getDescriptorFromResource("query13_candidate.sql")));
        assertTrue(analyzer.test(getDescriptorFromResource("query14_candidate.sql")));
    }

    private static QueryDescriptor getDescriptorFromResource(String resource)
            throws IOException
    {
        SemanticAnalyzerConfig config = new SemanticAnalyzerConfig()
                .setSource(resource)
                .setSourceType("resource");
        StringBuilder builder = new StringBuilder();
        ResourceProvider provider = new ResourceProvider(config);
        BufferedReader reader = new BufferedReader(new InputStreamReader(provider.get()));
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            builder.append(line).append("\n");
        }
        return getTestDescriptor(builder.toString());
    }

    static QueryDescriptor getTestDescriptor(String sql)
    {
        return new QueryDescriptor("test", "test", "test_schema", "test_id", "test_source", sql);
    }
}
