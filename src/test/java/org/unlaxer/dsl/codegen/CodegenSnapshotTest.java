package org.unlaxer.dsl.codegen;

import static org.junit.Assert.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;
import org.unlaxer.dsl.bootstrap.UBNFAST.GrammarDecl;
import org.unlaxer.dsl.bootstrap.UBNFMapper;

public class CodegenSnapshotTest {

    private static final String SNAPSHOT_GRAMMAR = """
        grammar Snapshot {
          @package: org.example.snapshot
          @whitespace: javaStyle

          token NUMBER = NumberParser

          @root
          @mapping(ExprNode, params=[left, op, right])
          @leftAssoc
          @precedence(level=10)
          Expr ::= Term @left { '+' @op Term @right } ;

          @mapping(TermNode, params=[left, op, right])
          @leftAssoc
          @precedence(level=20)
          Term ::= Factor @left { '*' @op Factor @right } ;

          Factor ::= NUMBER ;
        }
        """;

    private static final String RIGHT_ASSOC_SNAPSHOT_GRAMMAR = """
        grammar SnapshotRightAssoc {
          @package: org.example.snapshot
          @whitespace: javaStyle

          token NUMBER = NumberParser

          @root
          @mapping(PowNode, params=[left, op, right])
          @rightAssoc
          @precedence(level=30)
          Expr ::= Atom @left { '^' @op Expr @right } ;

          Atom ::= NUMBER ;
        }
        """;

    @Test
    public void testParserGeneratorSnapshot() throws Exception {
        GrammarDecl grammar = parseGrammar(SNAPSHOT_GRAMMAR);
        String actual = new ParserGenerator().generate(grammar).source();
        String expected = Files.readString(Path.of("src/test/resources/golden/parser_snapshot.java.txt"));
        assertEquals(normalize(expected), normalize(actual));
    }

    @Test
    public void testMapperGeneratorSnapshot() throws Exception {
        GrammarDecl grammar = parseGrammar(SNAPSHOT_GRAMMAR);
        String actual = new MapperGenerator().generate(grammar).source();
        String expected = Files.readString(Path.of("src/test/resources/golden/mapper_snapshot.java.txt"));
        assertEquals(normalize(expected), normalize(actual));
    }

    @Test
    public void testRightAssocParserGeneratorSnapshot() throws Exception {
        GrammarDecl grammar = parseGrammar(RIGHT_ASSOC_SNAPSHOT_GRAMMAR);
        String actual = new ParserGenerator().generate(grammar).source();
        String expected = Files.readString(Path.of("src/test/resources/golden/parser_right_assoc_snapshot.java.txt"));
        assertEquals(normalize(expected), normalize(actual));
    }

    @Test
    public void testRightAssocMapperGeneratorSnapshot() throws Exception {
        GrammarDecl grammar = parseGrammar(RIGHT_ASSOC_SNAPSHOT_GRAMMAR);
        String actual = new MapperGenerator().generate(grammar).source();
        String expected = Files.readString(Path.of("src/test/resources/golden/mapper_right_assoc_snapshot.java.txt"));
        assertEquals(normalize(expected), normalize(actual));
    }

    @Test
    public void testLspGeneratorSnapshot() throws Exception {
        GrammarDecl grammar = parseGrammar(SNAPSHOT_GRAMMAR);
        String actual = new LSPGenerator().generate(grammar).source();
        String expected = Files.readString(Path.of("src/test/resources/golden/lsp_snapshot.java.txt"));
        assertEquals(normalize(expected), normalize(actual));
    }

    @Test
    public void testDapGeneratorSnapshot() throws Exception {
        GrammarDecl grammar = parseGrammar(SNAPSHOT_GRAMMAR);
        String actual = new DAPGenerator().generate(grammar).source();
        String expected = Files.readString(Path.of("src/test/resources/golden/dap_snapshot.java.txt"));
        assertEquals(normalize(expected), normalize(actual));
    }

    @Test
    public void testAstGeneratorSnapshot() throws Exception {
        GrammarDecl grammar = parseGrammar(SNAPSHOT_GRAMMAR);
        String actual = new ASTGenerator().generate(grammar).source();
        String expected = Files.readString(Path.of("src/test/resources/golden/ast_snapshot.java.txt"));
        assertEquals(normalize(expected), normalize(actual));
    }

    @Test
    public void testEvaluatorGeneratorSnapshot() throws Exception {
        GrammarDecl grammar = parseGrammar(SNAPSHOT_GRAMMAR);
        String actual = new EvaluatorGenerator().generate(grammar).source();
        String expected = Files.readString(Path.of("src/test/resources/golden/evaluator_snapshot.java.txt"));
        assertEquals(normalize(expected), normalize(actual));
    }

    private GrammarDecl parseGrammar(String source) {
        return UBNFMapper.parse(source).grammars().get(0);
    }

    private String normalize(String s) {
        return s.replace("\r\n", "\n");
    }
}
