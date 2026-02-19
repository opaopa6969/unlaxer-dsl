package org.unlaxer.dsl.codegen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

    private static final String RIGHT_ASSOC_GRAMMAR =
        "grammar Pow {\n" +
        "  @package: org.example.pow\n" +
        "  @whitespace: javaStyle\n" +
        "  token NUMBER = NumberParser\n" +
        "  @root\n" +
        "  @mapping(PowNode, params=[left, op, right])\n" +
        "  @rightAssoc\n" +
        "  @precedence(level=30)\n" +
        "  Expr ::= Atom @left { '^' @op Atom @right } ;\n" +
        "  Atom ::= NUMBER ;\n" +
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
    public void testRightAssocRuleUsesRecursiveChoice() {
        String source = generate(RIGHT_ASSOC_GRAMMAR);
        assertTrue("right-assoc rule should be generated as choice",
            source.contains("class ExprParser extends LazyChoice"));
        assertTrue("right-assoc recursive branch should reference itself",
            source.contains("Parser.get(ExprParser.class)"));
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
    // 複合繰り返し体ヘルパー（バグ修正検証）
    // =========================================================================

    /** ネストしたグループを持つ最小文法 */
    private static final String NESTED_GROUP_GRAMMAR =
        "grammar TestNG {\n" +
        "  @package: test.generated\n" +
        "  @whitespace: javaStyle\n" +
        "  @root\n" +
        "  Rule ::= ( ( 'a' | 'b' ) 'c' ) ;\n" +
        "}";

    @Test
    public void testExpressionRepeat0ParserExists() {
        // ネストしたグループで inner helper (Group1Parser) が生成されることを確認
        String source = generate(NESTED_GROUP_GRAMMAR);
        assertTrue("should generate RuleGroup1Parser for inner group",
            source.contains("class RuleGroup1Parser"));
    }

    @Test
    public void testExpressionParserUsesZeroOrMoreRepeat() {
        // outer Group0Parser が inner Group1Parser を正しく参照することを確認
        String source = generate(NESTED_GROUP_GRAMMAR);
        assertTrue("RuleGroup0Parser should reference RuleGroup1Parser (counter bug fix)",
            source.contains("Parser.get(RuleGroup1Parser.class)"));
    }

    @Test
    public void testTermRepeat0ParserExists() {
        // outer helper (Group0Parser) も生成されることを確認
        String source = generate(NESTED_GROUP_GRAMMAR);
        assertTrue("should generate RuleGroup0Parser for outer group",
            source.contains("class RuleGroup0Parser"));
    }

    @Test
    public void testTermParserUsesZeroOrMoreRepeat() {
        // RuleParser が Group0Parser を正しく使用することを確認
        String source = generate(NESTED_GROUP_GRAMMAR);
        assertTrue("RuleParser should use Parser.get(RuleGroup0Parser.class)",
            source.contains("Parser.get(RuleGroup0Parser.class)"));
    }

    @Test
    public void testRepeat0ParserReferencesCorrectGroupParser() {
        // カウンターバグがない場合: Group2Parser は存在しない
        String source = generate(NESTED_GROUP_GRAMMAR);
        assertTrue("RuleGroup0Parser should exist",
            source.contains("class RuleGroup0Parser"));
        assertFalse("RuleGroup2Parser must not appear (would indicate counter bug)",
            source.contains("RuleGroup2Parser"));
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
