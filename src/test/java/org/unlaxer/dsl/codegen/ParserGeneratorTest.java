package org.unlaxer.dsl.codegen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.unlaxer.dsl.bootstrap.UBNFAST.GrammarDecl;
import org.unlaxer.dsl.bootstrap.UBNFMapper;

public class ParserGeneratorTest {

    private static final String TINYCALC_GRAMMAR =
        "grammar TinyCalc {\n" +
        "  @package: org.unlaxer.tinycalc.generated\n" +
        "  @whitespace: javaStyle\n" +
        "\n" +
        "  token NUMBER     = NumberParser\n" +
        "  token IDENTIFIER = IdentifierParser\n" +
        "\n" +
        "  @root\n" +
        "  @mapping(TinyCalcProgram, params=[declarations, expression])\n" +
        "  TinyCalc ::=\n" +
        "    { VariableDeclaration } @declarations\n" +
        "    Expression @expression ;\n" +
        "\n" +
        "  @mapping(VarDecl, params=[keyword, name, init])\n" +
        "  VariableDeclaration ::=\n" +
        "    ( 'var' | 'variable' ) @keyword\n" +
        "    IDENTIFIER @name\n" +
        "    [ 'set' Expression @init ]\n" +
        "    ';' ;\n" +
        "\n" +
        "  @mapping(BinaryExpr, params=[left, op, right])\n" +
        "  @leftAssoc\n" +
        "  Expression ::= Term @left { ( '+' @op | '-' @op ) Term @right } ;\n" +
        "\n" +
        "  @mapping(BinaryExpr, params=[left, op, right])\n" +
        "  @leftAssoc\n" +
        "  Term ::= Factor @left { ( '*' @op | '/' @op ) Factor @right } ;\n" +
        "\n" +
        "  Factor ::=\n" +
        "      '(' Expression ')'\n" +
        "    | NUMBER\n" +
        "    | IDENTIFIER ;\n" +
        "}";

    // =========================================================================
    // パッケージ名・クラス名
    // =========================================================================

    @Test
    public void testPackageName() {
        GrammarDecl grammar = parseGrammar(TINYCALC_GRAMMAR);
        ParserGenerator gen = new ParserGenerator();
        CodeGenerator.GeneratedSource result = gen.generate(grammar);
        assertEquals("org.unlaxer.tinycalc.generated", result.packageName());
    }

    @Test
    public void testClassName() {
        GrammarDecl grammar = parseGrammar(TINYCALC_GRAMMAR);
        ParserGenerator gen = new ParserGenerator();
        CodeGenerator.GeneratedSource result = gen.generate(grammar);
        assertEquals("TinyCalcParsers", result.className());
    }

    // =========================================================================
    // 基本構造
    // =========================================================================

    @Test
    public void testContainsOuterClass() {
        String source = generate(TINYCALC_GRAMMAR);
        assertTrue("should contain outer class declaration",
            source.contains("class TinyCalcParsers"));
    }

    @Test
    public void testContainsPackageDeclaration() {
        String source = generate(TINYCALC_GRAMMAR);
        assertTrue("should contain package declaration",
            source.contains("package org.unlaxer.tinycalc.generated;"));
    }

    @Test
    public void testContainsGetRootParser() {
        String source = generate(TINYCALC_GRAMMAR);
        assertTrue("should contain getRootParser method",
            source.contains("getRootParser()"));
    }

    @Test
    public void testGetRootParserReturnsTinyCalcParser() {
        String source = generate(TINYCALC_GRAMMAR);
        assertTrue("getRootParser should return TinyCalcParser",
            source.contains("Parser.get(TinyCalcParser.class)"));
    }

    // =========================================================================
    // ルールクラス
    // =========================================================================

    @Test
    public void testContainsTinyCalcParser() {
        String source = generate(TINYCALC_GRAMMAR);
        assertTrue("should contain TinyCalcParser class",
            source.contains("class TinyCalcParser"));
    }

    @Test
    public void testContainsVariableDeclarationParser() {
        String source = generate(TINYCALC_GRAMMAR);
        assertTrue("should contain VariableDeclarationParser class",
            source.contains("class VariableDeclarationParser"));
    }

    @Test
    public void testContainsExpressionParser() {
        String source = generate(TINYCALC_GRAMMAR);
        assertTrue("should contain ExpressionParser class",
            source.contains("class ExpressionParser"));
    }

    @Test
    public void testContainsTermParser() {
        String source = generate(TINYCALC_GRAMMAR);
        assertTrue("should contain TermParser class",
            source.contains("class TermParser"));
    }

    @Test
    public void testContainsFactorParser() {
        String source = generate(TINYCALC_GRAMMAR);
        assertTrue("should contain FactorParser class",
            source.contains("class FactorParser"));
    }

    // =========================================================================
    // 要素変換
    // =========================================================================

    @Test
    public void testContainsWordParserForVar() {
        String source = generate(TINYCALC_GRAMMAR);
        assertTrue("should contain WordParser for 'var'",
            source.contains("WordParser(\"var\")"));
    }

    @Test
    public void testContainsWordParserForVariable() {
        String source = generate(TINYCALC_GRAMMAR);
        assertTrue("should contain WordParser for 'variable'",
            source.contains("WordParser(\"variable\")"));
    }

    @Test
    public void testContainsParserGetExpressionParser() {
        String source = generate(TINYCALC_GRAMMAR);
        assertTrue("should contain Parser.get(ExpressionParser.class)",
            source.contains("Parser.get(ExpressionParser.class)"));
    }

    @Test
    public void testContainsZeroOrMore() {
        String source = generate(TINYCALC_GRAMMAR);
        assertTrue("should contain ZeroOrMore for repeat elements",
            source.contains("ZeroOrMore"));
    }

    @Test
    public void testContainsOptional() {
        String source = generate(TINYCALC_GRAMMAR);
        // VariableDeclaration has [ 'set' Expression @init ] -> Optional
        assertTrue("should contain Optional for optional elements",
            source.contains("Optional"));
    }

    // =========================================================================
    // トークン参照
    // =========================================================================

    @Test
    public void testTokenReferenceUsesParserClass() {
        String source = generate(TINYCALC_GRAMMAR);
        // NUMBER token -> NumberParser.class
        assertTrue("NUMBER token should reference NumberParser.class",
            source.contains("NumberParser.class"));
    }

    @Test
    public void testIdentifierTokenReferenceUsesParserClass() {
        String source = generate(TINYCALC_GRAMMAR);
        // IDENTIFIER token -> IdentifierParser.class
        assertTrue("IDENTIFIER token should reference IdentifierParser.class",
            source.contains("IdentifierParser.class"));
    }

    // =========================================================================
    // デリミタ・チェーン
    // =========================================================================

    @Test
    public void testContainsSpaceDelimitor() {
        String source = generate(TINYCALC_GRAMMAR);
        assertTrue("should contain space delimitor class",
            source.contains("TinyCalcSpaceDelimitor"));
    }

    @Test
    public void testContainsLazyChain() {
        String source = generate(TINYCALC_GRAMMAR);
        assertTrue("should contain base chain class",
            source.contains("TinyCalcLazyChain"));
    }

    @Test
    public void testContainsSpaceParserInDelimitor() {
        String source = generate(TINYCALC_GRAMMAR);
        assertTrue("delimitor should reference SpaceParser",
            source.contains("SpaceParser"));
    }

    // =========================================================================
    // インポート
    // =========================================================================

    @Test
    public void testContainsCombinatorImport() {
        String source = generate(TINYCALC_GRAMMAR);
        assertTrue("should import combinator package",
            source.contains("import org.unlaxer.parser.combinator.*"));
    }

    @Test
    public void testContainsWordParserImport() {
        String source = generate(TINYCALC_GRAMMAR);
        assertTrue("should import WordParser",
            source.contains("import org.unlaxer.parser.elementary.WordParser"));
    }

    @Test
    public void testContainsParsersImport() {
        String source = generate(TINYCALC_GRAMMAR);
        assertTrue("should import Parsers",
            source.contains("import org.unlaxer.parser.Parsers"));
    }

    // =========================================================================
    // serialVersionUID
    // =========================================================================

    @Test
    public void testContainsSerialVersionUID() {
        String source = generate(TINYCALC_GRAMMAR);
        assertTrue("parser classes should have serialVersionUID",
            source.contains("serialVersionUID = 1L"));
    }

    // =========================================================================
    // ヘルパーメソッド
    // =========================================================================

    private GrammarDecl parseGrammar(String source) {
        return UBNFMapper.parse(source).grammars().get(0);
    }

    private String generate(String grammarSource) {
        GrammarDecl grammar = parseGrammar(grammarSource);
        ParserGenerator gen = new ParserGenerator();
        return gen.generate(grammar).source();
    }
}
