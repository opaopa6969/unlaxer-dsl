package org.unlaxer.dsl.codegen;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.unlaxer.dsl.bootstrap.UBNFAST.GrammarDecl;
import org.unlaxer.dsl.bootstrap.UBNFMapper;

public class GrammarValidatorTest {

    @Test
    public void testValidMappingPasses() {
        GrammarDecl grammar = parseGrammar(
            "grammar G {\n"
                + "  @package: org.example\n"
                + "  @root\n"
                + "  @mapping(Root, params=[value])\n"
                + "  Start ::= 'ok' @value ;\n"
                + "}"
        );

        GrammarValidator.validateOrThrow(grammar);
    }

    @Test
    public void testMissingCaptureFails() {
        GrammarDecl grammar = parseGrammar(
            "grammar G {\n"
                + "  @package: org.example\n"
                + "  @root\n"
                + "  @mapping(Root, params=[value, missing])\n"
                + "  Start ::= 'ok' @value ;\n"
                + "}"
        );

        try {
            GrammarValidator.validateOrThrow(grammar);
            fail("expected validation error");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("param 'missing' has no matching capture"));
        }
    }

    @Test
    public void testUnlistedCaptureFails() {
        GrammarDecl grammar = parseGrammar(
            "grammar G {\n"
                + "  @package: org.example\n"
                + "  @root\n"
                + "  @mapping(Root, params=[left])\n"
                + "  Start ::= 'a' @left 'b' @right ;\n"
                + "}"
        );

        try {
            GrammarValidator.validateOrThrow(grammar);
            fail("expected validation error");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("capture @right not listed"));
        }
    }

    @Test
    public void testDuplicateParamsFails() {
        GrammarDecl grammar = parseGrammar(
            "grammar G {\n"
                + "  @package: org.example\n"
                + "  @root\n"
                + "  @mapping(Root, params=[value, value])\n"
                + "  Start ::= 'ok' @value ;\n"
                + "}"
        );

        try {
            GrammarValidator.validateOrThrow(grammar);
            fail("expected validation error");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("duplicate params"));
        }
    }

    @Test
    public void testLeftAssocWithoutRepeatFails() {
        GrammarDecl grammar = parseGrammar(
            "grammar G {\n"
                + "  @package: org.example\n"
                + "  @root\n"
                + "  @mapping(Bin, params=[left, op, right])\n"
                + "  @leftAssoc\n"
                + "  Expr ::= 'a' @left '+' @op 'b' @right ;\n"
                + "}"
        );

        try {
            GrammarValidator.validateOrThrow(grammar);
            fail("expected validation error");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("has no repeat segment"));
        }
    }

    @Test
    public void testLeftAssocWithoutMappingFails() {
        GrammarDecl grammar = parseGrammar(
            "grammar G {\n"
                + "  @package: org.example\n"
                + "  @root\n"
                + "  @leftAssoc\n"
                + "  Expr ::= Term @left { '+' @op Term @right } ;\n"
                + "  Term ::= 'n' ;\n"
                + "}"
        );

        try {
            GrammarValidator.validateOrThrow(grammar);
            fail("expected validation error");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("uses @leftAssoc but has no @mapping"));
        }
    }

    @Test
    public void testRuleWhitespaceUnknownStyleFails() {
        GrammarDecl grammar = parseGrammar(
            "grammar G {\n"
                + "  @package: org.example\n"
                + "  @root\n"
                + "  @whitespace(custom)\n"
                + "  Start ::= 'ok' ;\n"
                + "}"
        );

        try {
            GrammarValidator.validateOrThrow(grammar);
            fail("expected validation error");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("unsupported @whitespace style"));
        }
    }

    @Test
    public void testGlobalWhitespaceUnknownStyleFails() {
        GrammarDecl grammar = parseGrammar(
            "grammar G {\n"
                + "  @package: org.example\n"
                + "  @whitespace: custom\n"
                + "  @root\n"
                + "  Start ::= 'ok' ;\n"
                + "}"
        );

        try {
            GrammarValidator.validateOrThrow(grammar);
            fail("expected validation error");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("global @whitespace style must be javaStyle"));
        }
    }

    private GrammarDecl parseGrammar(String source) {
        return UBNFMapper.parse(source).grammars().get(0);
    }
}
