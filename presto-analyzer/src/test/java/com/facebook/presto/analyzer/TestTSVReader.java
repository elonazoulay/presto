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

import org.testng.annotations.Test;

import java.io.IOException;
import java.util.List;

import static org.testng.Assert.assertEquals;

public class TestTSVReader
{
    @Test
    public void testTsv()
            throws IOException
    {
        SemanticAnalyzerConfig config = new SemanticAnalyzerConfig();
        config.setSource("test.sql");
        TSVReader tsvReader = new TSVReader(new TSVSource.ResourceProvider(config).get(),
                config.getDelimiter(),
                config.getEscape());
        for (List<String> row = tsvReader.readLine(); row != null; row = tsvReader.readLine()) {
            assertEquals(row.size(), 4);
        }
    }

    @Test
    public void testLegacyAnalyzer()
    {
        SemanticAnalyzerConfig config = new SemanticAnalyzerConfig();
        config.setSource("test.sql");
        AnalyzeCommandRunner command = new AnalyzeCommandRunner(config);
        command.run();
    }
}
