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

import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;
import static edu.amherst.acdc.trellis.rosid.Constants.MEMENTO_CACHE;
import static edu.amherst.acdc.trellis.rosid.Constants.RESOURCE_CACHE;
import static edu.amherst.acdc.trellis.rosid.Constants.RESOURCE_JOURNAL;
import static edu.amherst.acdc.trellis.rosid.Constants.RESOURCE_QUADS;
import static edu.amherst.acdc.trellis.rosid.FileUtils.stringToQuad;
import static java.lang.String.join;
import static java.lang.System.lineSeparator;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.lines;
import static java.nio.file.Files.newBufferedWriter;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.time.Instant.now;
import static java.util.Optional.ofNullable;
import static java.util.stream.Stream.empty;
import static org.slf4j.LoggerFactory.getLogger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import edu.amherst.acdc.trellis.api.Resource;
import edu.amherst.acdc.trellis.api.VersionRange;
import edu.amherst.acdc.trellis.vocabulary.Trellis;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Quad;
import org.apache.commons.rdf.api.Triple;
import org.slf4j.Logger;

/**
 * An object that mediates access to the resource cache files.
 *
 * @author acoburn
 */
class CachedResource extends AbstractFileResource {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Logger LOGGER = getLogger(CachedResource.class);

    static {
        MAPPER.configure(WRITE_DATES_AS_TIMESTAMPS, false);
        MAPPER.registerModule(new JavaTimeModule());
    }

    /**
     * Create a File-based resource reader
     * @param directory the data storage directory
     * @param identifier the resource to retrieve
     */
    protected CachedResource(final File directory, final IRI identifier, final ResourceData data) {
        super(directory, identifier, data);
    }

    /**
     * Retrieve a cached resource, if it exists
     * @param directory the directory
     * @param identifier the identifier
     * @return the resource
     */
    public static Optional<Resource> find(final File directory, final IRI identifier) {
        ResourceData data = null;
        try {
            data = MAPPER.readValue(new File(directory, RESOURCE_CACHE), ResourceData.class);
        } catch (final IOException ex) {
            LOGGER.warn("Error reading cached resource: {}", ex.getMessage());
        }
        return ofNullable(data).map(d -> new CachedResource(directory, identifier, d));
    }

    /**
     * Write the resource data into a file as JSON
     * @param directory the directory
     * @param identifier the resource identifier
     */
    public static void write(final File directory, final IRI identifier) throws IOException {
        final Instant time = now();

        // Write the JSON file
        final Optional<ResourceData> data = VersionedResource.read(directory, identifier, time);
        MAPPER.writeValue(new File(directory, RESOURCE_CACHE), data.get());

        // Write the quads
        try (final BufferedWriter writer = newBufferedWriter(new File(directory, RESOURCE_QUADS).toPath(),
                    UTF_8, CREATE, WRITE, TRUNCATE_EXISTING)) {
            final File file = new File(directory, RESOURCE_JOURNAL);
            final Iterator<String> lineIter = RDFPatch.asStream(rdf, file, identifier, time)
                    .map(quad -> join(" ", quad.getSubject().ntriplesString(),
                        quad.getPredicate().ntriplesString(), quad.getObject().ntriplesString(),
                        quad.getGraphName().orElse(Trellis.PreferUserManaged).ntriplesString(), ".")).iterator();
            while (lineIter.hasNext()) {
                writer.write(lineIter.next() + lineSeparator());
            }
        }

        // Write the mementos
        try (final BufferedWriter writer = newBufferedWriter(new File(directory, MEMENTO_CACHE).toPath(),
                    UTF_8, CREATE, WRITE, TRUNCATE_EXISTING)) {
            final File file = new File(directory, RESOURCE_JOURNAL);
            final Iterator<VersionRange> iter = RDFPatch.asTimeMap(file).iterator();
            if (iter.hasNext()) {
                final VersionRange range = iter.next();
                writer.write(range.getFrom() + lineSeparator());
                writer.write(range.getUntil() + lineSeparator());
            }
            while (iter.hasNext()) {
                writer.write(iter.next().getUntil() + lineSeparator());
            }
        }
    }

    @Override
    public <T extends Resource.TripleCategory> Stream<Triple> stream(final Collection<T> category) {
        return Optional.of(new File(directory, RESOURCE_QUADS)).filter(File::exists).map(File::toPath)
            .map(uncheckedLines).orElse(empty()).map(line -> stringToQuad(rdf, line)).filter(Optional::isPresent)
            .map(Optional::get).filter(quad -> quad.getGraphName().isPresent() &&
                    category.contains(categorymap.get(quad.getGraphName().get()))).map(Quad::asTriple);
    }

    private Function<Path, Stream<String>> uncheckedLines = path -> {
        try {
            return lines(path);
        } catch (final IOException ex) {
            throw new UncheckedIOException(ex);
        }
    };
}
