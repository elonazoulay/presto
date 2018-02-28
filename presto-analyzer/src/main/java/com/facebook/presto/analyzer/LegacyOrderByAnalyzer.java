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
import com.facebook.presto.sql.parser.ParsingException;
import com.facebook.presto.sql.parser.ParsingOptions;
import com.facebook.presto.sql.parser.SqlParser;
import com.facebook.presto.sql.parser.SqlParserOptions;
import com.facebook.presto.sql.tree.Identifier;
import com.facebook.presto.sql.tree.OrderBy;
import com.facebook.presto.sql.tree.QuerySpecification;
import com.facebook.presto.sql.tree.Select;
import com.facebook.presto.sql.tree.SelectItem;
import com.facebook.presto.sql.tree.SingleColumn;
import com.facebook.presto.sql.tree.SortItem;
import com.facebook.presto.sql.tree.Statement;
import com.facebook.presto.sql.util.AstUtils;
import com.google.common.collect.ImmutableSet;
import io.airlift.log.Logger;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public class LegacyOrderByAnalyzer
{
    private static final Logger log = Logger.get(LegacyOrderByAnalyzer.class);
    private final SqlParser sqlParser;
    private final ParsingOptions parsingOptions;

    public LegacyOrderByAnalyzer(SemanticAnalyzerConfig config)
    {
        requireNonNull(config, "config is null");
        SqlParserOptions parserOptions = new SqlParserOptions();
        parserOptions.allowIdentifierSymbol(IdentifierSymbol.COLON, IdentifierSymbol.AT_SIGN);
        this.sqlParser = new SqlParser(parserOptions);
        this.parsingOptions = new ParsingOptions(config.getParsingOptions());
    }

    public boolean isCandidate(QueryDescriptor queryDescriptor)
    {
        requireNonNull(queryDescriptor, "queryDescriptor is null");
        // TODO: handle UNION, EXCEPT and INTERSECTION
        Statement statement;
        try {
            statement = sqlParser.createStatement(queryDescriptor.getSql(), parsingOptions);
        }
        catch (ParsingException e) {
            log.error("QueryId %s: %s", queryDescriptor.getQueryId(), e);
            return false;
        }
        if (!(statement instanceof com.facebook.presto.sql.tree.Query)) {
            return false;
        }
        com.facebook.presto.sql.tree.Query query = (com.facebook.presto.sql.tree.Query) statement;
        QuerySpecification querySpecification = null;
        if (!(query.getQueryBody() instanceof QuerySpecification)) {
            return false;
        }
        querySpecification = (QuerySpecification) query.getQueryBody();

        OrderBy orderBy = null;
        if (!querySpecification.getOrderBy().isPresent()) {
            return false;
        }
        orderBy = querySpecification.getOrderBy().get();
        Select select = querySpecification.getSelect();
        Set<Identifier> aliases = extractAliases(select.getSelectItems());
        // TODO: handle expressions, function calls, window functions
        List<SortItem> sortItems = orderBy.getSortItems();

        return findIdentifiersInSortItems(aliases, sortItems);
    }

    private Set<Identifier> extractAliases(List<SelectItem> selectItems)
    {
        return requireNonNull(selectItems, "selectItems is null")
                .stream()
                .filter(selectItem -> selectItem instanceof SingleColumn)
                .map(selectItem -> ((SingleColumn) selectItem).getAlias())
                .flatMap(alias -> alias.map(Stream::of).orElse(Stream.empty()))
                .collect(ImmutableSet.toImmutableSet());
    }

    private boolean findIdentifiersInSortItems(Set<Identifier> aliases, List<SortItem> sortItems)
    {
        requireNonNull(aliases, "aliases is null");
        requireNonNull(sortItems, "sortItems is null");
        for (SortItem sortItem : sortItems) {
            if (AstUtils.preOrder(sortItem)
                    .filter(node -> aliases.contains(node))
                    .findFirst()
                    .isPresent()) {
                return true;
            }
        }
        return false;
    }

    public static void main(String[] args)
    {
        String sql = "select t.z/t.y a, b from t order by t.z, a, t.z, t.z/t.y, ln(a) + ln(ln(a/t.z))";
        LegacyOrderByAnalyzer analyzer = new LegacyOrderByAnalyzer(new SemanticAnalyzerConfig());
        analyzer.isCandidate(new QueryDescriptor("test", "test", "test_id", sql));
    }
}
