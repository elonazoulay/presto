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
import com.facebook.presto.sql.parser.IdentifierSymbol;
import com.facebook.presto.sql.parser.ParsingOptions;
import com.facebook.presto.sql.parser.SqlParser;
import com.facebook.presto.sql.parser.SqlParserOptions;
import com.facebook.presto.sql.tree.DefaultTraversalVisitor;
import com.facebook.presto.sql.tree.Explain;
import com.facebook.presto.sql.tree.GroupBy;
import com.facebook.presto.sql.tree.GroupingElement;
import com.facebook.presto.sql.tree.Statement;
import io.airlift.log.Logger;

import javax.inject.Inject;

import java.util.OptionalInt;

import static java.util.Objects.requireNonNull;

public class GroupingAnalyzer
        implements SemanticTransform<OptionalInt>
{
    private static final Logger log = Logger.get(GroupingAnalyzer.class);

    private final SqlParser sqlParser;
    private final ParsingOptions parsingOptions;

    @Inject
    public GroupingAnalyzer(SemanticAnalyzerConfig config)
    {
        requireNonNull(config, "config is null");
        SqlParserOptions parserOptions = new SqlParserOptions();
        parserOptions.allowIdentifierSymbol(IdentifierSymbol.COLON, IdentifierSymbol.AT_SIGN);
        this.sqlParser = new SqlParser(parserOptions);
        this.parsingOptions = new ParsingOptions(config.getParsingOptions());
    }

    @Override
    public OptionalInt apply(QueryDescriptor descriptor)
    {
        Statement statement;
        try {
            statement = sqlParser.createStatement(descriptor.getSql(), parsingOptions);
        }
        catch (Exception e) {
            log.error("Error parsing query id %s : %s", descriptor.getQueryId(), e);
            return OptionalInt.empty();
        }
        if (statement instanceof Explain) {
            return OptionalInt.empty();
        }
        /*
        OptionalInt value = AstUtils.preOrder(statement)
                .filter(GroupBy.class::isInstance)
                .mapToInt(groupBy -> ((GroupBy) groupBy).getGroupingElements().stream()
                        .mapToInt(element -> element.enumerateGroupingSets().size()).reduce(1, (a, b) -> a * b)).max();
                        */
        int intValue = visit(statement);
        OptionalInt value = intValue == 0 ? OptionalInt.empty() : OptionalInt.of(intValue);
        return value;
    }

    @Override
    public boolean test(OptionalInt product)
    {
        return product.isPresent() && product.getAsInt() > 1;
    }

    public int visit(Statement statement)
    {
        class MaxRef
        {
            int max;
            void setIfMax(int candidate)
            {
                max = Math.max(candidate, max);
            }
        }
        MaxRef maxRef = new MaxRef();
        new DefaultTraversalVisitor<Void, Void>() {
            @Override
            public Void visitGroupBy(GroupBy node, Void context)
            {
                int product = 1;
                for (GroupingElement groupingElement : node.getGroupingElements()) {
                    super.process(groupingElement);
                    product *= groupingElement.enumerateGroupingSets().size();
                }
                maxRef.setIfMax(product);
                return null;
            }
        }.process(statement);
        return maxRef.max;
    }
}
