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

import com.facebook.presto.analyzer.grouping.GroupingAnalyzer;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.OptionalInt;

import static com.facebook.presto.analyzer.TestSemanticAnalyzer.getTestDescriptor;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

@Test(singleThreaded = true)
public class TestGroupingAnalyzer
{
    private GroupingAnalyzer analyzer;

    @BeforeClass
    public void setup()
    {
        analyzer = new GroupingAnalyzer(new SemanticAnalyzerConfig().setSourceType("resource"));
    }

    @Test
    public void test()
    {
        QueryDescriptor descriptor = getTestDescriptor("select a, b, c, sum(d) from (\n" +
                "select a, b, c, sum(d) as d\n" +
                "from (values (1, 2, 3, 1), (1, 2, 4, 1), (2, 2, 3, 1), (2, 2, 4, 1), (3, 2, 3, 1)) t(a,b,c, d) \n" +
                "group by \n" +
                "grouping sets ((a), (a, b), (b, c), (c))\n" +
                ") \n" +
                "group by grouping sets ((a), (a, b), (b, c))");
        OptionalInt product = analyzer.apply(descriptor);
        assertTrue(product.isPresent() && product.getAsInt() == 4);
    }

    @Test
    public void testProduct()
    {
        QueryDescriptor descriptor = getTestDescriptor("select a, b, c, sum(d) from (\n" +
                "select a, b, c, sum(d) as d\n" +
                "from (values (1, 2, 3, 1), (1, 2, 4, 1), (2, 2, 3, 1), (2, 2, 4, 1), (3, 2, 3, 1)) t(a,b,c, d) \n" +
                "group by \n" +
                "grouping sets ((a), (a, b), (b, c), (c), (a, b, c))\n" +
                ") \n" +
                "group by grouping sets ((a), (a, b), (b, c), (c)), \n" +
                "grouping sets((a,c), (a,b,c))");
        OptionalInt product = analyzer.apply(descriptor);
        assertTrue(product.isPresent() && product.getAsInt() == 8);
    }

    @Test
    public void testRegularGroupBy()
    {
        QueryDescriptor descriptor = getTestDescriptor("select a,b, sum(c) from (values (1,2,3), (1,2,4), (2,2,5)) t(a,b,c) group by a,b");
        OptionalInt product = analyzer.apply(descriptor);
        assertTrue(product.isPresent() && product.getAsInt() == 1);
    }

    @Test
    public void testNoGroupBy()
    {
        QueryDescriptor descriptor = getTestDescriptor("select a,b, sum(c) from (values (1,2,3), (1,2,4), (2,2,5)) t(a,b,c)");
        OptionalInt product = analyzer.apply(descriptor);
        assertFalse(product.isPresent());
    }
}
