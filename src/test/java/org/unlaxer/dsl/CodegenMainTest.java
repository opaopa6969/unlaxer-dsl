package org.unlaxer.dsl;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

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

        CodegenMain.main(new String[] {
            "--grammar", grammarFile.toString(),
            "--output", outputDir.toString(),
            "--generators", "AST"
        });

        Path firstAst = outputDir.resolve("org/example/first/FirstAST.java");
        Path secondAst = outputDir.resolve("org/example/second/SecondAST.java");

        assertTrue("first grammar AST should be generated", Files.exists(firstAst));
        assertTrue("second grammar AST should be generated", Files.exists(secondAst));
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

        try {
            CodegenMain.main(new String[] {
                "--grammar", grammarFile.toString(),
                "--output", outputDir.toString(),
                "--generators", "AST"
            });
            fail("expected validation error");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("has no matching capture"));
        }
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

        try {
            CodegenMain.main(new String[] {
                "--grammar", grammarFile.toString(),
                "--output", outputDir.toString(),
                "--generators", "Parser"
            });
            fail("expected validation error");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("body is not canonical"));
        }
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

        try {
            CodegenMain.main(new String[] {
                "--grammar", grammarFile.toString(),
                "--output", outputDir.toString(),
                "--generators", "Parser"
            });
            fail("expected validation error");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("grammar InvalidA:"));
            assertTrue(e.getMessage().contains("grammar InvalidB:"));
            assertTrue(e.getMessage().contains("[code:"));
        }
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

        CodegenMain.main(new String[] {
            "--grammar", grammarFile.toString(),
            "--validate-only"
        });

        Path ast = outputDir.resolve("org/example/valid/ValidAST.java");
        assertTrue("validate-only should not generate files", !Files.exists(ast));
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

        try {
            CodegenMain.main(new String[] {
                "--grammar", grammarFile.toString(),
                "--validate-only"
            });
            fail("expected validation error");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Grammar validation failed"));
            assertTrue(e.getMessage().contains("E-MAPPING-MISSING-CAPTURE"));
        }
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

        PrintStream originalOut = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(baos));
            CodegenMain.main(new String[] {
                "--grammar", grammarFile.toString(),
                "--validate-only",
                "--report-format", "json"
            });
        } finally {
            System.setOut(originalOut);
        }

        String out = baos.toString().trim();
        assertEquals("{\"reportVersion\":1,\"mode\":\"validate\",\"ok\":true,\"grammarCount\":1,\"issues\":[]}", out);
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

        try {
            CodegenMain.main(new String[] {
                "--grammar", grammarFile.toString(),
                "--validate-only",
                "--report-format", "json"
            });
            fail("expected validation error");
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            assertTrue(msg.startsWith("{\"reportVersion\":1,\"mode\":\"validate\",\"ok\":false"));
            assertTrue(msg.contains("\"grammar\":\"Invalid\""));
            assertTrue(msg.contains("\"rule\":\"Invalid\""));
            assertTrue(msg.contains("\"code\":\"E-MAPPING-MISSING-CAPTURE\""));
            assertTrue(msg.contains("\"severity\":\"ERROR\""));
            assertTrue(msg.contains("\"category\":\"MAPPING\""));
            assertTrue(msg.contains("\"issues\":["));
        }
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

        try {
            CodegenMain.main(new String[] {
                "--grammar", grammarFile.toString(),
                "--validate-only",
                "--report-format", "json"
            });
            fail("expected validation error");
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            int idxA = msg.indexOf("\"grammar\":\"InvalidA\"");
            int idxB = msg.indexOf("\"grammar\":\"InvalidB\"");
            assertTrue(idxA >= 0);
            assertTrue(idxB >= 0);
            assertTrue("issues should be sorted by grammar", idxA < idxB);
        }
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

        CodegenMain.main(new String[] {
            "--grammar", grammarFile.toString(),
            "--validate-only",
            "--report-format", "json",
            "--report-file", reportFile.toString()
        });

        String report = Files.readString(reportFile).trim();
        assertEquals("{\"reportVersion\":1,\"mode\":\"validate\",\"ok\":true,\"grammarCount\":1,\"issues\":[]}", report);
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

        CodegenMain.main(new String[] {
            "--grammar", grammarFile.toString(),
            "--output", outputDir.toString(),
            "--generators", "AST",
            "--report-format", "json",
            "--report-file", reportFile.toString()
        });

        String report = Files.readString(reportFile);
        assertTrue(report.contains("\"ok\":true"));
        assertTrue(report.contains("\"reportVersion\":1"));
        assertTrue(report.contains("\"mode\":\"generate\""));
        assertTrue(report.contains("\"generatedCount\":1"));
        assertTrue(report.contains("\"generatedFiles\":["));
        assertTrue(report.contains("ValidAST.java"));
    }
}
