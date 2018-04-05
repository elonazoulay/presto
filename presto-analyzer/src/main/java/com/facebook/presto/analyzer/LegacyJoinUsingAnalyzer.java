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

import com.facebook.presto.sql.parser.IdentifierSymbol;
import com.facebook.presto.sql.parser.ParsingOptions;
import com.facebook.presto.sql.parser.SqlParser;
import com.facebook.presto.sql.parser.SqlParserOptions;
import com.facebook.presto.sql.tree.Explain;
import com.facebook.presto.sql.tree.Join;
import com.facebook.presto.sql.tree.JoinUsing;
import com.facebook.presto.sql.tree.Statement;
import com.facebook.presto.sql.util.AstUtils;
import io.airlift.log.Logger;

import javax.inject.Inject;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class LegacyJoinUsingAnalyzer
        implements SemanticAnalyzer
{
    private static final Logger log = Logger.get(LegacyJoinUsingAnalyzer.class);

    private final SqlParser sqlParser;
    private final ParsingOptions parsingOptions;

    @Inject
    public LegacyJoinUsingAnalyzer(SemanticAnalyzerConfig config)
    {
        requireNonNull(config, "config is null");
        SqlParserOptions parserOptions = new SqlParserOptions();
        parserOptions.allowIdentifierSymbol(IdentifierSymbol.COLON, IdentifierSymbol.AT_SIGN);
        this.sqlParser = new SqlParser(parserOptions);
        this.parsingOptions = new ParsingOptions(config.getParsingOptions());
    }

    @Override
    public boolean test(QueryDescriptor descriptor)
    {
        Statement statement = sqlParser.createStatement(descriptor.getSql(), parsingOptions);
        if (statement instanceof Explain) {
            return false;
        }
        return AstUtils.preOrder(statement)
                .filter(Join.class::isInstance)
                .map(join -> ((Join) join).getCriteria())
                .filter(criteria -> criteria.isPresent())
                .map(Optional::get)
                .filter(JoinUsing.class::isInstance)
                .findFirst()
                .isPresent();
    }
}
