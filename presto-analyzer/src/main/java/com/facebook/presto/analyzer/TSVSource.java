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

import com.google.common.io.Resources;

import javax.inject.Inject;
import javax.inject.Provider;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import static java.util.Objects.requireNonNull;

public class TSVSource
        implements Iterator<QueryDescriptor>
{
    private final TSVReader tsvReader;
    private List<String> line;

    public TSVSource(InputStream inputStream, SemanticAnalyzerConfig config)
            throws IOException
    {
        requireNonNull(inputStream, "inputStreamSupplier is null");
        requireNonNull(config, "config is null");
        tsvReader = new TSVReader(
                inputStream,
                config.getDelimiter(),
                config.getEscape());
        line = tsvReader.readLine();
    }

    @Override
    public boolean hasNext()
    {
        return line != null;
    }

    @Override
    public QueryDescriptor next()
    {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        QueryDescriptor queryDescriptor = new QueryDescriptor(
                line.get(0), line.get(1), line.get(2), line.get(3), line.get(4), line.get(5));
        try {
            line = tsvReader.readLine();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        return queryDescriptor;
    }

    public static class FileProvider
            implements Provider<InputStream>
    {
        private final String source;

        @Inject
        public FileProvider(SemanticAnalyzerConfig config)
        {
            requireNonNull(config, "config is null");
            source = requireNonNull(config.getSource(), "source is null");
        }

        @Override
        public InputStream get()
        {
            requireNonNull(source);
            try {
                return new FileInputStream(source);
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class ResourceProvider
            implements Provider<InputStream>
    {
        private final String source;

        @Inject
        public ResourceProvider(SemanticAnalyzerConfig config)
        {
            requireNonNull(config, "config is null");
            source = requireNonNull(config.getSource(), "source is null");
        }

        public InputStream get()
        {
            try {
                return Resources.getResource(source).openStream();
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public enum SourceType {
        FILE,
        RESOURCE
    }

    public static Provider<InputStream> getProvider(SemanticAnalyzerConfig config)
    {
        requireNonNull(config, "config is null");
        SourceType sourceTypeValue = SourceType.valueOf(config.getSourceType().toUpperCase());
        switch(sourceTypeValue) {
            case FILE:
                return new FileProvider(config);
            case RESOURCE:
                return new ResourceProvider(config);
            default:
                throw new IllegalArgumentException();
        }
    }

    public static TSVSource create(SemanticAnalyzerConfig config)
    {
        try {
            return new TSVSource(getProvider(config).get(), config);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
