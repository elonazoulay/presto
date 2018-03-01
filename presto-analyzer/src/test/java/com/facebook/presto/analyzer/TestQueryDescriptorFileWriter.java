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

import com.google.common.io.RecursiveDeleteOption;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.google.common.io.MoreFiles.deleteRecursively;
import static org.testng.Assert.assertEquals;

@Test(singleThreaded = true)
public class TestQueryDescriptorFileWriter
{
    private Path tmpDir;

    @BeforeMethod
    public void setUp()
            throws IOException
    {
        tmpDir = null;
        tmpDir = Files.createTempDirectory("tmp");
    }

    @AfterMethod
    public void tearDown()
            throws IOException
    {
        deleteRecursively(tmpDir, RecursiveDeleteOption.ALLOW_INSECURE);
    }

    @Test
    public void testSource()
            throws IOException
    {
        SemanticAnalyzerConfig config = getConfig();
        config.setDirectory(tmpDir.toString());
        QueryDescriptorFileWriter fileWriter = new QueryDescriptorFileWriter(config);
        QueryDescriptor descriptor = new QueryDescriptor("test", "test", "test", "test",
                "unidash:1857098507844978:argus:450931", "select a order by a");
        assertEquals(fileWriter.getMatchedSource(descriptor), "450931");
    }

    @Test
    public void testEnvironment()
    {
        SemanticAnalyzerConfig config = getConfig();
        config.setDirectory(tmpDir.toString());
        QueryDescriptorFileWriter fileWriter = new QueryDescriptorFileWriter(config);
        QueryDescriptor descriptor = new QueryDescriptor("de1prod", "raptor", "test", "test",
                "unidash:1857098507844978:argus:450931", "select a order by a");
        assertEquals(fileWriter.getEnvironmentTier(descriptor), "de1-prod");
        descriptor = new QueryDescriptor("presto_tools", "raptor", "test", "test",
                "unidash:1857098507844978:argus:450931", "select a order by a");
        assertEquals(fileWriter.getEnvironmentTier(descriptor), "presto_tools");
    }

    private static SemanticAnalyzerConfig getConfig()
    {
        return new SemanticAnalyzerConfig()
                .setSource("test.sql")
                .setSourceType("resource")
                .setPattern(".*argus:(\\d+)$")
                .setPrefix("argus");
    }
}
