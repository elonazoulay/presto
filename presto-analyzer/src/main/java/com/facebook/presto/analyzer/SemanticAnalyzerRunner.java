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

import javax.inject.Inject;

import static java.util.Objects.requireNonNull;

public class SemanticAnalyzerRunner
        implements Runnable
{
    private static final Logger log = Logger.get(SemanticAnalyzerRunner.class);
    private final SemanticAnalyzerConfig config;
    private final SemanticAnalyzer semanticAnalyzer;
    private final QueryDescriptorConsumer consumer;

    @Inject
    public SemanticAnalyzerRunner(SemanticAnalyzer semanticAnalyzer, SemanticAnalyzerConfig config, QueryDescriptorConsumer consumer)
    {
        this.semanticAnalyzer = requireNonNull(semanticAnalyzer, "semanticAnalyzer is null");
        this.config = requireNonNull(config, "config is null");
        this.consumer = requireNonNull(consumer, "consumer is null");
    }

    @Override
    public void run()
    {
        TSVSource source = TSVSource.create(config);
        Streams.stream(source)
                .filter(semanticAnalyzer)
                .forEach(descriptor -> {
                    consumer.accept(descriptor, false);
                    consumer.accept(descriptor, true);
                });
    }
}
