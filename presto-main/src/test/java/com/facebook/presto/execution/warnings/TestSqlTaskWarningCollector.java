package com.facebook.presto.execution.warnings;

import com.facebook.presto.testing.TestingSqlTaskWarningCollector;
import org.testng.annotations.Test;

import java.util.stream.IntStream;

import static com.facebook.presto.testing.TestingSqlTaskWarningCollector.createTestSqlTaskWarning;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestSqlTaskWarningCollector
{
    @Test
    public void testSqlTaskWarningCollector()
    {
        // SqlTaskWarningCollector should only return newly added warnings
        ClearingWarningCollector warningCollector = new SqlTaskWarningCollector(new WarningCollectorConfig());
        int count = 5;
        IntStream.range(0, count)
                .mapToObj(TestingSqlTaskWarningCollector::createTestSqlTaskWarning)
                .forEach(warningCollector::add);
        assertEquals(warningCollector.getAndClearWarnings().size(), count);
        warningCollector.add(createTestSqlTaskWarning(count + 1));
        assertEquals(warningCollector.getAndClearWarnings().size(), 1);
        assertTrue(warningCollector.getAndClearWarnings().isEmpty());
    }
}
