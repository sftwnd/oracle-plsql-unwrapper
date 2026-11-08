// SPDX-License-Identifier: BSD-3-Clause

package com.github.sftwnd.oracle.unwrap;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The distribution with the format V2 only. */
class UnwrapperLaunchIT extends AbstractUnwrapperLaunchIT {

    /** The license of this project: the module directory is the working directory of the tests. */
    private static final Path LICENSE = Paths.get("..", "LICENSE");

    @Override
    protected String sample() {
        return "P1020EDGE";
    }

    @Override
    protected String format() {
        return "V2";
    }

    @Override
    protected void assertHelp(String help) {
        assertTrue(help.contains("  V2  Oracle 10g and later\n"), help);
        assertFalse(help.contains("V1"), help);
    }

    /**
     * The executable jar and the distribution hold the license of this project as it is: no text of the build, such as
     * its time, which would make every build differ.
     */
    @Test
    void licenseIsTheOneOfThisProject() throws IOException {
        byte[] expected = Files.readAllBytes(LICENSE);
        try (JarFile jar = new JarFile(System.getProperty("executable.jar"))) {
            ZipEntry entry = jar.getEntry("META-INF/LICENSE");
            assertNotNull(entry, "META-INF/LICENSE");
            try (InputStream in = jar.getInputStream(entry)) {
                assertArrayEquals(expected, readAll(in), "META-INF/LICENSE of the executable jar");
            }
        }
        Path distribution = Paths.get(System.getProperty("distribution.directory"));
        assertArrayEquals(expected, Files.readAllBytes(distribution.resolve("LICENSE")), "LICENSE of the distribution");
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int n = in.read(buffer); n >= 0; n = in.read(buffer)) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }
}
