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
package com.facebook.presto.analyzer.grouping;

import com.facebook.presto.analyzer.QueryDescriptor;
import com.facebook.presto.analyzer.SemanticAnalyzerConfig;
import com.facebook.presto.analyzer.SemanticAnalyzerRunner;
import com.facebook.presto.analyzer.TSVSource;
import com.google.common.collect.Streams;
import io.airlift.log.Logger;

import javax.inject.Inject;

import static java.util.Objects.requireNonNull;

public class SemanticTransforRunner
        implements Runnable
{
    private static final Logger log = Logger.get(SemanticAnalyzerRunner.class);
    private final SemanticAnalyzerConfig config;
    private final SemanticTransform semanticTransform;
    private final QueryDescriptorWriter writer;

    @Inject
    public SemanticTransforRunner(SemanticTransform semanticTransform, SemanticAnalyzerConfig config, QueryDescriptorWriter writer)
    {
        this.semanticTransform = requireNonNull(semanticTransform, "semanticTransform is null");
        this.config = requireNonNull(config, "config is null");
        this.writer = requireNonNull(writer, "writer is null");
    }

    @Override
    public void run()
    {
        TSVSource source = TSVSource.create(config);
        Streams.stream(source)
                .map(descriptor -> new Tuple(descriptor, semanticTransform.apply(descriptor)))
                .filter(tuple -> semanticTransform.test(tuple.info))
                .forEach(tuple -> writer.accept(tuple.descriptor, tuple.info));
    }

    public static class Tuple<T>
    {
        public final QueryDescriptor descriptor;
        public final T info;

        public Tuple(QueryDescriptor descriptor, T info)
        {
            this.descriptor = descriptor;
            this.info = info;
        }
    }
}
