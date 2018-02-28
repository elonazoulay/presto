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

import com.google.common.collect.ImmutableList;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

public class TSVReader
{
    private final BufferedReader reader;
    private final char delimiter;
    private final char escape;

    public TSVReader(InputStream inputStream, char delimiter, char escape)
    {
        this.reader = new BufferedReader(
                new InputStreamReader(requireNonNull(inputStream, "inputStream is null"), UTF_8));
        this.delimiter = delimiter;
        this.escape = escape;
    }

    public List<String> readLine()
            throws IOException
    {
        String line = reader.readLine();
        if (line == null) {
            return null;
        }
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == delimiter) {
                builder.add(sb.toString());
                sb = new StringBuilder();
            }
            else if (line.charAt(i) == escape) {
                if (++i == line.length()) {
                    throw new IOException("Invalid character");
                }
                switch(line.charAt(i)) {
                    case '0':
                        sb.append('\0');
                        break;
                    case 'f':
                        sb.append('\f');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case '\\':
                        sb.append('\\');
                        break;
                    default:
                        sb.append(escape).append(line.charAt(i));
                        break;
                }
            }
            else {
                sb.append(line.charAt(i));
            }
        }
        builder.add(sb.toString());
        return builder.build();
    }
}
