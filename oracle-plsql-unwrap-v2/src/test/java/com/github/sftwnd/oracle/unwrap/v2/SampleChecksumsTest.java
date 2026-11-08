// SPDX-License-Identifier: BSD-3-Clause

package com.github.sftwnd.oracle.unwrap.v2;

import com.github.sftwnd.oracle.unwrap.spi.ReferenceChecksums;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The samples of V2 are as WRAP wrote them and the unwrapper restored them: every one has its listed checksum. */
class SampleChecksumsTest {

    @Test
    void samplesHaveTheirChecksums() throws URISyntaxException {
        Path samples = Paths.get(SampleChecksumsTest.class.getResource("/samples").toURI());
        assertEquals(Collections.emptyList(), ReferenceChecksums.mismatches(samples),
                "samples differ from samples/" + ReferenceChecksums.MANIFEST + " (see ReferenceChecksums)");
    }
}
