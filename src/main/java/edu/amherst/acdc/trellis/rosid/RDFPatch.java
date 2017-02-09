/*
 * Copyright Amherst College
 *
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
package edu.amherst.acdc.trellis.rosid;

import static java.lang.System.lineSeparator;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.newBufferedWriter;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.time.Instant.now;
import static java.util.stream.StreamSupport.stream;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.time.Instant;
import java.util.stream.Stream;

/**
 * @author acoburn
 */
class RDFPatch {

    /**
     * Read the triples from the journal that existed up to (and including) the specified time
     * @param file the file
     * @param time the time
     * @return a stream of RDF Patch statements
     */
    public static Stream<String> read(final File file, final Instant time) {
        return stream(new ReverseFileSpliterator(file, time), false);
    }

    /**
     * Read the triples from the journal for the current state of the resource
     * @param file the file
     * @return a stream of RDF Patch statements
     */
    public static Stream<String> read(final File file) {
        return read(file, now());
    }

    /**
     * Write RDF Patch statements to the specified file
     * @param file the file
     * @param statements the statements
     * @param time the time
     */
    public static void write(final File file, final Stream<String> statements, final Instant time) {
        try (final BufferedWriter writer = newBufferedWriter(file.toPath(), UTF_8, APPEND)) {
            writer.write("BEGIN # " + time.toString() + lineSeparator());
            statements.forEach(statement -> {
                uncheckedWrite(writer, statement + lineSeparator());
            });
            writer.write("END # " + time.toString() + lineSeparator());
        } catch (final IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static void uncheckedWrite(final Writer writer, final String string) {
        try {
            writer.write(string);
        } catch (final IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private RDFPatch() {
        // prevent instantiation
    }
}
