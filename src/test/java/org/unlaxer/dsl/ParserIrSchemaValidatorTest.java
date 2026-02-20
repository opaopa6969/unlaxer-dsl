package org.unlaxer.dsl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class ParserIrSchemaValidatorTest {

    @Test
    public void testAcceptsValidMinimalFixture() throws Exception {
        String payload = loadFixture("valid-minimal.json");
        ParserIrSchemaValidator.validate(payload);
    }

    @Test
    public void testRejectsBlankSourceFixture() throws Exception {
        String payload = loadFixture("invalid-source-blank.json");
        try {
            ParserIrSchemaValidator.validate(payload);
            fail("expected parser ir validation error");
        } catch (ReportSchemaValidationException expected) {
            assertEquals("E-PARSER-IR-CONSTRAINT", expected.code());
            assertTrue(expected.getMessage().contains("source must not be blank"));
        }
    }

    @Test
    public void testRejectsBrokenJson() {
        try {
            ParserIrSchemaValidator.validate("{");
            fail("expected parser ir parse error");
        } catch (ReportSchemaValidationException expected) {
            assertEquals("E-PARSER-IR-PARSE", expected.code());
        }
    }

    private static String loadFixture(String name) throws Exception {
        return Files.readString(Path.of("src/test/resources/schema/parser-ir").resolve(name));
    }
}
