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

import com.facebook.presto.sql.parser.ParsingOptions;
import io.airlift.configuration.Config;
import io.airlift.configuration.ConfigDescription;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

public class SemanticAnalyzerConfig
{
    private ParsingOptions.DecimalLiteralTreatment parsingOptions = ParsingOptions.DecimalLiteralTreatment.AS_DOUBLE;
    private String source;
    private String sourceType = "file";
    private char delimiter = '\t';
    private char escape = '\\';
    private Path directory = Paths.get(".");
    private Pattern pattern = Pattern.compile("(.*)");
    private String prefix = "";

    public ParsingOptions.DecimalLiteralTreatment getParsingOptions()
    {
        return parsingOptions;
    }

    @ConfigDescription("Parsing options for DecimalLiteralTreatment")
    @Config("parsing-options")
    public SemanticAnalyzerConfig setParsingOptions(String decmalLiteralTreatment)
    {
        this.parsingOptions = ParsingOptions.DecimalLiteralTreatment.valueOf(decmalLiteralTreatment.toUpperCase());
        return this;
    }

    public String getSourceType()
    {
        return this.sourceType;
    }

    @ConfigDescription("Type of source: file, resource")
    @Config("source-type")
    public SemanticAnalyzerConfig setSourceType(String sourceType)
    {
        this.sourceType = sourceType;
        return this;
    }
    public String getSource()
    {
        return source;
    }

    @ConfigDescription("File/Resource name for query descriptors: environment,query_id,source,query")
    @Config("source")
    public SemanticAnalyzerConfig setSource(String source)
    {
        this.source = source;
        return this;
    }

    public char getDelimiter()
    {
        return delimiter;
    }

    @ConfigDescription("Delimiter for query descriptor source")
    @Config("delimiter")
    public SemanticAnalyzerConfig setDelimiter(char delimiter)
    {
        this.delimiter = delimiter;
        return this;
    }

    public char getEscape()
    {
        return escape;
    }

    @ConfigDescription("Escape character")
    @Config("escape")
    public SemanticAnalyzerConfig setEscape(char escape)
    {
        this.escape = escape;
        return this;
    }

    public Path getDirectory()
    {
        return directory;
    }

    @ConfigDescription("Directory to put files in")
    @Config("directory")
    public SemanticAnalyzerConfig setDirectory(String directory)
    {
        this.directory = Paths.get(directory);
        return this;
    }

    public Pattern getPattern()
    {
        return pattern;
    }
    @ConfigDescription("Source regexp, must contain atleast one group, the first of which will be used ex. \".*(argus\\d+)$\"")
    @Config("source-regex")
    public SemanticAnalyzerConfig setPattern(String pattern)
    {
        this.pattern = Pattern.compile(pattern);
        return this;
    }

    public String getPrefix()
    {
        return prefix;
    }

    @ConfigDescription("Prefix to output file name")
    @Config("prefix")
    public SemanticAnalyzerConfig setPrefix(String prefix)
    {
        this.prefix = prefix;
        return this;
    }
}
