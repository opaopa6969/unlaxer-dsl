package org.unlaxer.dsl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

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
        assertFalse(options.dryRun());
        assertFalse(options.cleanOutput());
        assertFalse(options.strict());
        assertFalse(options.reportSchemaCheck());
        assertFalse(options.warningsAsJson());
        assertEquals("always", options.overwrite());
        assertFalse(options.help());
        assertFalse(options.version());
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
        assertFalse(options.strict());
        assertTrue(options.reportSchemaCheck());
    }

    @Test
    public void testParseStrictOption() throws Exception {
        var options = CodegenCliParser.parse(new String[] {
            "--grammar", "a.ubnf",
            "--validate-only",
            "--strict"
        });
        assertTrue(options.strict());
        assertFalse(options.warningsAsJson());
    }

    @Test
    public void testParseDryRunAndOverwritePolicy() throws Exception {
        var options = CodegenCliParser.parse(new String[] {
            "--grammar", "a.ubnf",
            "--output", "out",
            "--dry-run",
            "--overwrite", "if-different"
        });
        assertTrue(options.dryRun());
        assertEquals("if-different", options.overwrite());
    }

    @Test
    public void testParseCleanOutputAndNdjsonFormat() throws Exception {
        var options = CodegenCliParser.parse(new String[] {
            "--grammar", "a.ubnf",
            "--output", "out",
            "--clean-output",
            "--report-format", "ndjson"
        });
        assertTrue(options.cleanOutput());
        assertEquals("ndjson", options.reportFormat());
    }

    @Test
    public void testParseWarningsAsJsonOption() throws Exception {
        var options = CodegenCliParser.parse(new String[] {
            "--grammar", "a.ubnf",
            "--validate-only",
            "--warnings-as-json"
        });
        assertTrue(options.warningsAsJson());
    }

    @Test
    public void testRejectUnsupportedOverwritePolicy() {
        try {
            CodegenCliParser.parse(new String[] {
                "--grammar", "a.ubnf",
                "--output", "out",
                "--overwrite", "sometimes"
            });
            fail("expected parser usage error");
        } catch (CodegenCliParser.UsageException e) {
            assertFalse(e.showUsage());
            assertTrue(e.getMessage().contains("Unsupported --overwrite"));
        }
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
    public void testParseGeneratorsTrimsWhitespaceAndDropsEmptyEntries() throws Exception {
        var options = CodegenCliParser.parse(new String[] {
            "--grammar", "a.ubnf",
            "--output", "out",
            "--generators", "AST, LSP, , Parser"
        });

        assertEquals(List.of("AST", "LSP", "Parser"), options.generators());
    }

    @Test
    public void testRejectEmptyGeneratorsValue() {
        try {
            CodegenCliParser.parse(new String[] {
                "--grammar", "a.ubnf",
                "--output", "out",
                "--generators", " ,  , "
            });
            fail("expected parser usage error");
        } catch (CodegenCliParser.UsageException e) {
            assertFalse(e.showUsage());
            assertTrue(e.getMessage().contains("No generators specified"));
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

    @Test
    public void testParseHelpWithoutGrammarOrOutput() throws Exception {
        var options = CodegenCliParser.parse(new String[] {"--help"});
        assertTrue(options.help());
        assertFalse(options.version());
    }

    @Test
    public void testParseVersionWithoutGrammarOrOutput() throws Exception {
        var options = CodegenCliParser.parse(new String[] {"--version"});
        assertTrue(options.version());
        assertFalse(options.help());
    }
}
