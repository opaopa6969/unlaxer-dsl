package org.unlaxer.dsl;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
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
        assertEquals(
            List.of("reportVersion", "toolVersion", "generatedAt", "mode", "ok", "grammarCount", "issues"),
            extractTopLevelKeys(result.out().trim())
        );
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
            extractTopLevelKeys(result.err().trim())
        );
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
            extractTopLevelKeys(json)
        );
    }

    private static RunResult runCodegen(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exitCode = CodegenMain.run(args, new PrintStream(out), new PrintStream(err));
        return new RunResult(exitCode, out.toString(), err.toString());
    }

    private static List<String> extractTopLevelKeys(String json) {
        String s = json.trim();
        if (s.isEmpty() || s.charAt(0) != '{' || s.charAt(s.length() - 1) != '}') {
            throw new IllegalArgumentException("not a JSON object: " + json);
        }
        List<String> keys = new ArrayList<>();
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        boolean expectingKey = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                if (depth == 1 && expectingKey) {
                    int j = i + 1;
                    boolean keyEscape = false;
                    StringBuilder key = new StringBuilder();
                    while (j < s.length()) {
                        char kc = s.charAt(j);
                        if (keyEscape) {
                            keyEscape = false;
                            key.append(kc);
                        } else if (kc == '\\') {
                            keyEscape = true;
                        } else if (kc == '"') {
                            break;
                        } else {
                            key.append(kc);
                        }
                        j++;
                    }
                    keys.add(key.toString());
                    expectingKey = false;
                    i = j;
                    continue;
                }
                inString = true;
                continue;
            }
            if (c == '{' || c == '[') {
                depth++;
                if (c == '{' && depth == 1) {
                    expectingKey = true;
                }
                continue;
            }
            if (c == '}' || c == ']') {
                depth--;
                continue;
            }
            if (c == ',' && depth == 1) {
                expectingKey = true;
            }
        }
        return keys;
    }

    private record RunResult(int exitCode, String out, String err) {}
}
