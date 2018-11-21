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
package com.facebook.presto.execution.warnings;

import com.facebook.presto.execution.buffer.SerializedPage;
import com.facebook.presto.spi.PrestoWarning;
import com.facebook.presto.testing.TestingSqlTaskWarningCollector;
import org.testng.annotations.Test;

import java.util.List;
import java.util.stream.IntStream;

import static com.facebook.presto.execution.buffer.TestingPagesSerdeFactory.testingPagesSerde;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static org.testng.Assert.assertEquals;

public class TestWarningsSerde
{
    @Test
    public void testWarningSerde()
    {
        List<PrestoWarning> warnings = IntStream.range(0, 5)
                .mapToObj(TestingSqlTaskWarningCollector::createTestSqlTaskWarning)
                .collect(toImmutableList());
        WarningsSerde warningsSerde = new WarningsSerde(testingPagesSerde());
        List<SerializedPage> pages = warningsSerde.serialize(warnings);
        assertEquals(warningsSerde.deserialize(pages), warnings);
    }
}
