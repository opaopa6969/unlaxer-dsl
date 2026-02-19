package org.unlaxer.dsl.codegen;

import java.nio.file.Files;
import java.nio.file.Path;

import org.unlaxer.dsl.bootstrap.UBNFAST.GrammarDecl;
import org.unlaxer.dsl.bootstrap.UBNFMapper;

/**
 * Utility entry point to refresh golden snapshot fixtures.
 *
 * <p>Run from project root after test classes are compiled:</p>
 *
 * <pre>
 *   mvn -q -DskipTests test-compile
 *   CP="target/classes:target/test-classes:$(mvn -q -DincludeScope=test -Dmdep.outputFile=/tmp/unlaxer-dsl-test-cp.txt dependency:build-classpath >/dev/null && cat /tmp/unlaxer-dsl-test-cp.txt)"
 *   java --enable-preview -cp "$CP" org.unlaxer.dsl.codegen.SnapshotFixtureWriter
 * </pre>
 */
public final class SnapshotFixtureWriter {

    private SnapshotFixtureWriter() {}

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

    public static void main(String[] args) throws Exception {
        Path goldenDir = Path.of("src/test/resources/golden");
        Files.createDirectories(goldenDir);

        GrammarDecl snapshot = UBNFMapper.parse(SNAPSHOT_GRAMMAR).grammars().get(0);
        GrammarDecl rightAssoc = UBNFMapper.parse(RIGHT_ASSOC_SNAPSHOT_GRAMMAR).grammars().get(0);

        Files.writeString(goldenDir.resolve("ast_snapshot.java.txt"),
            new ASTGenerator().generate(snapshot).source());
        Files.writeString(goldenDir.resolve("evaluator_snapshot.java.txt"),
            new EvaluatorGenerator().generate(snapshot).source());
        Files.writeString(goldenDir.resolve("parser_snapshot.java.txt"),
            new ParserGenerator().generate(snapshot).source());
        Files.writeString(goldenDir.resolve("mapper_snapshot.java.txt"),
            new MapperGenerator().generate(snapshot).source());
        Files.writeString(goldenDir.resolve("lsp_snapshot.java.txt"),
            new LSPGenerator().generate(snapshot).source());
        Files.writeString(goldenDir.resolve("dap_snapshot.java.txt"),
            new DAPGenerator().generate(snapshot).source());

        Files.writeString(goldenDir.resolve("parser_right_assoc_snapshot.java.txt"),
            new ParserGenerator().generate(rightAssoc).source());
        Files.writeString(goldenDir.resolve("mapper_right_assoc_snapshot.java.txt"),
            new MapperGenerator().generate(rightAssoc).source());
    }
}
