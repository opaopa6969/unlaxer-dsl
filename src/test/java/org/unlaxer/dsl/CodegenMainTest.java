package org.unlaxer.dsl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

public class CodegenMainTest {

    @Test
    public void testGeneratesAllGrammarBlocks() throws Exception {
        String source = """
            grammar First {
              @package: org.example.first
              @root
              @mapping(RootNode, params=[value])
              First ::= 'a' @value ;
            }

            grammar Second {
              @package: org.example.second
              @root
              @mapping(RootNode, params=[value])
              Second ::= 'b' @value ;
            }
            """;

        Path grammarFile = Files.createTempFile("codegen-main", ".ubnf");
        Path outputDir = Files.createTempDirectory("codegen-main-out");
        Files.writeString(grammarFile, source);

        RunResult result = runCodegen(
            "--grammar", grammarFile.toString(),
            "--output", outputDir.toString(),
            "--generators", "AST"
        );

        assertEquals(CodegenMain.EXIT_OK, result.exitCode());
        assertTrue(result.err().isEmpty());

        Path firstAst = outputDir.resolve("org/example/first/FirstAST.java");
        Path secondAst = outputDir.resolve("org/example/second/SecondAST.java");

        assertTrue(Files.exists(firstAst));
        assertTrue(Files.exists(secondAst));
    }

    @Test
    public void testFailsOnInvalidMappingContract() throws Exception {
        String source = """
            grammar Invalid {
              @package: org.example.invalid
              @root
              @mapping(RootNode, params=[value, missing])
              Invalid ::= 'x' @value ;
            }
            """;

        Path grammarFile = Files.createTempFile("codegen-main-invalid", ".ubnf");
        Path outputDir = Files.createTempDirectory("codegen-main-invalid-out");
        Files.writeString(grammarFile, source);

        RunResult result = runCodegen(
            "--grammar", grammarFile.toString(),
            "--output", outputDir.toString(),
            "--generators", "AST"
        );

        assertEquals(CodegenMain.EXIT_VALIDATION_ERROR, result.exitCode());
        assertTrue(result.err().contains("has no matching capture"));
    }

    @Test
    public void testFailsOnNonCanonicalRightAssoc() throws Exception {
        String source = """
            grammar InvalidRightAssoc {
              @package: org.example.invalid
              @root
              @mapping(PowNode, params=[left, op, right])
              @rightAssoc
              @precedence(level=30)
              Expr ::= Atom @left { '^' @op Atom @right } ;
              Atom ::= 'n' ;
            }
            """;

        Path grammarFile = Files.createTempFile("codegen-main-invalid-rightassoc", ".ubnf");
        Path outputDir = Files.createTempDirectory("codegen-main-invalid-rightassoc-out");
        Files.writeString(grammarFile, source);

        RunResult result = runCodegen(
            "--grammar", grammarFile.toString(),
            "--output", outputDir.toString(),
            "--generators", "Parser"
        );

        assertEquals(CodegenMain.EXIT_VALIDATION_ERROR, result.exitCode());
        assertTrue(result.err().contains("body is not canonical"));
    }

    @Test
    public void testAggregatesValidationErrorsAcrossGrammarBlocks() throws Exception {
        String source = """
            grammar InvalidA {
              @package: org.example.invalid
              @root
              @mapping(RootNode, params=[value, missing])
              A ::= 'x' @value ;
            }

            grammar InvalidB {
              @package: org.example.invalid
              @root
              @mapping(PowNode, params=[left, op, right])
              @rightAssoc
              @precedence(level=30)
              Expr ::= Atom @left { '^' @op Atom @right } ;
              Atom ::= 'n' ;
            }
            """;

        Path grammarFile = Files.createTempFile("codegen-main-invalid-multi", ".ubnf");
        Path outputDir = Files.createTempDirectory("codegen-main-invalid-multi-out");
        Files.writeString(grammarFile, source);

        RunResult result = runCodegen(
            "--grammar", grammarFile.toString(),
            "--output", outputDir.toString(),
            "--generators", "Parser"
        );

        assertEquals(CodegenMain.EXIT_VALIDATION_ERROR, result.exitCode());
        assertTrue(result.err().contains("grammar InvalidA:"));
        assertTrue(result.err().contains("grammar InvalidB:"));
        assertTrue(result.err().contains("[code:"));
    }

    @Test
    public void testValidateOnlySkipsGeneration() throws Exception {
        String source = """
            grammar Valid {
              @package: org.example.valid
              @root
              @mapping(RootNode, params=[value])
              Valid ::= 'ok' @value ;
            }
            """;

        Path grammarFile = Files.createTempFile("codegen-main-validate-only", ".ubnf");
        Path outputDir = Files.createTempDirectory("codegen-main-validate-only-out");
        Files.writeString(grammarFile, source);

        RunResult result = runCodegen(
            "--grammar", grammarFile.toString(),
            "--validate-only"
        );

        assertEquals(CodegenMain.EXIT_OK, result.exitCode());

        Path ast = outputDir.resolve("org/example/valid/ValidAST.java");
        assertTrue(!Files.exists(ast));
    }

    @Test
    public void testValidateOnlyStillFailsOnInvalidGrammar() throws Exception {
        String source = """
            grammar Invalid {
              @package: org.example.invalid
              @root
              @mapping(RootNode, params=[value, missing])
              Invalid ::= 'x' @value ;
            }
            """;

        Path grammarFile = Files.createTempFile("codegen-main-validate-only-invalid", ".ubnf");
        Files.writeString(grammarFile, source);

        RunResult result = runCodegen(
            "--grammar", grammarFile.toString(),
            "--validate-only"
        );

        assertEquals(CodegenMain.EXIT_VALIDATION_ERROR, result.exitCode());
        assertTrue(result.err().contains("Grammar validation failed"));
        assertTrue(result.err().contains("E-MAPPING-MISSING-CAPTURE"));
    }

    @Test
    public void testValidateOnlyJsonSuccessReport() throws Exception {
        String source = """
            grammar Valid {
              @package: org.example.valid
              @root
              @mapping(RootNode, params=[value])
              Valid ::= 'ok' @value ;
            }
            """;

        Path grammarFile = Files.createTempFile("codegen-main-validate-only-json", ".ubnf");
        Files.writeString(grammarFile, source);

        RunResult result = runCodegen(
            "--grammar", grammarFile.toString(),
            "--validate-only",
            "--report-format", "json"
        );

        assertEquals(CodegenMain.EXIT_OK, result.exitCode());
        String out = result.out().trim();
        assertTrue(out.startsWith("{\"reportVersion\":1,"));
        assertTrue(out.contains("\"schemaVersion\":\"1.0\""));
        assertTrue(out.contains("\"schemaUrl\":\"https://unlaxer.dev/schema/report-v1.json\""));
        assertTrue(out.contains("\"toolVersion\":\""));
        assertTrue(out.contains("\"argsHash\":\""));
        assertTrue(out.contains("\"generatedAt\":\""));
        assertHasNonEmptyJsonField(out, "toolVersion");
        assertGeneratedAtIsIsoInstant(out);
        assertTrue(out.contains("\"mode\":\"validate\""));
        assertTrue(out.contains("\"ok\":true"));
        assertTrue(out.contains("\"grammarCount\":1"));
        assertTrue(out.contains("\"warningsCount\":0"));
        assertTrue(out.endsWith("\"issues\":[]}"));

        Map<String, Object> obj = JsonTestUtil.parseObject(out);
        assertEquals(1L, JsonTestUtil.getLong(obj, "reportVersion"));
        assertEquals("1.0", JsonTestUtil.getString(obj, "schemaVersion"));
        assertEquals("https://unlaxer.dev/schema/report-v1.json", JsonTestUtil.getString(obj, "schemaUrl"));
        assertHasNonEmptyJsonField(out, "argsHash");
        assertEquals("validate", JsonTestUtil.getString(obj, "mode"));
        assertTrue(JsonTestUtil.getBoolean(obj, "ok"));
        assertEquals(1L, JsonTestUtil.getLong(obj, "grammarCount"));
        assertEquals(0L, JsonTestUtil.getLong(obj, "warningsCount"));
        assertEquals(List.of(), JsonTestUtil.getArray(obj, "issues"));
    }

    @Test
    public void testValidateOnlyJsonFailureReport() throws Exception {
        String source = """
            grammar Invalid {
              @package: org.example.invalid
              @root
              @mapping(RootNode, params=[value, missing])
              Invalid ::= 'x' @value ;
            }
            """;

        Path grammarFile = Files.createTempFile("codegen-main-validate-only-json-invalid", ".ubnf");
        Files.writeString(grammarFile, source);

        RunResult result = runCodegen(
            "--grammar", grammarFile.toString(),
            "--validate-only",
            "--report-format", "json"
        );

        assertEquals(CodegenMain.EXIT_VALIDATION_ERROR, result.exitCode());
        String msg = result.err().trim();
        assertTrue(msg.startsWith("{\"reportVersion\":1,"));
        assertTrue(msg.contains("\"schemaVersion\":\"1.0\""));
        assertTrue(msg.contains("\"schemaUrl\":\"https://unlaxer.dev/schema/report-v1.json\""));
        assertTrue(msg.contains("\"toolVersion\":\""));
        assertTrue(msg.contains("\"argsHash\":\""));
        assertTrue(msg.contains("\"generatedAt\":\""));
        assertHasNonEmptyJsonField(msg, "toolVersion");
        assertGeneratedAtIsIsoInstant(msg);
        assertTrue(msg.contains("\"mode\":\"validate\""));
        assertTrue(msg.contains("\"ok\":false"));
        assertTrue(msg.contains("\"warningsCount\":0"));
        assertTrue(msg.contains("\"severityCounts\":{\"ERROR\":1}"));
        assertTrue(msg.contains("\"categoryCounts\":{\"MAPPING\":1}"));
        assertTrue(msg.contains("\"grammar\":\"Invalid\""));
        assertTrue(msg.contains("\"rule\":\"Invalid\""));
        assertTrue(msg.contains("\"code\":\"E-MAPPING-MISSING-CAPTURE\""));
        assertTrue(msg.contains("\"severity\":\"ERROR\""));
        assertTrue(msg.contains("\"category\":\"MAPPING\""));
        assertTrue(msg.contains("\"issues\":["));

        Map<String, Object> obj = JsonTestUtil.parseObject(msg);
        assertEquals(1L, JsonTestUtil.getLong(obj, "reportVersion"));
        assertEquals("1.0", JsonTestUtil.getString(obj, "schemaVersion"));
        assertEquals("https://unlaxer.dev/schema/report-v1.json", JsonTestUtil.getString(obj, "schemaUrl"));
        assertHasNonEmptyJsonField(msg, "argsHash");
        assertEquals("validate", JsonTestUtil.getString(obj, "mode"));
        assertFalse(JsonTestUtil.getBoolean(obj, "ok"));
        assertEquals(1L, JsonTestUtil.getLong(obj, "issueCount"));
        assertEquals(0L, JsonTestUtil.getLong(obj, "warningsCount"));
        Map<String, Object> severityCounts = JsonTestUtil.getObject(obj, "severityCounts");
        assertEquals(1L, JsonTestUtil.getLong(severityCounts, "ERROR"));
        List<Object> issues = JsonTestUtil.getArray(obj, "issues");
        assertEquals(1, issues.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> issue = (Map<String, Object>) issues.get(0);
        assertEquals("E-MAPPING-MISSING-CAPTURE", JsonTestUtil.getString(issue, "code"));
        assertEquals("ERROR", JsonTestUtil.getString(issue, "severity"));
        assertEquals("MAPPING", JsonTestUtil.getString(issue, "category"));
    }

    @Test
    public void testValidateOnlyJsonFailureReportIsSortedByGrammar() throws Exception {
        String source = """
            grammar InvalidB {
              @package: org.example.invalid
              @root
              @mapping(PowNode, params=[left, op, right])
              @rightAssoc
              @precedence(level=30)
              Expr ::= Atom @left { '^' @op Atom @right } ;
              Atom ::= 'n' ;
            }

            grammar InvalidA {
              @package: org.example.invalid
              @root
              @mapping(RootNode, params=[value, missing])
              Invalid ::= 'x' @value ;
            }
            """;

        Path grammarFile = Files.createTempFile("codegen-main-validate-only-json-invalid-sort", ".ubnf");
        Files.writeString(grammarFile, source);

        RunResult result = runCodegen(
            "--grammar", grammarFile.toString(),
            "--validate-only",
            "--report-format", "json"
        );

        assertEquals(CodegenMain.EXIT_VALIDATION_ERROR, result.exitCode());
        String msg = result.err();
        int idxA = msg.indexOf("\"grammar\":\"InvalidA\"");
        int idxB = msg.indexOf("\"grammar\":\"InvalidB\"");
        assertTrue(idxA >= 0);
        assertTrue(idxB >= 0);
        assertTrue(idxA < idxB);
    }

    @Test
    public void testValidateOnlyJsonFailureReportIncludesAggregateCounts() throws Exception {
        String source = """
            grammar Invalid {
              @package: org.example.invalid
              @root
              @mapping(RootNode, params=[value, missing])
              @whitespace(custom)
              Invalid ::= 'x' @value ;
            }
            """;

        Path grammarFile = Files.createTempFile("codegen-main-validate-only-json-invalid-counts", ".ubnf");
        Files.writeString(grammarFile, source);

        RunResult result = runCodegen(
            "--grammar", grammarFile.toString(),
            "--validate-only",
            "--report-format", "json"
        );

        assertEquals(CodegenMain.EXIT_VALIDATION_ERROR, result.exitCode());
        String msg = result.err();
        assertTrue(msg.contains("\"issueCount\":2"));
        assertTrue(msg.contains("\"severityCounts\":{\"ERROR\":2}"));
        assertTrue(msg.contains("\"categoryCounts\":{\"MAPPING\":1,\"WHITESPACE\":1}"));
    }

    @Test
    public void testValidateOnlyJsonWritesReportFile() throws Exception {
        String source = """
            grammar Valid {
              @package: org.example.valid
              @root
              @mapping(RootNode, params=[value])
              Valid ::= 'ok' @value ;
            }
            """;

        Path grammarFile = Files.createTempFile("codegen-main-validate-only-json-file", ".ubnf");
        Path reportFile = Files.createTempFile("codegen-main-report", ".json");
        Files.writeString(grammarFile, source);

        RunResult result = runCodegen(
            "--grammar", grammarFile.toString(),
            "--validate-only",
            "--report-format", "json",
            "--report-file", reportFile.toString()
        );

        assertEquals(CodegenMain.EXIT_OK, result.exitCode());
        String report = Files.readString(reportFile).trim();
        assertTrue(report.startsWith("{\"reportVersion\":1,"));
        assertTrue(report.contains("\"schemaVersion\":\"1.0\""));
        assertTrue(report.contains("\"schemaUrl\":\"https://unlaxer.dev/schema/report-v1.json\""));
        assertTrue(report.contains("\"toolVersion\":\""));
        assertTrue(report.contains("\"argsHash\":\""));
        assertTrue(report.contains("\"generatedAt\":\""));
        assertHasNonEmptyJsonField(report, "toolVersion");
        assertGeneratedAtIsIsoInstant(report);
        assertTrue(report.contains("\"mode\":\"validate\""));
        assertTrue(report.contains("\"ok\":true"));
        assertTrue(report.contains("\"grammarCount\":1"));
        assertTrue(report.contains("\"warningsCount\":0"));
        assertTrue(report.endsWith("\"issues\":[]}"));
    }

    @Test
    public void testGenerationJsonReportIncludesGeneratedFiles() throws Exception {
        String source = """
            grammar Valid {
              @package: org.example.valid
              @root
              @mapping(RootNode, params=[value])
              Valid ::= 'ok' @value ;
            }
            """;

        Path grammarFile = Files.createTempFile("codegen-main-generate-json", ".ubnf");
        Path outputDir = Files.createTempDirectory("codegen-main-generate-json-out");
        Path reportFile = Files.createTempFile("codegen-main-generate-report", ".json");
        Files.writeString(grammarFile, source);

        RunResult result = runCodegen(
            "--grammar", grammarFile.toString(),
            "--output", outputDir.toString(),
            "--generators", "AST",
            "--report-format", "json",
            "--report-file", reportFile.toString()
        );

        assertEquals(CodegenMain.EXIT_OK, result.exitCode());
        String report = Files.readString(reportFile);
        assertTrue(report.contains("\"ok\":true"));
        assertTrue(report.contains("\"reportVersion\":1"));
        assertTrue(report.contains("\"schemaVersion\":\"1.0\""));
        assertTrue(report.contains("\"schemaUrl\":\"https://unlaxer.dev/schema/report-v1.json\""));
        assertTrue(report.contains("\"toolVersion\":\""));
        assertTrue(report.contains("\"argsHash\":\""));
        assertTrue(report.contains("\"generatedAt\":\""));
        assertHasNonEmptyJsonField(report, "toolVersion");
        assertGeneratedAtIsIsoInstant(report);
        assertTrue(report.contains("\"mode\":\"generate\""));
        assertTrue(report.contains("\"generatedCount\":1"));
        assertTrue(report.contains("\"warningsCount\":0"));
        assertTrue(report.contains("\"writtenCount\":1"));
        assertTrue(report.contains("\"skippedCount\":0"));
        assertTrue(report.contains("\"conflictCount\":0"));
        assertTrue(report.contains("\"dryRunCount\":0"));
        assertTrue(report.contains("\"generatedFiles\":["));
        assertTrue(report.contains("ValidAST.java"));

        Map<String, Object> obj = JsonTestUtil.parseObject(report);
        assertEquals(1L, JsonTestUtil.getLong(obj, "reportVersion"));
        assertEquals("1.0", JsonTestUtil.getString(obj, "schemaVersion"));
        assertEquals("https://unlaxer.dev/schema/report-v1.json", JsonTestUtil.getString(obj, "schemaUrl"));
        assertHasNonEmptyJsonField(report, "argsHash");
        assertEquals("generate", JsonTestUtil.getString(obj, "mode"));
        assertTrue(JsonTestUtil.getBoolean(obj, "ok"));
        assertEquals(1L, JsonTestUtil.getLong(obj, "generatedCount"));
        assertEquals(0L, JsonTestUtil.getLong(obj, "warningsCount"));
        assertEquals(1L, JsonTestUtil.getLong(obj, "writtenCount"));
        assertEquals(0L, JsonTestUtil.getLong(obj, "skippedCount"));
        assertEquals(0L, JsonTestUtil.getLong(obj, "conflictCount"));
        assertEquals(0L, JsonTestUtil.getLong(obj, "dryRunCount"));
        List<Object> files = JsonTestUtil.getArray(obj, "generatedFiles");
        assertEquals(1, files.size());
    }

    @Test
    public void testUnknownGeneratorReturnsCliErrorCode() throws Exception {
        String source = """
            grammar Valid {
              @package: org.example.valid
              @root
              @mapping(RootNode, params=[value])
              Valid ::= 'ok' @value ;
            }
            """;
        Path grammarFile = Files.createTempFile("codegen-main-unknown-gen", ".ubnf");
        Path outputDir = Files.createTempDirectory("codegen-main-unknown-gen-out");
        Files.writeString(grammarFile, source);

        RunResult result = runCodegen(
            "--grammar", grammarFile.toString(),
            "--output", outputDir.toString(),
            "--generators", "Nope"
        );

        assertEquals(CodegenMain.EXIT_CLI_ERROR, result.exitCode());
        assertTrue(result.err().contains("Unknown generator"));
    }

    @Test
    public void testEmptyGeneratorsValueReturnsCliErrorCode() throws Exception {
        String source = """
            grammar Valid {
              @package: org.example.valid
              @root
              @mapping(RootNode, params=[value])
              Valid ::= 'ok' @value ;
            }
            """;
        Path grammarFile = Files.createTempFile("codegen-main-empty-gens", ".ubnf");
        Path outputDir = Files.createTempDirectory("codegen-main-empty-gens-out");
        Files.writeString(grammarFile, source);

        RunResult result = runCodegen(
            "--grammar", grammarFile.toString(),
            "--output", outputDir.toString(),
            "--generators", " ,  , "
        );

        assertEquals(CodegenMain.EXIT_CLI_ERROR, result.exitCode());
        assertTrue(result.err().contains("No generators specified"));
    }

    @Test
    public void testHelpReturnsOkAndPrintsUsage() {
        RunResult result = runCodegen("--help");
        assertEquals(CodegenMain.EXIT_OK, result.exitCode());
        assertTrue(result.out().contains("Usage: CodegenMain"));
        assertTrue(result.out().contains("--help"));
        assertTrue(result.out().contains("--version"));
        assertTrue(result.out().contains("--strict"));
        assertTrue(result.out().contains("--dry-run"));
        assertTrue(result.out().contains("--clean-output"));
        assertTrue(result.out().contains("--overwrite"));
        assertTrue(result.out().contains("--fail-on"));
        assertTrue(result.out().contains("--output-manifest"));
        assertTrue(result.out().contains("text|json|ndjson"));
        assertTrue(result.out().contains("--warnings-as-json"));
    }

    @Test
    public void testVersionReturnsOkAndPrintsVersion() {
        RunResult result = runCodegen("--version");
        assertEquals(CodegenMain.EXIT_OK, result.exitCode());
        assertFalse(result.out().isBlank());
        assertTrue(result.err().isBlank());
    }

    @Test
    public void testBrokenGrammarReturnsGenerationError() throws Exception {
        String source = "grammar Broken {";
        Path grammarFile = Files.createTempFile("codegen-main-broken-grammar", ".ubnf");
        Files.writeString(grammarFile, source);

        RunResult result = runCodegen(
            "--grammar", grammarFile.toString(),
            "--validate-only"
        );

        assertEquals(CodegenMain.EXIT_GENERATION_ERROR, result.exitCode());
        assertTrue(result.err().contains("Generation failed:"));
    }

    @Test
    public void testReportFileWriteFailureReturnsGenerationError() throws Exception {
        String source = """
            grammar Valid {
              @package: org.example.valid
              @root
              @mapping(RootNode, params=[value])
              Valid ::= 'ok' @value ;
            }
            """;
        Path grammarFile = Files.createTempFile("codegen-main-report-write-failure", ".ubnf");
        Files.writeString(grammarFile, source);
        Path blocker = Files.createTempFile("codegen-main-report-blocker", ".tmp");
        Path reportFile = blocker.resolve("report.json");

        RunResult result = runCodegen(
            "--grammar", grammarFile.toString(),
            "--validate-only",
            "--report-format", "json",
            "--report-file", reportFile.toString()
        );

        assertEquals(CodegenMain.EXIT_GENERATION_ERROR, result.exitCode());
        assertTrue(result.err().contains("I/O error:"));
    }

    @Test
    public void testUnknownGeneratorWithReportOptionsReturnsCliErrorAndNoReport() throws Exception {
        String source = """
            grammar Valid {
              @package: org.example.valid
              @root
              @mapping(RootNode, params=[value])
              Valid ::= 'ok' @value ;
            }
            """;
        Path grammarFile = Files.createTempFile("codegen-main-unknown-gen-report", ".ubnf");
        Path outputDir = Files.createTempDirectory("codegen-main-unknown-gen-report-out");
        Path reportFile = outputDir.resolve("report.json");
        Files.writeString(grammarFile, source);

        RunResult result = runCodegen(
            "--grammar", grammarFile.toString(),
            "--output", outputDir.toString(),
            "--generators", "Nope",
            "--report-format", "json",
            "--report-file", reportFile.toString(),
            "--report-schema-check"
        );

        assertEquals(CodegenMain.EXIT_CLI_ERROR, result.exitCode());
        assertTrue(result.err().contains("Unknown generator"));
        assertFalse(Files.exists(reportFile));
    }

    @Test
    public void testMissingGrammarReturnsCliErrorCode() {
        RunResult result = runCodegen("--validate-only");
        assertEquals(CodegenMain.EXIT_CLI_ERROR, result.exitCode());
        assertTrue(result.err().contains("Usage: CodegenMain"));
        assertTrue(result.err().contains("--report-version 1"));
        assertTrue(result.err().contains("--strict"));
        assertTrue(result.err().contains("--dry-run"));
        assertTrue(result.err().contains("--clean-output"));
        assertTrue(result.err().contains("--overwrite"));
        assertTrue(result.err().contains("--fail-on"));
        assertTrue(result.err().contains("--output-manifest"));
        assertTrue(result.err().contains("--report-schema-check"));
        assertTrue(result.err().contains("--warnings-as-json"));
    }

    @Test
    public void testUnsupportedReportVersionReturnsCliErrorCode() throws Exception {
        String source = """
            grammar Valid {
              @package: org.example.valid
              @root
              @mapping(RootNode, params=[value])
              Valid ::= 'ok' @value ;
            }
            """;
        Path grammarFile = Files.createTempFile("codegen-main-report-version-invalid", ".ubnf");
        Files.writeString(grammarFile, source);

        RunResult result = runCodegen(
            "--grammar", grammarFile.toString(),
            "--validate-only",
            "--report-format", "json",
            "--report-version", "2"
        );
        assertEquals(CodegenMain.EXIT_CLI_ERROR, result.exitCode());
        assertTrue(result.err().contains("Unsupported --report-version"));
    }

    @Test
    public void testReportVersion1OptionIsAccepted() throws Exception {
        String source = """
            grammar Valid {
              @package: org.example.valid
              @root
              @mapping(RootNode, params=[value])
              Valid ::= 'ok' @value ;
            }
            """;
        Path grammarFile = Files.createTempFile("codegen-main-report-version-v1", ".ubnf");
        Files.writeString(grammarFile, source);

        RunResult result = runCodegen(
            "--grammar", grammarFile.toString(),
            "--validate-only",
            "--report-format", "json",
            "--report-version", "1"
        );
        assertEquals(CodegenMain.EXIT_OK, result.exitCode());
        Map<String, Object> obj = JsonTestUtil.parseObject(result.out().trim());
        assertEquals(1L, JsonTestUtil.getLong(obj, "reportVersion"));
    }

    @Test
    public void testReportSchemaCheckOptionIsAccepted() throws Exception {
        String source = """
            grammar Valid {
              @package: org.example.valid
              @root
              @mapping(RootNode, params=[value])
              Valid ::= 'ok' @value ;
            }
            """;
        Path grammarFile = Files.createTempFile("codegen-main-schema-check", ".ubnf");
        Files.writeString(grammarFile, source);

        RunResult result = runCodegen(
            "--grammar", grammarFile.toString(),
            "--validate-only",
            "--report-format", "json",
            "--report-schema-check"
        );
        assertEquals(CodegenMain.EXIT_OK, result.exitCode());
        assertTrue(result.err().isBlank());
    }

    @Test
    public void testStrictOptionIsAccepted() throws Exception {
        String source = """
            grammar Valid {
              @package: org.example.valid
              @root
              @mapping(RootNode, params=[value])
              Valid ::= 'ok' @value ;
            }
            """;
        Path grammarFile = Files.createTempFile("codegen-main-strict", ".ubnf");
        Files.writeString(grammarFile, source);

        RunResult result = runCodegen(
            "--grammar", grammarFile.toString(),
            "--validate-only",
            "--strict"
        );
        assertEquals(CodegenMain.EXIT_OK, result.exitCode());
        assertTrue(result.out().contains("Validation succeeded"));
    }

    @Test
    public void testWarningsDoNotFailWithoutStrict() throws Exception {
        String source = """
            grammar WarnOnly {
              @package: org.example.warn
              @mapping(RootNode, params=[value])
              Start ::= 'ok' @value ;
            }
            """;
        Path grammarFile = Files.createTempFile("codegen-main-warning-nonstrict", ".ubnf");
        Files.writeString(grammarFile, source);

        RunResult result = runCodegen(
            "--grammar", grammarFile.toString(),
            "--validate-only"
        );
        assertEquals(CodegenMain.EXIT_OK, result.exitCode());
        assertTrue(result.err().contains("Validation warnings:"));
        assertTrue(result.err().contains("W-GENERAL-NO-ROOT"));
    }

    @Test
    public void testWarningsCanBeEmittedAsJsonInTextMode() throws Exception {
        String source = """
            grammar WarnOnly {
              @package: org.example.warn
              @mapping(RootNode, params=[value])
              Start ::= 'ok' @value ;
            }
            """;
        Path grammarFile = Files.createTempFile("codegen-main-warning-json", ".ubnf");
        Files.writeString(grammarFile, source);

        RunResult result = runCodegen(
            "--grammar", grammarFile.toString(),
            "--validate-only",
            "--warnings-as-json"
        );
        assertEquals(CodegenMain.EXIT_OK, result.exitCode());
        assertTrue(result.out().contains("Validation succeeded"));
        String warningPayload = result.err().trim();
        assertTrue(warningPayload.startsWith("{\"reportVersion\":1,"));
        assertTrue(warningPayload.contains("\"severity\":\"WARNING\""));
        assertTrue(warningPayload.contains("\"code\":\"W-GENERAL-NO-ROOT\""));
        assertTrue(warningPayload.contains("\"warningsCount\":1"));
    }

    @Test
    public void testDryRunDoesNotWriteGeneratedFiles() throws Exception {
        String source = """
            grammar Valid {
              @package: org.example.valid
              @root
              @mapping(RootNode, params=[value])
              Valid ::= 'ok' @value ;
            }
            """;
        Path grammarFile = Files.createTempFile("codegen-main-dry-run", ".ubnf");
        Path outputDir = Files.createTempDirectory("codegen-main-dry-run-out");
        Files.writeString(grammarFile, source);

        RunResult result = runCodegen(
            "--grammar", grammarFile.toString(),
            "--output", outputDir.toString(),
            "--generators", "AST",
            "--dry-run"
        );

        assertEquals(CodegenMain.EXIT_OK, result.exitCode());
        assertTrue(result.out().contains("Dry-run: would generate"));
        Path ast = outputDir.resolve("org/example/valid/ValidAST.java");
        assertFalse(Files.exists(ast));
    }

    @Test
    public void testOverwriteNeverRefusesExistingFile() throws Exception {
        String source = """
            grammar Valid {
              @package: org.example.valid
              @root
              @mapping(RootNode, params=[value])
              Valid ::= 'ok' @value ;
            }
            """;
        Path grammarFile = Files.createTempFile("codegen-main-overwrite-never", ".ubnf");
        Path outputDir = Files.createTempDirectory("codegen-main-overwrite-never-out");
        Files.writeString(grammarFile, source);
        Path ast = outputDir.resolve("org/example/valid/ValidAST.java");
        Files.createDirectories(ast.getParent());
        Files.writeString(ast, "// existing");

        RunResult result = runCodegen(
            "--grammar", grammarFile.toString(),
            "--output", outputDir.toString(),
            "--generators", "AST",
            "--overwrite", "never"
        );

        assertEquals(CodegenMain.EXIT_GENERATION_ERROR, result.exitCode());
        assertTrue(result.err().contains("Conflict (not overwritten):"));
        assertTrue(result.err().contains("Fail-on policy triggered: conflict=1"));
        assertEquals("// existing", Files.readString(ast));
    }

    @Test
    public void testOverwriteNeverCanPassWithFailOnNone() throws Exception {
        String source = """
            grammar Valid {
              @package: org.example.valid
              @root
              @mapping(RootNode, params=[value])
              Valid ::= 'ok' @value ;
            }
            """;
        Path grammarFile = Files.createTempFile("codegen-main-overwrite-never-pass", ".ubnf");
        Path outputDir = Files.createTempDirectory("codegen-main-overwrite-never-pass-out");
        Files.writeString(grammarFile, source);
        Path ast = outputDir.resolve("org/example/valid/ValidAST.java");
        Files.createDirectories(ast.getParent());
        Files.writeString(ast, "// existing");

        RunResult result = runCodegen(
            "--grammar", grammarFile.toString(),
            "--output", outputDir.toString(),
            "--generators", "AST",
            "--overwrite", "never",
            "--fail-on", "none",
            "--report-format", "json"
        );
        assertEquals(CodegenMain.EXIT_OK, result.exitCode());
        assertTrue(result.out().contains("\"conflictCount\":1"));
    }

    @Test
    public void testOverwriteIfDifferentSkipsUnchangedFile() throws Exception {
        String source = """
            grammar Valid {
              @package: org.example.valid
              @root
              @mapping(RootNode, params=[value])
              Valid ::= 'ok' @value ;
            }
            """;
        Path grammarFile = Files.createTempFile("codegen-main-overwrite-if-different", ".ubnf");
        Path outputDir = Files.createTempDirectory("codegen-main-overwrite-if-different-out");
        Files.writeString(grammarFile, source);

        RunResult first = runCodegen(
            "--grammar", grammarFile.toString(),
            "--output", outputDir.toString(),
            "--generators", "AST"
        );
        assertEquals(CodegenMain.EXIT_OK, first.exitCode());

        RunResult second = runCodegen(
            "--grammar", grammarFile.toString(),
            "--output", outputDir.toString(),
            "--generators", "AST",
            "--overwrite", "if-different"
        );
        assertEquals(CodegenMain.EXIT_OK, second.exitCode());
        assertTrue(second.out().contains("Skipped (unchanged):"));
    }

    @Test
    public void testCleanOutputAllowsOverwriteNever() throws Exception {
        String source = """
            grammar Valid {
              @package: org.example.valid
              @root
              @mapping(RootNode, params=[value])
              Valid ::= 'ok' @value ;
            }
            """;
        Path grammarFile = Files.createTempFile("codegen-main-clean-output", ".ubnf");
        Path outputDir = Files.createTempDirectory("codegen-main-clean-output-out");
        Files.writeString(grammarFile, source);
        Path ast = outputDir.resolve("org/example/valid/ValidAST.java");
        Files.createDirectories(ast.getParent());
        Files.writeString(ast, "// stale content");

        RunResult result = runCodegen(
            "--grammar", grammarFile.toString(),
            "--output", outputDir.toString(),
            "--generators", "AST",
            "--clean-output",
            "--overwrite", "never"
        );

        assertEquals(CodegenMain.EXIT_OK, result.exitCode());
        assertTrue(result.out().contains("Generated: "));
        assertFalse(Files.readString(ast).contains("stale content"));
    }

    @Test
    public void testWarningsFailWithStrictMode() throws Exception {
        String source = """
            grammar WarnOnly {
              @package: org.example.warn
              @mapping(RootNode, params=[value])
              Start ::= 'ok' @value ;
            }
            """;
        Path grammarFile = Files.createTempFile("codegen-main-warning-strict", ".ubnf");
        Files.writeString(grammarFile, source);

        RunResult result = runCodegen(
            "--grammar", grammarFile.toString(),
            "--validate-only",
            "--strict",
            "--report-format", "json"
        );
        assertEquals(CodegenMain.EXIT_STRICT_VALIDATION_ERROR, result.exitCode());
        assertTrue(result.err().contains("\"ok\":false"));
        assertTrue(result.err().contains("\"severity\":\"WARNING\""));
        assertTrue(result.err().contains("\"code\":\"W-GENERAL-NO-ROOT\""));
    }

    @Test
    public void testFailOnWarningWithoutStrict() throws Exception {
        String source = """
            grammar WarnOnly {
              @package: org.example.warn
              @mapping(RootNode, params=[value])
              Start ::= 'ok' @value ;
            }
            """;
        Path grammarFile = Files.createTempFile("codegen-main-warning-failon", ".ubnf");
        Files.writeString(grammarFile, source);

        RunResult result = runCodegen(
            "--grammar", grammarFile.toString(),
            "--validate-only",
            "--fail-on", "warning",
            "--report-format", "json"
        );
        assertEquals(CodegenMain.EXIT_STRICT_VALIDATION_ERROR, result.exitCode());
        assertTrue(result.err().contains("\"severity\":\"WARNING\""));
    }

    @Test
    public void testNdjsonValidateOnlyOutput() throws Exception {
        String source = """
            grammar Valid {
              @package: org.example.valid
              @root
              @mapping(RootNode, params=[value])
              Valid ::= 'ok' @value ;
            }
            """;
        Path grammarFile = Files.createTempFile("codegen-main-ndjson-validate", ".ubnf");
        Files.writeString(grammarFile, source);

        RunResult result = runCodegen(
            "--grammar", grammarFile.toString(),
            "--validate-only",
            "--report-format", "ndjson"
        );

        assertEquals(CodegenMain.EXIT_OK, result.exitCode());
        String out = result.out().trim();
        assertTrue(out.startsWith("{\"event\":\"validate-success\",\"payload\":{"));
        assertTrue(out.contains("\"mode\":\"validate\""));
        assertTrue(out.contains("\"warningsCount\":0"));
    }

    @Test
    public void testNdjsonGenerationOutputIncludesFileEventsAndSummary() throws Exception {
        String source = """
            grammar Valid {
              @package: org.example.valid
              @root
              @mapping(RootNode, params=[value])
              Valid ::= 'ok' @value ;
            }
            """;
        Path grammarFile = Files.createTempFile("codegen-main-ndjson-generate", ".ubnf");
        Path outputDir = Files.createTempDirectory("codegen-main-ndjson-generate-out");
        Files.writeString(grammarFile, source);

        RunResult result = runCodegen(
            "--grammar", grammarFile.toString(),
            "--output", outputDir.toString(),
            "--generators", "AST",
            "--report-format", "ndjson"
        );
        assertEquals(CodegenMain.EXIT_OK, result.exitCode());
        String out = result.out();
        assertTrue(out.contains("\"event\":\"file\""));
        assertTrue(out.contains("\"action\":\"written\""));
        assertTrue(out.contains("\"event\":\"generate-summary\""));
        assertTrue(out.contains("\"writtenCount\":1"));
    }

    @Test
    public void testNdjsonGenerationIncludesCleanedEvent() throws Exception {
        String source = """
            grammar Valid {
              @package: org.example.valid
              @root
              @mapping(RootNode, params=[value])
              Valid ::= 'ok' @value ;
            }
            """;
        Path grammarFile = Files.createTempFile("codegen-main-ndjson-cleaned", ".ubnf");
        Path outputDir = Files.createTempDirectory("codegen-main-ndjson-cleaned-out");
        Files.writeString(grammarFile, source);
        Path ast = outputDir.resolve("org/example/valid/ValidAST.java");
        Files.createDirectories(ast.getParent());
        Files.writeString(ast, "// stale");

        RunResult result = runCodegen(
            "--grammar", grammarFile.toString(),
            "--output", outputDir.toString(),
            "--generators", "AST",
            "--clean-output",
            "--report-format", "ndjson"
        );
        assertEquals(CodegenMain.EXIT_OK, result.exitCode());
        assertTrue(result.out().contains("\"action\":\"cleaned\""));
    }

    @Test
    public void testFailOnSkippedReturnsGenerationError() throws Exception {
        String source = """
            grammar Valid {
              @package: org.example.valid
              @root
              @mapping(RootNode, params=[value])
              Valid ::= 'ok' @value ;
            }
            """;
        Path grammarFile = Files.createTempFile("codegen-main-failon-skipped", ".ubnf");
        Path outputDir = Files.createTempDirectory("codegen-main-failon-skipped-out");
        Files.writeString(grammarFile, source);

        RunResult first = runCodegen(
            "--grammar", grammarFile.toString(),
            "--output", outputDir.toString(),
            "--generators", "AST"
        );
        assertEquals(CodegenMain.EXIT_OK, first.exitCode());

        RunResult second = runCodegen(
            "--grammar", grammarFile.toString(),
            "--output", outputDir.toString(),
            "--generators", "AST",
            "--overwrite", "if-different",
            "--fail-on", "skipped"
        );
        assertEquals(CodegenMain.EXIT_GENERATION_ERROR, second.exitCode());
        assertTrue(second.err().contains("Fail-on policy triggered: skipped=1"));
    }

    @Test
    public void testFailOnWarningsThresholdReturnsStrictValidationError() throws Exception {
        String source = """
            grammar WarnOnly {
              @package: org.example.warn
              @mapping(RootNode, params=[value])
              Start ::= 'ok' @value ;
            }
            """;
        Path grammarFile = Files.createTempFile("codegen-main-failon-warning-threshold", ".ubnf");
        Files.writeString(grammarFile, source);

        RunResult result = runCodegen(
            "--grammar", grammarFile.toString(),
            "--validate-only",
            "--fail-on", "warnings-count>=1",
            "--report-format", "json"
        );
        assertEquals(CodegenMain.EXIT_STRICT_VALIDATION_ERROR, result.exitCode());
        assertTrue(result.err().contains("\"warningsCount\":1"));
    }

    @Test
    public void testOutputManifestIsWritten() throws Exception {
        String source = """
            grammar Valid {
              @package: org.example.valid
              @root
              @mapping(RootNode, params=[value])
              Valid ::= 'ok' @value ;
            }
            """;
        Path grammarFile = Files.createTempFile("codegen-main-manifest", ".ubnf");
        Path outputDir = Files.createTempDirectory("codegen-main-manifest-out");
        Path manifest = Files.createTempFile("codegen-main-manifest", ".json");
        Files.writeString(grammarFile, source);

        RunResult result = runCodegen(
            "--grammar", grammarFile.toString(),
            "--output", outputDir.toString(),
            "--generators", "AST",
            "--output-manifest", manifest.toString()
        );
        assertEquals(CodegenMain.EXIT_OK, result.exitCode());
        String payload = Files.readString(manifest);
        assertTrue(payload.contains("\"mode\":\"generate\""));
        assertTrue(payload.contains("\"writtenCount\":1"));
        assertTrue(payload.contains("\"files\":["));
    }

    @Test
    public void testCleanOutputRejectsUnsafeRootPath() throws Exception {
        String source = """
            grammar Valid {
              @package: org.example.valid
              @root
              @mapping(RootNode, params=[value])
              Valid ::= 'ok' @value ;
            }
            """;
        Path grammarFile = Files.createTempFile("codegen-main-clean-unsafe", ".ubnf");
        Files.writeString(grammarFile, source);

        RunResult result = runCodegen(
            "--grammar", grammarFile.toString(),
            "--output", "/",
            "--generators", "AST",
            "--clean-output"
        );
        assertEquals(CodegenMain.EXIT_CLI_ERROR, result.exitCode());
        assertTrue(result.err().contains("Refusing --clean-output for unsafe path"));
    }

    @Test
    public void testReportSchemaCheckOptionWithValidationFailureJson() throws Exception {
        String source = """
            grammar Invalid {
              @package: org.example.invalid
              @root
              @mapping(RootNode, params=[value, missing])
              Invalid ::= 'x' @value ;
            }
            """;
        Path grammarFile = Files.createTempFile("codegen-main-schema-check-invalid", ".ubnf");
        Files.writeString(grammarFile, source);

        RunResult result = runCodegen(
            "--grammar", grammarFile.toString(),
            "--validate-only",
            "--report-format", "json",
            "--report-schema-check"
        );
        assertEquals(CodegenMain.EXIT_VALIDATION_ERROR, result.exitCode());
        String payload = result.err().trim();
        assertTrue(payload.startsWith("{\"reportVersion\":1,"));
        assertTrue(payload.contains("\"mode\":\"validate\""));
        assertTrue(payload.contains("\"ok\":false"));
    }

    @Test
    public void testReportSchemaCheckOptionWithGenerationJson() throws Exception {
        String source = """
            grammar Valid {
              @package: org.example.valid
              @root
              @mapping(RootNode, params=[value])
              Valid ::= 'ok' @value ;
            }
            """;
        Path grammarFile = Files.createTempFile("codegen-main-schema-check-generate", ".ubnf");
        Path outputDir = Files.createTempDirectory("codegen-main-schema-check-generate-out");
        Path reportFile = Files.createTempFile("codegen-main-schema-check-generate-report", ".json");
        Files.writeString(grammarFile, source);

        RunResult result = runCodegen(
            "--grammar", grammarFile.toString(),
            "--output", outputDir.toString(),
            "--generators", "AST",
            "--report-format", "json",
            "--report-file", reportFile.toString(),
            "--report-schema-check"
        );
        assertEquals(CodegenMain.EXIT_OK, result.exitCode());
        assertTrue(result.out().contains("\"mode\":\"generate\""));
        assertTrue(Files.readString(reportFile).contains("\"mode\":\"generate\""));
    }

    @Test
    public void testGeneratedAtUsesProvidedClock() throws Exception {
        String source = """
            grammar Valid {
              @package: org.example.valid
              @root
              @mapping(RootNode, params=[value])
              Valid ::= 'ok' @value ;
            }
            """;
        Path grammarFile = Files.createTempFile("codegen-main-clock", ".ubnf");
        Files.writeString(grammarFile, source);

        Clock fixedClock = Clock.fixed(Instant.parse("2026-01-02T03:04:05Z"), ZoneOffset.UTC);
        RunResult result = runCodegenWithClock(
            fixedClock,
            "--grammar", grammarFile.toString(),
            "--validate-only",
            "--report-format", "json"
        );

        assertEquals(CodegenMain.EXIT_OK, result.exitCode());
        assertTrue(result.out().contains("\"generatedAt\":\"2026-01-02T03:04:05Z\""));
    }

    private static RunResult runCodegen(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exitCode = CodegenMain.run(args, new PrintStream(out), new PrintStream(err));
        return new RunResult(exitCode, out.toString(), err.toString());
    }

    private static RunResult runCodegenWithClock(Clock clock, String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exitCode = CodegenMain.runWithClock(args, new PrintStream(out), new PrintStream(err), clock);
        return new RunResult(exitCode, out.toString(), err.toString());
    }

    private static void assertHasNonEmptyJsonField(String json, String fieldName) {
        String value = extractJsonStringField(json, fieldName);
        assertTrue(fieldName + " should be non-empty", value != null && !value.isBlank());
    }

    private static void assertGeneratedAtIsIsoInstant(String json) {
        String value = extractJsonStringField(json, "generatedAt");
        assertTrue("generatedAt should exist", value != null);
        Instant.parse(value);
    }

    private static String extractJsonStringField(String json, String fieldName) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(fieldName) + "\":\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }

    private record RunResult(int exitCode, String out, String err) {}
}
