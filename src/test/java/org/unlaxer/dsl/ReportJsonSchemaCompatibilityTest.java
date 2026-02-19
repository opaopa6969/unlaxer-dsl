package org.unlaxer.dsl;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Test;

public class ReportJsonSchemaCompatibilityTest {

    @Test
    public void testValidateSuccessTopLevelSchemaV1() throws Exception {
        String source = """
            grammar Valid {
              @package: org.example.valid
              @root
              @mapping(RootNode, params=[value])
              Valid ::= 'ok' @value ;
            }
            """;
        Path grammarFile = Files.createTempFile("schema-validate-success", ".ubnf");
        Files.writeString(grammarFile, source);

        RunResult result = runCodegen(
            "--grammar", grammarFile.toString(),
            "--validate-only",
            "--report-format", "json"
        );

        assertEquals(CodegenMain.EXIT_OK, result.exitCode());
        var obj = JsonTestUtil.parseObject(result.out().trim());
        assertEquals(
            List.of("reportVersion", "toolVersion", "generatedAt", "mode", "ok", "grammarCount", "issues"),
            List.copyOf(obj.keySet())
        );
        assertEquals(1L, obj.get("reportVersion"));
        assertEquals(Boolean.TRUE, obj.get("ok"));
        assertEquals(1L, obj.get("grammarCount"));
        assertEquals(List.of(), obj.get("issues"));
    }

    @Test
    public void testValidateFailureTopLevelSchemaV1() throws Exception {
        String source = """
            grammar Invalid {
              @package: org.example.invalid
              @root
              @mapping(RootNode, params=[value, missing])
              Invalid ::= 'x' @value ;
            }
            """;
        Path grammarFile = Files.createTempFile("schema-validate-failure", ".ubnf");
        Files.writeString(grammarFile, source);

        RunResult result = runCodegen(
            "--grammar", grammarFile.toString(),
            "--validate-only",
            "--report-format", "json"
        );

        assertEquals(CodegenMain.EXIT_VALIDATION_ERROR, result.exitCode());
        var obj = JsonTestUtil.parseObject(result.err().trim());
        assertEquals(
            List.of(
                "reportVersion",
                "toolVersion",
                "generatedAt",
                "mode",
                "ok",
                "issueCount",
                "severityCounts",
                "categoryCounts",
                "issues"
            ),
            List.copyOf(obj.keySet())
        );
        assertEquals(1L, obj.get("reportVersion"));
        assertEquals(Boolean.FALSE, obj.get("ok"));
        @SuppressWarnings("unchecked")
        var severityCounts = (java.util.Map<String, Object>) obj.get("severityCounts");
        assertEquals(1L, severityCounts.get("ERROR"));
        @SuppressWarnings("unchecked")
        var issues = (java.util.List<Object>) obj.get("issues");
        assertEquals(1, issues.size());
    }

    @Test
    public void testGenerateSuccessTopLevelSchemaV1() throws Exception {
        String source = """
            grammar Valid {
              @package: org.example.valid
              @root
              @mapping(RootNode, params=[value])
              Valid ::= 'ok' @value ;
            }
            """;
        Path grammarFile = Files.createTempFile("schema-generate-success", ".ubnf");
        Path outputDir = Files.createTempDirectory("schema-generate-success-out");
        Files.writeString(grammarFile, source);

        RunResult result = runCodegen(
            "--grammar", grammarFile.toString(),
            "--output", outputDir.toString(),
            "--generators", "AST",
            "--report-format", "json"
        );

        assertEquals(CodegenMain.EXIT_OK, result.exitCode());
        String[] lines = result.out().trim().split("\\R");
        String json = lines[lines.length - 1];
        var obj = JsonTestUtil.parseObject(json);
        assertEquals(
            List.of(
                "reportVersion",
                "toolVersion",
                "generatedAt",
                "mode",
                "ok",
                "grammarCount",
                "generatedCount",
                "generatedFiles"
            ),
            List.copyOf(obj.keySet())
        );
        assertEquals(1L, obj.get("reportVersion"));
        assertEquals(Boolean.TRUE, obj.get("ok"));
        assertEquals(1L, obj.get("generatedCount"));
    }

    private static RunResult runCodegen(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exitCode = CodegenMain.run(args, new PrintStream(out), new PrintStream(err));
        return new RunResult(exitCode, out.toString(), err.toString());
    }

    private record RunResult(int exitCode, String out, String err) {}
}
