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

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Throwables.throwIfUnchecked;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public abstract class AbstractQueryDescriptorWriter<T>
        implements QueryDescriptorWriter<T>
{
    private final SemanticAnalyzerConfig config;
    private static final Pattern TIER_PATTERN = Pattern.compile("^(de\\d+)(.*)");
    private static final String COMMENT_FORMAT = "-- %s\n";
    private static final String DEFAULT_SUFFIX = "out";

    public AbstractQueryDescriptorWriter(SemanticAnalyzerConfig config)
    {
        this.config = requireNonNull(config, "config is null");
        try {
            Path directory = config.getDirectory();
            if (!Files.exists(directory)) {
                Files.createDirectory(directory);
            }
            else if (!Files.isDirectory(directory)) {
                throw new RuntimeException(format("Invalid directory %s", directory));
            }
        }
        catch (Exception e) {
            throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
    }

    protected abstract String getInfo(T info);

    protected String getFileSuffix()
    {
        return DEFAULT_SUFFIX;
    }

    protected String getMatchedSource(QueryDescriptor descriptor)
    {
        Matcher matcher = config.getPattern().matcher(descriptor.getSource());
        return matcher.matches() && matcher.groupCount() > 0 ?
                matcher.group(1) :
                descriptor.getSource();
    }

    protected String getFileName(QueryDescriptor descriptor)
    {
        return format("%s.%s.%s", config.getPrefix(), getMatchedSource(descriptor), getFileSuffix());
    }

    protected String getEnvironmentTier(QueryDescriptor descriptor)
    {
        if (descriptor.getCatalog().equalsIgnoreCase("raptor")) {
            Matcher matcher = TIER_PATTERN.matcher(descriptor.getEnvironment());
            if (matcher.matches()) {
                return format("%s-%s", matcher.group(1), matcher.group(2));
            }
            else {
                return descriptor.getEnvironment();
            }
        }
        else {
            return descriptor.getEnvironment();
        }
    }

    protected String getText(QueryDescriptor descriptor, T info)
    {
        return new StringBuilder()
                .append(format(COMMENT_FORMAT, getEnvironmentTier(descriptor)))
                .append(format(COMMENT_FORMAT, descriptor.getCatalog()))
                .append(format(COMMENT_FORMAT, descriptor.getSchema()))
                .append(format(COMMENT_FORMAT, descriptor.getSource()))
                .append(format(COMMENT_FORMAT, descriptor.getQueryId()))
                .append(format(COMMENT_FORMAT, getInfo(info)))
                .append("EXPLAIN ")
                .append(descriptor.getSql())
                .append("\n;")
                .toString();
    }

    @Override
    public void accept(QueryDescriptor descriptor, T info)
    {
        requireNonNull(info, "info is null");
        Path directory = config.getDirectory();
        try (BufferedWriter writer = Files.newBufferedWriter(directory.resolve(getFileName(descriptor)))) {
            writer.write(getText(descriptor, info));
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
