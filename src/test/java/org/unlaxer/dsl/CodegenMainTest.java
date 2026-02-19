package org.unlaxer.dsl;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
}
