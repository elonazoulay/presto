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
package com.facebook.presto.cli;

import com.facebook.presto.spi.PrestoWarning;
import com.google.common.collect.AbstractIterator;

import java.util.Iterator;
import java.util.List;

import static com.facebook.presto.cli.ConsolePrinter.REAL_TERMINAL;
import static java.lang.String.format;

abstract class AbstractWarningsPrinter
        implements WarningsPrinter
{
    private static final String WARNING_BEGIN = ((char) 27) + "[33m";
    private static final String WARNING_END = ((char) 27) + "[39m";

    private boolean hasProcessedWarnings;
    private int processedWarnings;

    protected String getWarningMessage(PrestoWarning warning)
    {
        // If this is a real terminal color the warnings yellow
        if (REAL_TERMINAL) {
            return format("%sWARNING: %s%s", WARNING_BEGIN, warning.getMessage(), WARNING_END);
        }
        return format("WARNING: %s", warning.getMessage());
    }

    protected int getProcessedWarnings()
    {
        return processedWarnings;
    }

    protected Iterator<PrestoWarning> warningIterator(List<PrestoWarning> warnings)
    {
        return new AbstractIterator<PrestoWarning>() {
            @Override
            protected PrestoWarning computeNext()
            {
                if (processedWarnings < warnings.size()) {
                    return warnings.get(processedWarnings++);
                }
                return endOfData();
            }
        };
    }

    protected abstract void print(List<PrestoWarning> warnings);

    protected abstract void printSeparator();

    private void printWithSeparators(List<PrestoWarning> warnings)
    {
        // Print warnings separated from previous and subsequent output
        if (warnings.size() > getProcessedWarnings()) {
            printSeparator();
            print(warnings);
            printSeparator();
        }
    }

    private void printWithInitialSeparator(List<PrestoWarning> warnings)
    {
        // Separate first warnings from previous output
        if (warnings.size() > 0 && !hasProcessedWarnings) {
            printSeparator();
            hasProcessedWarnings = true;
            print(warnings);
        }
    }

    private void printWithTrailingSeparator(List<PrestoWarning> warnings)
    {
        // Print warnings and separate from subsequent output
        if (warnings.size() > getProcessedWarnings()) {
            print(warnings);
            printSeparator();
        }
    }

    @Override
    public void print(List<PrestoWarning> warnings, boolean withInitialSeparator, boolean withTrailingSeparator)
    {
        if (withInitialSeparator) {
            if (withTrailingSeparator) {
                printWithSeparators(warnings);
            }
            else {
                printWithInitialSeparator(warnings);
            }
        }
        else if (withTrailingSeparator) {
            printWithTrailingSeparator(warnings);
        }
        else {
            print(warnings);
        }
    }
}
