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
    }

    @Test
    public void testParseReportVersionOne() throws Exception {
        var options = CodegenCliParser.parse(new String[] {
            "--grammar", "a.ubnf",
            "--validate-only",
            "--report-format", "json",
            "--report-version", "1"
        });

        assertEquals("json", options.reportFormat());
        assertEquals(1, options.reportVersion());
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
