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

import com.google.inject.Inject;

import javax.inject.Provider;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class LegacyOrderByRunner
        implements Runnable
{
    private final Provider<InputStream> inputStreamProvider;
    private final SemanticAnalyzerConfig config;

    @Inject
    public LegacyOrderByRunner(SemanticAnalyzerConfig config)
    {
        this.config = requireNonNull(config, "config is null");
        this.inputStreamProvider = TSVSource.getProvider(config);
    }

    @Override
    public void run()
    {
        try {
            LegacyOrderByAnalyzer analyzer = new LegacyOrderByAnalyzer(config);
            Path directory = config.getDirectory();
            if (!Files.exists(directory)) {
                Files.createDirectory(directory);
            }
            else if (!Files.isDirectory(directory)) {
                throw new RuntimeException(format("Invalid directory %s", directory));
            }
            LegacyOrderByFileWriter fileWriter = new LegacyOrderByFileWriter(config);
            TSVSource source = new TSVSource(inputStreamProvider.get(), config);
            while (source.hasNext()) {
                QueryDescriptor queryDescriptor = source.next();
                if (analyzer.test(queryDescriptor)) {
                    fileWriter.accept(queryDescriptor, false);
                    fileWriter.accept(queryDescriptor, true);
                }
            }
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
