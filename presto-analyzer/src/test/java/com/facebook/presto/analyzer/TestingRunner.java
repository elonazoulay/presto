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

import com.google.common.collect.Streams;
import io.airlift.log.Logger;
import org.testng.TestRunner;

import java.util.function.Consumer;

// import static com.google.common.base.Throwables.throwIfUnchecked;
import static java.util.Objects.requireNonNull;

public class TestingRunner
        implements Runnable
{
    private static final Logger log = Logger.get(TestRunner.class);
    private final SemanticAnalyzerConfig config;
    private final SemanticAnalyzer semanticAnalyzer;
    private final Consumer<QueryDescriptor> action;

    public TestingRunner(SemanticAnalyzer semanticAnalyzer, String resource, Consumer<QueryDescriptor> action)
    {
        this.semanticAnalyzer = requireNonNull(semanticAnalyzer, "semanticAnalyzer is null");
        config = new SemanticAnalyzerConfig()
                .setSourceType("resource")
                .setSource(requireNonNull(resource, "resource is null"));
        this.action = requireNonNull(action, "action is null");
    }

    @Override
    public void run()
    {
        try {
            TSVSource source = TSVSource.create(config);
            Streams.stream(source)
                    .filter(semanticAnalyzer)
                    .forEach(action);
        }
        catch (Throwable e) {
            log.error("Error analyzing query: %s", e);
            //throwIfUnchecked(e);
            //throw new RuntimeException(e);
        }
    }
}
