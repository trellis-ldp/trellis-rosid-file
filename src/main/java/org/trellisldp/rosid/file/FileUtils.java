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
package org.trellisldp.rosid.file;

import static java.io.File.separator;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.of;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.IntStream.range;
import static org.apache.commons.codec.digest.DigestUtils.md5Hex;
import static org.apache.commons.rdf.jena.JenaRDF.asQuad;
import static org.apache.jena.riot.Lang.NQUADS;
import static org.apache.jena.riot.system.StreamRDFLib.sinkQuads;

import java.io.File;
import java.io.StringReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.StringJoiner;
import java.util.zip.CRC32;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Quad;
import org.apache.commons.rdf.api.RDF;
import org.apache.jena.atlas.lib.SinkToCollection;
import org.apache.jena.riot.RDFParserRegistry;
import org.apache.jena.riot.ReaderRIOT;

/**
 * @author acoburn
 */
final class FileUtils {

    // The length of the CRC directory partition
    public final static int LENGTH = 2;
    public final static int MAX = 3;

    private static final ReaderRIOT READER = RDFParserRegistry.getFactory(NQUADS).create(NQUADS);

    /**
     * Partition an identifier into a directory structure
     * @param identifier the identifier
     * @return a string usable as a directory path
     */
    public static String partition(final IRI identifier) {
        return partition(identifier.getIRIString());
    }

    /**
     * Partition an identifier into a directory structure
     * @param identifier the identifier
     * @return a string usable as a directory path
     */
    public static String partition(final String identifier) {
        requireNonNull(identifier, "identifier must not be null!");

        final StringJoiner joiner = new StringJoiner(separator);
        final CRC32 hasher = new CRC32();
        hasher.update(identifier.getBytes(UTF_8));
        final String intermediate = Long.toHexString(hasher.getValue());

        range(0, intermediate.length() / LENGTH).limit(MAX)
            .forEach(i -> joiner.add(intermediate.substring(i * LENGTH, (i + 1) * LENGTH)));

        joiner.add(md5Hex(identifier));
        return joiner.toString();
    }

    /**
     * Parse a string into a Quad
     * @param rdf the RDF object
     * @param line the line of text
     * @return the Quad
     */
    public static Optional<Quad> stringToQuad(final RDF rdf, final String line) {
        final List<org.apache.jena.sparql.core.Quad> c = new ArrayList<>();
        READER.read(new StringReader(line), null, NQUADS.getContentType(), sinkQuads(new SinkToCollection<>(c)), null);
        return of(c).filter(x -> !x.isEmpty()).map(x -> asQuad(rdf, x.get(0)));
    }

    /**
     * Get the resource directory for a given identifier
     * @param config the configuration
     * @param identifier the identifier
     * @return the file
     */
    public static File resourceDirectory(final Map<String, String> config, final IRI identifier) {
        return resourceDirectory(config, identifier.getIRIString());
    }

    /**
     * Get the resource directory for a given identifier
     * @param config the configuration
     * @param identifier the identifier
     * @return the file
     */
    public static File resourceDirectory(final Map<String, String> config, final String identifier) {
        final String repo = identifier.split("/")[0].split(":")[1];
        if (config.containsKey(repo)) {
            final File root;
            if (config.get(repo).startsWith("file:")) {
                root = new File(URI.create(config.get(repo)));
            } else {
                root = new File(config.get(repo));
            }
            final File directory = new File(root, partition(identifier));
            directory.mkdirs();
            return directory;
        }
        return null;
    }

    /**
     * Get the storage-related configuration values as a map
     * @param props the property values
     * @param suffix the suffix
     * @return the configuration values
     */
    public static Map<String, String> getStorageConfig(final Properties props, final String suffix) {
        // trellis.storage.repo1.resources
        // trellis.storage.repo1.blobs
        return props.stringPropertyNames().stream().filter(key -> key.endsWith(suffix))
            .collect(toMap(key -> key.split("\\.")[0], props::getProperty));
    }

    /**
     * Get a slice of the property configuration
     * @param configuration the configuration
     * @param prefix the prefix
     * @return a subset of the properties
     */
    public static Properties getPropertySection(final Properties configuration, final String prefix) {
        final Properties props = new Properties();
        configuration.stringPropertyNames().stream().filter(key -> key.startsWith(prefix))
            .forEach(key -> props.setProperty(key.substring(prefix.length()), configuration.getProperty(key)));
        return props;
    }

    private FileUtils() {
        // prevent instantiation
    }
}