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
package edu.amherst.acdc.trellis.rosid.file;

import static edu.amherst.acdc.trellis.rosid.common.RDFUtils.getInstance;
import static edu.amherst.acdc.trellis.rosid.file.Constants.RESOURCE_JOURNAL;
import static edu.amherst.acdc.trellis.rosid.file.FileUtils.resourceDirectory;
import static edu.amherst.acdc.trellis.vocabulary.Fedora.PreferInboundReferences;
import static edu.amherst.acdc.trellis.vocabulary.LDP.DirectContainer;
import static edu.amherst.acdc.trellis.vocabulary.LDP.MemberSubject;
import static edu.amherst.acdc.trellis.vocabulary.LDP.PreferContainment;
import static edu.amherst.acdc.trellis.vocabulary.LDP.PreferMembership;
import static edu.amherst.acdc.trellis.vocabulary.Trellis.PreferUserManaged;
import static java.time.Instant.now;
import static java.util.Optional.of;
import static java.util.stream.Stream.empty;
import static org.apache.kafka.streams.KeyValue.pair;
import static org.slf4j.LoggerFactory.getLogger;

import edu.amherst.acdc.trellis.api.Resource;

import java.io.File;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.commons.rdf.api.Dataset;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Quad;
import org.apache.commons.rdf.api.RDF;
import org.apache.commons.rdf.api.Triple;
import org.apache.kafka.streams.KeyValue;
import org.slf4j.Logger;

/**
 * @author acoburn
 */
final class StreamProcessing {

    private static final Logger LOGGER = getLogger(StreamProcessing.class);

    private static final RDF rdf = getInstance();

    private static Predicate<Quad> isContainerQuad = q ->
        q.getGraphName().filter(n -> PreferContainment.equals(n) || PreferMembership.equals(n)).isPresent();

    /**
     * A mapping function for updating LDP Membership properties
     * @param config the storage configuration
     * @param key the key
     * @param value the value
     * @return a new key-value pair
     */
    public static KeyValue<String, Dataset> addMembershipQuads(final Map<String, String> config, final String key,
            final Dataset value) {

        final Instant time = now();
        final Optional<Resource> resource = CachedResource.find(resourceDirectory(config, key), rdf.createIRI(key))
            .filter(res -> res.getInsertedContentRelation().isPresent())
            .filter(res -> res.getMembershipResource().isPresent())
            .filter(res -> res.getMemberRelation().isPresent());

        if (resource.isPresent()) {
            final IRI model = resource.get().getInteractionModel();
            final IRI identifier = resource.get().getMembershipResource().get();
            final IRI relation = resource.get().getMemberRelation().get();
            final IRI insertedContent = resource.get().getInsertedContentRelation().get();
            final Stream<IRI> adding = value.getGraph(PreferContainment)
                    .map(g -> g.stream().map(Triple::getObject)).orElse(empty())
                    .filter(iri -> iri instanceof IRI).map(iri -> (IRI) iri);
            final Stream<Quad> addMembers;
            if (DirectContainer.equals(model) || MemberSubject.equals(insertedContent)) {
                addMembers = adding.map(iri ->
                        rdf.createQuad(PreferMembership, identifier, relation, iri));
            } else {
                addMembers = adding.flatMap(iri -> value.stream(
                    of(PreferUserManaged), iri, resource.get().getInsertedContentRelation().get(), null)
                        .map(quad -> rdf.createQuad(PreferMembership, identifier, relation, quad.getObject())));
            }

            final File file = new File(resourceDirectory(config, identifier), RESOURCE_JOURNAL);
            if (RDFPatch.write(file, empty(), addMembers, time)) {
                return pair(identifier.getIRIString(), value);
            }
            LOGGER.error("Error adding LDP membership triples to {}", identifier.getIRIString());
        }
        return pair(key, rdf.createDataset());

    }

    /**
     * A mapping function for deleting LDP Membership properties
     * @param config the storage configuration
     * @param key the key
     * @param value the value
     * @return a new key-value pair
     */
    public static KeyValue<String, Dataset> deleteMembershipQuads(final Map<String, String> config, final String key,
            final Dataset value) {
        final Instant time = now();
        final Optional<Resource> resource = CachedResource.find(resourceDirectory(config, key), rdf.createIRI(key))
            .filter(res -> res.getInsertedContentRelation().isPresent())
            .filter(res -> res.getMembershipResource().isPresent())
            .filter(res -> res.getMemberRelation().isPresent());

        if (resource.isPresent()) {
            final IRI model = resource.get().getInteractionModel();
            final IRI identifier = resource.get().getMembershipResource().get();
            final IRI relation = resource.get().getMemberRelation().get();
            final IRI insertedContent = resource.get().getInsertedContentRelation().get();
            final Stream<IRI> adding = value.getGraph(PreferContainment)
                    .map(g -> g.stream().map(Triple::getObject)).orElse(empty())
                    .filter(iri -> iri instanceof IRI).map(iri -> (IRI) iri);

            final Stream<Quad> deleteMembers;
            if (DirectContainer.equals(model) || MemberSubject.equals(insertedContent)) {
                deleteMembers = adding.map(iri ->
                        rdf.createQuad(PreferMembership, identifier, relation, iri));
            } else {
                deleteMembers = adding.flatMap(iri -> value.stream(
                    of(PreferUserManaged), iri, resource.get().getInsertedContentRelation().get(), null)
                        .map(quad -> rdf.createQuad(PreferMembership, identifier, relation, quad.getObject())));
            }

            final File file = new File(resourceDirectory(config, identifier), RESOURCE_JOURNAL);
            if (RDFPatch.write(file, deleteMembers, empty(), time)) {
                return pair(identifier.getIRIString(), value);
            }
            LOGGER.error("Error adding LDP membership triples to {}", identifier.getIRIString());
        }

        return pair(key, rdf.createDataset());
    }

    /**
     * A mapping function for updating LDP Container properties
     * @param config the storage configuration
     * @param key the key
     * @param value the value
     * @return a new key-value pair
     */
    public static KeyValue<String, Dataset> addContainmentQuads(final Map<String, String> config, final String key,
            final Dataset value) {
        final File file = new File(resourceDirectory(config, key), RESOURCE_JOURNAL);
        if (!RDFPatch.write(file, empty(), value.stream().filter(isContainerQuad), now())) {
            LOGGER.error("Error adding LDP container triples to {}", key);
        }
        return pair(key, value);
    }

    /**
     * A mapping function for deleting LDP Container properties
     * @param config the storage configuration
     * @param key the key
     * @param value the value
     * @return a new key-value pair
     */
    public static KeyValue<String, Dataset> deleteContainmentQuads(final Map<String, String> config, final String key,
            final Dataset value) {
        final File file = new File(resourceDirectory(config, key), RESOURCE_JOURNAL);
        if (!RDFPatch.write(file, value.stream().filter(isContainerQuad), empty(), now())) {
            LOGGER.error("Error removing LDP container triples from {}", key);
        }
        return pair(key, value);
    }

    /**
     * A mapping function for updating the resource cache
     * @param config the storage configuration
     * @param key the key
     * @param value the value
     * @return a new key-value pair
     */
    public static KeyValue<String, Dataset> writeCacheQuads(final Map<String, String> config, final String key,
            final Dataset value) {
        if (!CachedResource.write(resourceDirectory(config, key), key)) {
            LOGGER.error("Error writing cache for {}", key);
        }
        return pair(key, value);
    }

    /**
     * A processing function for adding inbound refs
     * @param config the storage configuration
     * @param key the key
     * @param value the value
     */
    public static void addInboundQuads(final Map<String, String> config, final String key,
            final Dataset value) {
        final File file = new File(resourceDirectory(config, key), RESOURCE_JOURNAL);
        if (!RDFPatch.write(file, empty(), value.stream(of(PreferInboundReferences), null, null, null), now())) {
            LOGGER.error("Error adding inbound reference triples to {}", key);
        }
    }

    /**
     * A processing function for deleting inbound refs
     * @param config the storage configuration
     * @param key the key
     * @param value the value
     */
    public static void deleteInboundQuads(final Map<String, String> config, final String key, final Dataset value) {
        final File file = new File(resourceDirectory(config, key), RESOURCE_JOURNAL);
        if (RDFPatch.write(file, value.stream(of(PreferInboundReferences), null, null, null), empty(), now())) {
            LOGGER.error("Error removing inbound reference triples from {}", key);
        }
    }

    private StreamProcessing() {
        // prevent instantiation
    }
}
