package org.unlaxer.dsl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class CodegenCliParserTest {

    @Test
    public void testParseValidateOnlyDefaults() throws Exception {
        var options = CodegenCliParser.parse(new String[] {
            "--grammar", "a.ubnf",
            "--validate-only"
        });

        assertEquals("a.ubnf", options.grammarFile());
        assertEquals("text", options.reportFormat());
        assertEquals(1, options.reportVersion());
        assertTrue(options.validateOnly());
        assertFalse(options.reportSchemaCheck());
    }

    @Test
    public void testParseReportVersionOne() throws Exception {
        var options = CodegenCliParser.parse(new String[] {
            "--grammar", "a.ubnf",
            "--validate-only",
            "--report-format", "json",
            "--report-version", "1",
            "--report-schema-check"
        });

        assertEquals("json", options.reportFormat());
        assertEquals(1, options.reportVersion());
        assertTrue(options.reportSchemaCheck());
    }

    @Test
    public void testRejectUnsupportedReportVersion() {
        try {
            CodegenCliParser.parse(new String[] {
                "--grammar", "a.ubnf",
                "--validate-only",
                "--report-version", "2"
            });
            fail("expected parser usage error");
        } catch (CodegenCliParser.UsageException e) {
            assertFalse(e.showUsage());
            assertTrue(e.getMessage().contains("Unsupported --report-version"));
        }
    }

    @Test
    public void testRejectMissingReportVersionValue() {
        try {
            CodegenCliParser.parse(new String[] {
                "--grammar", "a.ubnf",
                "--validate-only",
                "--report-version"
            });
            fail("expected parser usage error");
        } catch (CodegenCliParser.UsageException e) {
            assertTrue(e.showUsage());
            assertTrue(e.getMessage().contains("Missing value for --report-version"));
        }
    }

    @Test
    public void testRejectUnsupportedReportFormat() {
        try {
            CodegenCliParser.parse(new String[] {
                "--grammar", "a.ubnf",
                "--validate-only",
                "--report-format", "yaml"
            });
            fail("expected parser usage error");
        } catch (CodegenCliParser.UsageException e) {
            assertFalse(e.showUsage());
            assertTrue(e.getMessage().contains("Unsupported --report-format"));
        }
    }

    @Test
    public void testRejectUnknownArgument() {
        try {
            CodegenCliParser.parse(new String[] {
                "--grammar", "a.ubnf",
                "--validate-only",
                "--report-schema-check=true"
            });
            fail("expected parser usage error");
        } catch (CodegenCliParser.UsageException e) {
            assertTrue(e.showUsage());
            assertTrue(e.getMessage().contains("Unknown argument"));
        }
    }

    @Test
    public void testRejectMissingOutputWithoutValidateOnly() {
        try {
            CodegenCliParser.parse(new String[] {
                "--grammar", "a.ubnf"
            });
            fail("expected parser usage error");
        } catch (CodegenCliParser.UsageException e) {
            assertTrue(e.showUsage());
        }
    }
}
