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

import static java.io.File.separator;
import static java.util.Objects.requireNonNull;
import static java.util.stream.IntStream.range;
import static org.apache.commons.codec.digest.DigestUtils.md5Hex;

import java.util.StringJoiner;
import java.util.zip.CRC32;

import org.apache.commons.rdf.api.IRI;

/**
 * @author acoburn
 */
class FileUtils {

    // The length of the CRC directory partition
    public final static int LENGTH = 2;

    public static String partition(final IRI identifier) {
        requireNonNull(identifier, "identifier must not be null!");

        final StringJoiner joiner = new StringJoiner(separator);
        final CRC32 hasher = new CRC32();
        hasher.update(identifier.getIRIString().getBytes());
        final String intermediate = Long.toHexString(hasher.getValue());

        final int count = intermediate.length() / LENGTH;

        range(0, count).forEach(i -> joiner.add(intermediate.substring(i * LENGTH, (i + 1) * LENGTH)));

        joiner.add(md5Hex(identifier.getIRIString()));
        return joiner.toString();
    }

    private FileUtils() {
        // prevent instantiation
    }
}
