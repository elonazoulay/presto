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
import com.facebook.presto.sql.tree.AstVisitor;
import com.facebook.presto.sql.tree.DereferenceExpression;
import com.facebook.presto.sql.tree.ExistsPredicate;
import com.facebook.presto.sql.tree.Expression;
import com.facebook.presto.sql.tree.Identifier;
import com.facebook.presto.sql.tree.IfExpression;
import com.facebook.presto.sql.tree.Node;
import com.facebook.presto.sql.tree.OrderBy;
import com.facebook.presto.sql.tree.QuerySpecification;
import com.facebook.presto.sql.tree.Select;
import com.facebook.presto.sql.tree.SelectItem;
import com.facebook.presto.sql.tree.SingleColumn;
import com.facebook.presto.sql.tree.SortItem;
import com.facebook.presto.sql.tree.Statement;
import com.facebook.presto.sql.tree.SubqueryExpression;
import com.google.common.collect.ImmutableSet;
import io.airlift.log.Logger;

import java.util.List;
import java.util.Set;

import static java.util.Objects.requireNonNull;

public class LegacyOrderByAnalyzer
{
    private static final Logger log = Logger.get(LegacyOrderByAnalyzer.class);
    private final SqlParser sqlParser;
    private final ParsingOptions parsingOptions;
    private final SemanticAnalyzerConfig config;

    public LegacyOrderByAnalyzer(SemanticAnalyzerConfig config)
    {
        this.config = requireNonNull(config, "config is null");
        SqlParserOptions parserOptions = new SqlParserOptions();
        parserOptions.allowIdentifierSymbol(IdentifierSymbol.COLON, IdentifierSymbol.AT_SIGN);
        this.sqlParser = new SqlParser(parserOptions);
        this.parsingOptions = new ParsingOptions(config.getParsingOptions());
    }

    public boolean isCandidate(QueryDescriptor queryDescriptor)
    {
        requireNonNull(queryDescriptor, "queryDescriptor is null");
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
        List<SortItem> sortItems = orderBy.getSortItems();
        if (querySpecification.getGroupBy().isPresent()) {
            return findAliasesWithGroupBy(extractAllAliases(select.getSelectItems()), sortItems);
        }
        else {
            return findAliasesInSortItems(extractAliases(select.getSelectItems()), extractAllAliases(select.getSelectItems()), sortItems);
        }
    }

    private Set<Identifier> extractAliases(List<SelectItem> selectItems)
    {
        return requireNonNull(selectItems, "selectItems is null").stream()
                .filter(selectItem -> selectItem instanceof SingleColumn)
                .map(selectItem -> (SingleColumn) selectItem)
                .filter(singleColumn -> singleColumn.getAlias().isPresent())
                .filter(singleColumn -> new AliasVisitor(ImmutableSet.of(singleColumn.getAlias().get())).process(singleColumn.getExpression()))
                .map(singleColumn -> singleColumn.getAlias().get())
                .collect(ImmutableSet.toImmutableSet());
    }

    private Set<Identifier> extractAllAliases(List<SelectItem> selectItems)
    {
        return requireNonNull(selectItems, "selectItems is null").stream()
                .filter(selectItem -> selectItem instanceof SingleColumn)
                .map(selectItem -> (SingleColumn) selectItem)
                .filter(singleColumn -> singleColumn.getAlias().isPresent())
                .map(singleColumn -> singleColumn.getAlias().get())
                .collect(ImmutableSet.toImmutableSet());
    }

    private boolean findAliasesInSortItems(Set<Identifier> aliases, Set<Identifier> allAliases, List<SortItem> sortItems)
    {
        requireNonNull(aliases, "aliases is null");
        requireNonNull(sortItems, "sortItems is null");
        return sortItems.stream()
                .filter(sortItem -> new AliasVisitor(aliases, allAliases).process(sortItem.getSortKey()))
                .findFirst()
                .map(sortItem -> true)
                .orElse(false);
    }

    private boolean findAliasesWithGroupBy(Set<Identifier> allAliases, List<SortItem> sortItems)
    {
        requireNonNull(sortItems, "sortItems is null");
        return sortItems.stream()
                .filter(sortItem -> !(sortItem.getSortKey() instanceof Identifier))
                .filter(sortItem -> new AliasVisitor(allAliases, allAliases).process(sortItem.getSortKey()))
                .findFirst()
                .map(sortItem -> true)
                .orElse(false);
    }

    private static class AliasVisitor
            extends AstVisitor<Boolean, Void>
    {
        private final Set<Identifier> aliases;
        private final Set<Identifier> allAliases;

        public AliasVisitor(Set<Identifier> aliases)
        {
            this(aliases, ImmutableSet.of());
        }

        public AliasVisitor(Set<Identifier> aliases, Set<Identifier> allAliases)
        {
            this.aliases = ImmutableSet.copyOf(requireNonNull(aliases, "aliases is null"));
            this.allAliases = ImmutableSet.copyOf(requireNonNull(allAliases, "allAliases is null"));
        }

        @Override
        protected Boolean visitNode(Node node, Void context)
        {
            for (Node child : node.getChildren()) {
                if (process(child, context)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        protected Boolean visitIfExpression(IfExpression node, Void context)
        {
            return false;
        }

        @Override
        protected Boolean visitDereferenceExpression(DereferenceExpression node, Void context)
        {
            if (!allAliases.isEmpty()) {
                Expression expression = node;
                while (!(expression instanceof Identifier)) {
                    expression = ((DereferenceExpression) expression).getBase();
                }
                return allAliases.contains(expression);
            }
            else {
                return false;
            }
        }

        @Override
        protected Boolean visitIdentifier(Identifier node, Void context)
        {
            return aliases.contains(node);
        }

        @Override
        protected Boolean visitSubqueryExpression(SubqueryExpression node, Void context)
        {
            // Don't traverse into Subqueries within an Expression
            return false;
        }

        @Override
        protected Boolean visitExists(ExistsPredicate node, Void context)
        {
            // Don't traverse into Subqueries within an Expression
            return false;
        }
    }

    public static void main(String[] args)
    {
        String sql = "select t.z/t.y a, b from t order by t.z, a, t.z, t.z/t.y, ln(a) + ln(ln(a/t.z))";
        SemanticAnalyzerConfig config = new SemanticAnalyzerConfig();
        LegacyOrderByAnalyzer analyzer = new LegacyOrderByAnalyzer(config);
        analyzer.isCandidate(new QueryDescriptor("test", "test", "test", "test", "test_id", sql));
    }
}
