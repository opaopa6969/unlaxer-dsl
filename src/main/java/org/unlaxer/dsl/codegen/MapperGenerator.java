package org.unlaxer.dsl.codegen;

import org.unlaxer.dsl.bootstrap.UBNFAST.AnnotatedElement;
import org.unlaxer.dsl.bootstrap.UBNFAST.AtomicElement;
import org.unlaxer.dsl.bootstrap.UBNFAST.ChoiceBody;
import org.unlaxer.dsl.bootstrap.UBNFAST.GrammarDecl;
import org.unlaxer.dsl.bootstrap.UBNFAST.GroupElement;
import org.unlaxer.dsl.bootstrap.UBNFAST.LeftAssocAnnotation;
import org.unlaxer.dsl.bootstrap.UBNFAST.MappingAnnotation;
import org.unlaxer.dsl.bootstrap.UBNFAST.OptionalElement;
import org.unlaxer.dsl.bootstrap.UBNFAST.RepeatElement;
import org.unlaxer.dsl.bootstrap.UBNFAST.RightAssocAnnotation;
import org.unlaxer.dsl.bootstrap.UBNFAST.RootAnnotation;
import org.unlaxer.dsl.bootstrap.UBNFAST.RuleBody;
import org.unlaxer.dsl.bootstrap.UBNFAST.RuleDecl;
import org.unlaxer.dsl.bootstrap.UBNFAST.RuleRefElement;
import org.unlaxer.dsl.bootstrap.UBNFAST.SequenceBody;
import org.unlaxer.dsl.bootstrap.UBNFAST.StringSettingValue;
import org.unlaxer.dsl.bootstrap.UBNFAST.TerminalElement;
import org.unlaxer.dsl.bootstrap.UBNFAST.TokenDecl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * GrammarDecl から XxxMapper.java を生成する。
 */
public class MapperGenerator implements CodeGenerator {

    @Override
    public GeneratedSource generate(GrammarDecl grammar) {
        String packageName = getPackageName(grammar);
        String grammarName = grammar.name();
        String astClass = grammarName + "AST";
        String mapperClass = grammarName + "Mapper";
        String parsersClass = grammarName + "Parsers";

        Map<String, TokenDecl> tokenDeclByName = grammar.tokens().stream()
            .collect(Collectors.toMap(TokenDecl::name, t -> t, (a, b) -> a, LinkedHashMap::new));

        Map<String, RuleDecl> ruleByName = grammar.rules().stream()
            .collect(Collectors.toMap(RuleDecl::name, r -> r, (a, b) -> a, LinkedHashMap::new));

        Optional<RuleDecl> rootRule = grammar.rules().stream()
            .filter(r -> r.annotations().stream().anyMatch(a -> a instanceof RootAnnotation))
            .findFirst();

        Map<String, RuleDecl> mappingRules = new LinkedHashMap<>();
        Map<String, String> mappedClassByRuleName = new LinkedHashMap<>();
        for (RuleDecl rule : grammar.rules()) {
            getMappingAnnotation(rule).ifPresent(m -> {
                mappingRules.putIfAbsent(m.className(), rule);
                mappedClassByRuleName.putIfAbsent(rule.name(), m.className());
            });
        }

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(packageName).append(";\n\n");
        sb.append("import java.util.ArrayList;\n");
        sb.append("import java.util.List;\n");
        sb.append("import java.util.Optional;\n\n");
        sb.append("import org.unlaxer.Parsed;\n");
        sb.append("import org.unlaxer.StringSource;\n");
        sb.append("import org.unlaxer.Token;\n");
        sb.append("import org.unlaxer.context.ParseContext;\n");
        sb.append("import org.unlaxer.parser.Parser;\n\n");

        sb.append("/**\n");
        sb.append(" * ").append(grammarName).append(" parse tree (Token) -> ")
          .append(astClass).append(" mapper.\n");
        sb.append(" */\n");
        sb.append("public class ").append(mapperClass).append(" {\n\n");
        sb.append("    private ").append(mapperClass).append("() {}\n\n");
        sb.append("    private static final java.util.IdentityHashMap<Object, int[]> NODE_SOURCE_SPANS =\n");
        sb.append("        new java.util.IdentityHashMap<>();\n\n");

        String rootClassName = rootRule.flatMap(this::getMappingAnnotation)
            .map(m -> astClass + "." + m.className())
            .orElse(astClass);

        sb.append("    // =========================================================================\n");
        sb.append("    // Entry Point\n");
        sb.append("    // =========================================================================\n\n");
        sb.append("    public static ").append(rootClassName).append(" parse(String source) {\n");
        sb.append("        NODE_SOURCE_SPANS.clear();\n");
        sb.append("        Parser rootParser = ").append(parsersClass).append(".getRootParser();\n");
        sb.append("        ParseContext context = new ParseContext(createRootSourceCompat(source));\n");
        sb.append("        Parsed parsed;\n");
        sb.append("        try {\n");
        sb.append("            parsed = rootParser.parse(context);\n");
        sb.append("        } finally {\n");
        sb.append("            context.close();\n");
        sb.append("        }\n");
        sb.append("        if (!parsed.isSucceeded()) {\n");
        sb.append("            throw new IllegalArgumentException(\"Parse failed: \" + source);\n");
        sb.append("        }\n");
        sb.append("        int consumed = consumedLengthCompat(parsed.getConsumed());\n");
        sb.append("        if (consumed != source.length()) {\n");
        sb.append("            throw new IllegalArgumentException(\"Parse failed at offset \" + consumed + \": \" + source);\n");
        sb.append("        }\n");
        sb.append("        Token rootToken = parsed.getRootToken(true);\n");

        if (rootRule.isPresent() && getMappingAnnotation(rootRule.get()).isPresent()) {
            RuleDecl rr = rootRule.get();
            String rootParserClass = parsersClass + "." + rr.name() + "Parser.class";
            String rootMappingClass = getMappingAnnotation(rr).orElseThrow().className();
            sb.append("        Token mappingRoot = rootToken;\n");
            sb.append("        if (mappingRoot.parser.getClass() != ").append(rootParserClass).append(") {\n");
            sb.append("            mappingRoot = findFirstDescendant(mappingRoot, ").append(rootParserClass).append(");\n");
            sb.append("        }\n");
            sb.append("        if (mappingRoot == null) {\n");
            sb.append("            throw new IllegalArgumentException(\"Root mapping token not found for ").append(rr.name()).append("\");\n");
            sb.append("        }\n");
            sb.append("        return to").append(rootMappingClass).append("(mappingRoot);\n");
        } else {
            sb.append("        Token bestMappedToken = findBestMappedToken(rootToken);\n");
            sb.append("        ").append(astClass).append(" mapped = mapToken(bestMappedToken);\n");
            sb.append("        if (mapped == null) {\n");
            sb.append("            throw new IllegalArgumentException(\"No mapped node found in parse tree\");\n");
            sb.append("        }\n");
            sb.append("        return (").append(rootClassName).append(") mapped;\n");
        }
        sb.append("    }\n\n");

        sb.append("    private static ").append(astClass).append(" mapToken(Token token) {\n");
        sb.append("        if (token == null) {\n");
        sb.append("            return null;\n");
        sb.append("        }\n");
        for (Map.Entry<String, RuleDecl> entry : mappingRules.entrySet()) {
            String className = entry.getKey();
            RuleDecl rule = entry.getValue();
            sb.append("        if (token.parser.getClass() == ").append(parsersClass).append(".")
                .append(rule.name()).append("Parser.class) {\n");
            sb.append("            return to").append(className).append("(token);\n");
            sb.append("        }\n");
        }
        sb.append("        return null;\n");
        sb.append("    }\n\n");

        sb.append("    private static Token findBestMappedToken(Token token) {\n");
        sb.append("        MappingCandidate best = findBestMappedToken(token, 0, null);\n");
        sb.append("        return best == null ? null : best.token;\n");
        sb.append("    }\n\n");

        sb.append("    private static MappingCandidate findBestMappedToken(Token token, int depth, MappingCandidate best) {\n");
        sb.append("        if (token == null) {\n");
        sb.append("            return best;\n");
        sb.append("        }\n");
        sb.append("        ").append(astClass).append(" mapped = mapToken(token);\n");
        sb.append("        if (mapped != null) {\n");
        sb.append("            MappingCandidate candidate = new MappingCandidate(token, depth, tokenStartOffsetCompat(token));\n");
        sb.append("            best = betterCandidate(best, candidate);\n");
        sb.append("        }\n");
        sb.append("        for (Token child : token.filteredChildren) {\n");
        sb.append("            best = findBestMappedToken(child, depth + 1, best);\n");
        sb.append("        }\n");
        sb.append("        return best;\n");
        sb.append("    }\n\n");

        sb.append("    private static MappingCandidate betterCandidate(MappingCandidate current, MappingCandidate candidate) {\n");
        sb.append("        if (candidate == null) {\n");
        sb.append("            return current;\n");
        sb.append("        }\n");
        sb.append("        if (current == null) {\n");
        sb.append("            return candidate;\n");
        sb.append("        }\n");
        sb.append("        if (candidate.depth < current.depth) {\n");
        sb.append("            return candidate;\n");
        sb.append("        }\n");
        sb.append("        if (candidate.depth > current.depth) {\n");
        sb.append("            return current;\n");
        sb.append("        }\n");
        sb.append("        return candidate.startOffset >= current.startOffset ? candidate : current;\n");
        sb.append("    }\n\n");

        sb.append("    private static final class MappingCandidate {\n");
        sb.append("        private final Token token;\n");
        sb.append("        private final int depth;\n");
        sb.append("        private final int startOffset;\n\n");
        sb.append("        private MappingCandidate(Token token, int depth, int startOffset) {\n");
        sb.append("            this.token = token;\n");
        sb.append("            this.depth = depth;\n");
        sb.append("            this.startOffset = startOffset;\n");
        sb.append("        }\n");
        sb.append("    }\n\n");

        sb.append("    // =========================================================================\n");
        sb.append("    // Mapping Methods\n");
        sb.append("    // =========================================================================\n\n");

        for (Map.Entry<String, RuleDecl> entry : mappingRules.entrySet()) {
            String className = entry.getKey();
            RuleDecl rule = entry.getValue();
            MappingAnnotation mapping = getMappingAnnotation(rule).orElseThrow();
            boolean leftAssoc = isLeftAssocRule(rule, mapping);
            boolean rightAssoc = isRightAssocRule(rule, mapping);

            sb.append("    static ").append(astClass).append(".").append(className)
              .append(" to").append(className).append("(Token token) {\n");

            if (leftAssoc || rightAssoc) {
                Optional<AssocShape> assocShapeOpt = findAssocShape(rule, "left", "op", "right");
                if (assocShapeOpt.isPresent()) {
                    AssocShape assocShape = assocShapeOpt.get();

                    String leftType = inferType(grammar, rule, "left");
                    String opType = unwrapListType(inferType(grammar, rule, "op")).orElse("String");
                    String rightType = unwrapListType(inferType(grammar, rule, "right")).orElse("Object");
                    boolean leafFallbackSupported =
                        (astClass + "." + className).equals(leftType)
                        && "String".equals(opType)
                        && (astClass + "." + className).equals(rightType);

                    String ruleParserClass = parsersClass + "." + rule.name() + "Parser.class";
                    String repeatParserClass = parsersClass + "." + rule.name() + "Repeat" + assocShape.repeatIndex + "Parser.class";
                    String leftParserClass = parserClassLiteral(assocShape.leftElement, parsersClass, tokenDeclByName, ruleByName)
                        .orElse(ruleParserClass);
                    String rightParserClass = parserClassLiteral(assocShape.rightElement, parsersClass, tokenDeclByName, ruleByName)
                        .orElse(ruleParserClass);

                    String leftMapper = mapExpressionForElement(
                        assocShape.leftElement,
                        "leftToken",
                        mappedClassByRuleName,
                        tokenDeclByName,
                        ruleByName);
                    String rightMapper = mapExpressionForElement(
                        assocShape.rightElement,
                        "rightToken",
                        mappedClassByRuleName,
                        tokenDeclByName,
                        ruleByName);

                    sb.append("        Token working = token;\n");
                    sb.append("        if (working.parser.getClass() != ").append(ruleParserClass).append(") {\n");
                    sb.append("            working = findFirstDescendant(working, ").append(ruleParserClass).append(");\n");
                    sb.append("        }\n");
                    sb.append("        if (working == null) {\n");
                    if (leafFallbackSupported) {
                        sb.append("            String literal = stripQuotes(firstTokenText(token));\n");
                        sb.append("            literal = literal == null ? \"\" : literal;\n");
                        sb.append("            return registerNodeSourceSpan(new ").append(astClass).append(".").append(className)
                            .append("(null, List.of(literal), List.of()), token);\n");
                    } else {
                        sb.append("            throw new IllegalArgumentException(\"Mapping token not found for rule ").append(rule.name()).append("\");\n");
                    }
                    sb.append("        }\n");
                    sb.append("        Token leftToken = findFirstDescendant(working, ").append(leftParserClass).append(");\n");
                    sb.append("        if (leftToken == null) {\n");
                    if (leafFallbackSupported) {
                        sb.append("            String literal = stripQuotes(firstTokenText(working));\n");
                        sb.append("            literal = literal == null ? \"\" : literal;\n");
                        sb.append("            return registerNodeSourceSpan(new ").append(astClass).append(".").append(className)
                            .append("(null, List.of(literal), List.of()), working);\n");
                    } else {
                        sb.append("            throw new IllegalArgumentException(\"Left operand not found for rule ").append(rule.name()).append("\");\n");
                    }
                    sb.append("        }\n");
                    sb.append("        ").append(leftType).append(" left = ").append(leftMapper).append(";\n");
                    sb.append("        List<").append(opType).append("> op = new ArrayList<>();\n");
                    sb.append("        List<").append(rightType).append("> right = new ArrayList<>();\n");
                    sb.append("        for (Token repeatToken : findDescendants(working, ").append(repeatParserClass).append(")) {\n");
                    sb.append("            String opValue = firstTokenText(repeatToken);\n");
                    sb.append("            if (opValue != null && !opValue.isEmpty()) {\n");
                    sb.append("                op.add(stripQuotes(opValue));\n");
                    sb.append("            }\n");
                    sb.append("            Token rightToken = findFirstDescendant(repeatToken, ").append(rightParserClass).append(");\n");
                    sb.append("            if (rightToken != null) {\n");
                    sb.append("                right.add(").append(rightMapper).append(");\n");
                    sb.append("            }\n");
                    sb.append("        }\n");
                    sb.append("        return registerNodeSourceSpan(new ").append(astClass).append(".").append(className).append("(left, op, right), working);\n");
                } else {
                    sb.append("        throw new IllegalArgumentException(\"Unsupported assoc mapping shape for rule: ")
                      .append(rule.name()).append("\");\n");
                }
            } else {
                String ruleParserClass = parsersClass + "." + rule.name() + "Parser.class";
                for (String param : mapping.paramNames()) {
                    String type = inferType(grammar, rule, param);
                    Optional<AtomicElement> capturedElement = findCapturedElement(rule.body(), param);
                    if (capturedElement.isEmpty()) {
                        sb.append("        ").append(type).append(" ").append(param)
                            .append(" = ").append(defaultValueForType(type)).append(";\n");
                        continue;
                    }

                    AtomicElement element = capturedElement.get();
                    AtomicElement normalized = normalizeCapturedElement(element).orElse(element);
                    String parserClass = parserClassLiteral(normalized, parsersClass, tokenDeclByName, ruleByName)
                        .orElse(ruleParserClass);
                    String tokenVarName = "paramToken_" + safeName(param);
                    String mapExpression = mapExpressionForElement(
                        normalized,
                        tokenVarName,
                        mappedClassByRuleName,
                        tokenDeclByName,
                        ruleByName);

                    Optional<String> listElementType = unwrapListType(type);
                    if (listElementType.isPresent()) {
                        sb.append("        List<").append(listElementType.get()).append("> ").append(param)
                            .append(" = new ArrayList<>();\n");
                        sb.append("        for (Token ").append(tokenVarName)
                            .append(" : findDescendants(token, ").append(parserClass).append(")) {\n");
                        sb.append("            ").append(param).append(".add(").append(mapExpression).append(");\n");
                        sb.append("        }\n");
                        continue;
                    }

                    Optional<String> optionalElementType = unwrapOptionalType(type);
                    if (optionalElementType.isPresent()) {
                        sb.append("        Optional<").append(optionalElementType.get()).append("> ").append(param)
                            .append(" = Optional.empty();\n");
                        sb.append("        Token ").append(tokenVarName)
                            .append(" = findFirstDescendant(token, ").append(parserClass).append(");\n");
                        sb.append("        if (").append(tokenVarName).append(" != null) {\n");
                        sb.append("            ").append(param).append(" = Optional.ofNullable(").append(mapExpression).append(");\n");
                        sb.append("        }\n");
                        continue;
                    }

                    sb.append("        ").append(type).append(" ").append(param)
                        .append(" = ").append(defaultValueForType(type)).append(";\n");
                    sb.append("        Token ").append(tokenVarName)
                        .append(" = findFirstDescendant(token, ").append(parserClass).append(");\n");
                    sb.append("        if (").append(tokenVarName).append(" != null) {\n");
                    sb.append("            ").append(param).append(" = ").append(mapExpression).append(";\n");
                    sb.append("        }\n");
                }
                sb.append("        ").append(astClass).append(".").append(className).append(" mapped = new ")
                    .append(astClass).append(".").append(className).append("(\n");
                for (int i = 0; i < mapping.paramNames().size(); i++) {
                    String param = mapping.paramNames().get(i);
                    String suffix = i < mapping.paramNames().size() - 1 ? "," : "";
                    sb.append("            ").append(param).append(suffix)
                        .append(" // ").append(param).append("\n");
                }
                sb.append("        );\n");
                sb.append("        return registerNodeSourceSpan(mapped, token);\n");
            }

            sb.append("    }\n\n");
        }

        sb.append("    // =========================================================================\n");
        sb.append("    // Utilities\n");
        sb.append("    // =========================================================================\n\n");

        sb.append("    static List<Token> findDescendants(Token token, Class<? extends Parser> parserClass) {\n");
        sb.append("        List<Token> results = new ArrayList<>();\n");
        sb.append("        if (token == null) {\n");
        sb.append("            return results;\n");
        sb.append("        }\n");
        sb.append("        for (Token child : token.filteredChildren) {\n");
        sb.append("            if (child.parser.getClass() == parserClass) {\n");
        sb.append("                results.add(child);\n");
        sb.append("            }\n");
        sb.append("            results.addAll(findDescendants(child, parserClass));\n");
        sb.append("        }\n");
        sb.append("        return results;\n");
        sb.append("    }\n\n");

        sb.append("    static Token findFirstDescendant(Token token, Class<? extends Parser> parserClass) {\n");
        sb.append("        if (token == null) {\n");
        sb.append("            return null;\n");
        sb.append("        }\n");
        sb.append("        if (token.parser.getClass() == parserClass) {\n");
        sb.append("            return token;\n");
        sb.append("        }\n");
        sb.append("        for (Token child : token.filteredChildren) {\n");
        sb.append("            Token found = findFirstDescendant(child, parserClass);\n");
        sb.append("            if (found != null) {\n");
        sb.append("                return found;\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("        return null;\n");
        sb.append("    }\n\n");

        sb.append("    static String firstTokenText(Token token) {\n");
        sb.append("        if (token == null) {\n");
        sb.append("            return null;\n");
        sb.append("        }\n");
        sb.append("        String raw = tokenTextCompat(token);\n");
        sb.append("        if (raw != null && !raw.isBlank()) {\n");
        sb.append("            return raw.strip();\n");
        sb.append("        }\n");
        sb.append("        for (Token child : token.filteredChildren) {\n");
        sb.append("            String found = firstTokenText(child);\n");
        sb.append("            if (found != null && !found.isEmpty()) {\n");
        sb.append("                return found;\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("        return raw == null ? null : raw.strip();\n");
        sb.append("    }\n\n");

        sb.append("    static String tokenTextCompat(Token token) {\n");
        sb.append("        if (token == null) {\n");
        sb.append("            return null;\n");
        sb.append("        }\n");
        sb.append("        try {\n");
        sb.append("            java.lang.reflect.Method m = token.getClass().getMethod(\"getToken\");\n");
        sb.append("            Object value = m.invoke(token);\n");
        sb.append("            if (value instanceof Optional<?> optional && optional.isPresent()) {\n");
        sb.append("                Object v = optional.get();\n");
        sb.append("                return v == null ? null : String.valueOf(v);\n");
        sb.append("            }\n");
        sb.append("        } catch (Throwable ignored) {}\n");
        sb.append("        try {\n");
        sb.append("            java.lang.reflect.Field f = token.getClass().getField(\"tokenString\");\n");
        sb.append("            Object value = f.get(token);\n");
        sb.append("            if (value instanceof Optional<?> optional && optional.isPresent()) {\n");
        sb.append("                Object v = optional.get();\n");
        sb.append("                return v == null ? null : String.valueOf(v);\n");
        sb.append("            }\n");
        sb.append("        } catch (Throwable ignored) {}\n");
        sb.append("        try {\n");
        sb.append("            java.lang.reflect.Field f = token.getClass().getField(\"source\");\n");
        sb.append("            Object src = f.get(token);\n");
        sb.append("            if (src != null) {\n");
        sb.append("                java.lang.reflect.Method m = src.getClass().getMethod(\"sourceAsString\");\n");
        sb.append("                Object v = m.invoke(src);\n");
        sb.append("                return v == null ? null : String.valueOf(v);\n");
        sb.append("            }\n");
        sb.append("        } catch (Throwable ignored) {}\n");
        sb.append("        return null;\n");
        sb.append("    }\n\n");

        sb.append("    static int consumedLengthCompat(Token token) {\n");
        sb.append("        String text = tokenTextCompat(token);\n");
        sb.append("        return text == null ? 0 : text.length();\n");
        sb.append("    }\n\n");

        sb.append("    static int tokenStartOffsetCompat(Token token) {\n");
        sb.append("        if (token == null) {\n");
        sb.append("            return 0;\n");
        sb.append("        }\n");
        sb.append("        try {\n");
        sb.append("            java.lang.reflect.Field sourceField = token.getClass().getField(\"source\");\n");
        sb.append("            Object source = sourceField.get(token);\n");
        sb.append("            if (source == null) {\n");
        sb.append("                return 0;\n");
        sb.append("            }\n");
        sb.append("            java.lang.reflect.Method offsetMethod = source.getClass().getMethod(\"offsetFromRoot\");\n");
        sb.append("            Object offset = offsetMethod.invoke(source);\n");
        sb.append("            if (offset == null) {\n");
        sb.append("                return 0;\n");
        sb.append("            }\n");
        sb.append("            java.lang.reflect.Method valueMethod = offset.getClass().getMethod(\"value\");\n");
        sb.append("            Object value = valueMethod.invoke(offset);\n");
        sb.append("            if (value instanceof Integer i) {\n");
        sb.append("                return i;\n");
        sb.append("            }\n");
        sb.append("            if (value instanceof Number n) {\n");
        sb.append("                return n.intValue();\n");
        sb.append("            }\n");
        sb.append("        } catch (Throwable ignored) {}\n");
        sb.append("        return 0;\n");
        sb.append("    }\n\n");

        sb.append("    static <T> T registerNodeSourceSpan(T node, Token token) {\n");
        sb.append("        if (node == null || token == null) {\n");
        sb.append("            return node;\n");
        sb.append("        }\n");
        sb.append("        int start = Math.max(0, tokenStartOffsetCompat(token));\n");
        sb.append("        int length = Math.max(0, consumedLengthCompat(token));\n");
        sb.append("        int end = start + length;\n");
        sb.append("        NODE_SOURCE_SPANS.put(node, new int[]{start, end});\n");
        sb.append("        return node;\n");
        sb.append("    }\n\n");

        sb.append("    public static Optional<int[]> sourceSpanOf(Object node) {\n");
        sb.append("        if (node == null) {\n");
        sb.append("            return Optional.empty();\n");
        sb.append("        }\n");
        sb.append("        int[] span = NODE_SOURCE_SPANS.get(node);\n");
        sb.append("        if (span == null || span.length < 2) {\n");
        sb.append("            return Optional.empty();\n");
        sb.append("        }\n");
        sb.append("        return Optional.of(new int[]{span[0], span[1]});\n");
        sb.append("    }\n\n");

        sb.append("    static StringSource createRootSourceCompat(String source) {\n");
        sb.append("        try {\n");
        sb.append("            java.lang.reflect.Method m = StringSource.class.getMethod(\"createRootSource\", String.class);\n");
        sb.append("            Object v = m.invoke(null, source);\n");
        sb.append("            if (v instanceof StringSource s) {\n");
        sb.append("                return s;\n");
        sb.append("            }\n");
        sb.append("        } catch (Throwable ignored) {}\n");
        sb.append("        try {\n");
        sb.append("            for (java.lang.reflect.Constructor<?> c : StringSource.class.getDeclaredConstructors()) {\n");
        sb.append("                Class<?>[] types = c.getParameterTypes();\n");
        sb.append("                if (types.length == 0 || types[0] != String.class) {\n");
        sb.append("                    continue;\n");
        sb.append("                }\n");
        sb.append("                Object[] args = new Object[types.length];\n");
        sb.append("                args[0] = source;\n");
        sb.append("                c.setAccessible(true);\n");
        sb.append("                Object v = c.newInstance(args);\n");
        sb.append("                if (v instanceof StringSource s) {\n");
        sb.append("                    return s;\n");
        sb.append("                }\n");
        sb.append("            }\n");
        sb.append("        } catch (Throwable ignored) {}\n");
        sb.append("        throw new IllegalStateException(\"No compatible StringSource initializer found\");\n");
        sb.append("    }\n\n");

        sb.append("    static String stripQuotes(String quoted) {\n");
        sb.append("        if (quoted == null) {\n");
        sb.append("            return null;\n");
        sb.append("        }\n");
        sb.append("        if (quoted.length() >= 2\n");
        sb.append("            && '\\'' == quoted.charAt(0)\n");
        sb.append("            && '\\'' == quoted.charAt(quoted.length() - 1)) {\n");
        sb.append("            return quoted.substring(1, quoted.length() - 1);\n");
        sb.append("        }\n");
        sb.append("        return quoted;\n");
        sb.append("    }\n");

        sb.append("}\n");

        return new GeneratedSource(packageName, mapperClass, sb.toString());
    }

    private boolean isLeftAssocRule(RuleDecl rule, MappingAnnotation mapping) {
        boolean hasLeftAssoc = rule.annotations().stream().anyMatch(a -> a instanceof LeftAssocAnnotation);
        if (!hasLeftAssoc) {
            return false;
        }
        List<String> params = mapping.paramNames();
        return params.contains("left") && params.contains("op") && params.contains("right");
    }

    private boolean isRightAssocRule(RuleDecl rule, MappingAnnotation mapping) {
        boolean hasRightAssoc = rule.annotations().stream().anyMatch(a -> a instanceof RightAssocAnnotation);
        if (!hasRightAssoc) {
            return false;
        }
        List<String> params = mapping.paramNames();
        return params.contains("left") && params.contains("op") && params.contains("right");
    }

    private Optional<AssocShape> findAssocShape(RuleDecl rule, String leftCapture, String opCapture, String rightCapture) {
        SequenceBody sequence = firstSequence(rule.body()).orElse(null);
        if (sequence == null) {
            return Optional.empty();
        }

        AtomicElement leftElement = findCapturedElement(rule.body(), leftCapture).orElse(null);
        int repeatIndex = 0;
        for (AnnotatedElement element : sequence.elements()) {
            AtomicElement atomic = element.element();
            if (atomic instanceof RepeatElement repeatElement) {
                Optional<AtomicElement> opElement = findCapturedElement(repeatElement.body(), opCapture);
                Optional<AtomicElement> rightElement = findCapturedElement(repeatElement.body(), rightCapture);
                if (opElement.isPresent() && rightElement.isPresent()) {
                    if (leftElement != null) {
                        return Optional.of(new AssocShape(leftElement, opElement.get(), rightElement.get(), repeatIndex));
                    }
                    return Optional.empty();
                }
                repeatIndex++;
            }
        }
        return Optional.empty();
    }

    private Optional<SequenceBody> firstSequence(RuleBody body) {
        return switch (body) {
            case SequenceBody sequenceBody -> Optional.of(sequenceBody);
            case ChoiceBody choiceBody -> choiceBody.alternatives().stream().findFirst();
        };
    }

    private Optional<AtomicElement> findCapturedElement(RuleBody body, String captureName) {
        return switch (body) {
            case ChoiceBody choiceBody -> choiceBody.alternatives().stream()
                .flatMap(alt -> findCapturedElement(alt, captureName).stream())
                .findFirst();
            case SequenceBody sequenceBody -> {
                for (AnnotatedElement element : sequenceBody.elements()) {
                    if (element.captureName().isPresent() && captureName.equals(element.captureName().get())) {
                        yield Optional.of(element.element());
                    }
                    Optional<AtomicElement> nested = findCapturedElement(element.element(), captureName);
                    if (nested.isPresent()) {
                        yield nested;
                    }
                }
                yield Optional.empty();
            }
        };
    }

    private Optional<AtomicElement> findCapturedElement(AtomicElement element, String captureName) {
        return switch (element) {
            case GroupElement groupElement -> findCapturedElement(groupElement.body(), captureName);
            case OptionalElement optionalElement -> findCapturedElement(optionalElement.body(), captureName);
            case RepeatElement repeatElement -> findCapturedElement(repeatElement.body(), captureName);
            default -> Optional.empty();
        };
    }

    private Optional<String> parserClassLiteral(AtomicElement element, String parsersClass,
        Map<String, TokenDecl> tokenDeclByName, Map<String, RuleDecl> ruleByName) {

        return switch (element) {
            case RuleRefElement ruleRefElement -> {
                if (ruleByName.containsKey(ruleRefElement.name())) {
                    yield Optional.of(parsersClass + "." + ruleRefElement.name() + "Parser.class");
                }
                if (tokenDeclByName.containsKey(ruleRefElement.name())) {
                    yield Optional.empty();
                }
                yield Optional.empty();
            }
            case TerminalElement ignored -> Optional.of("org.unlaxer.parser.elementary.WordParser.class");
            default -> Optional.empty();
        };
    }

    private String mapExpressionForElement(AtomicElement element, String tokenVar,
        Map<String, String> mappedClassByRuleName,
        Map<String, TokenDecl> tokenDeclByName,
        Map<String, RuleDecl> ruleByName) {

        if (element instanceof RuleRefElement ruleRefElement) {
            String name = ruleRefElement.name();
            if (mappedClassByRuleName.containsKey(name)) {
                return "to" + mappedClassByRuleName.get(name) + "(" + tokenVar + ")";
            }
            if (tokenDeclByName.containsKey(name) || ruleByName.containsKey(name)) {
                return "stripQuotes(firstTokenText(" + tokenVar + "))";
            }
        }
        if (element instanceof TerminalElement) {
            return "stripQuotes(firstTokenText(" + tokenVar + "))";
        }
        return "stripQuotes(firstTokenText(" + tokenVar + "))";
    }

    private Optional<String> unwrapListType(String type) {
        if (type.startsWith("List<") && type.endsWith(">")) {
            return Optional.of(type.substring("List<".length(), type.length() - 1));
        }
        return Optional.empty();
    }

    private Optional<String> unwrapOptionalType(String type) {
        if (type.startsWith("Optional<") && type.endsWith(">")) {
            return Optional.of(type.substring("Optional<".length(), type.length() - 1));
        }
        return Optional.empty();
    }

    private Optional<AtomicElement> normalizeCapturedElement(AtomicElement element) {
        return switch (element) {
            case GroupElement groupElement -> firstAtomicElement(groupElement.body());
            case OptionalElement optionalElement -> firstAtomicElement(optionalElement.body());
            case RepeatElement repeatElement -> firstAtomicElement(repeatElement.body());
            default -> Optional.of(element);
        };
    }

    private Optional<AtomicElement> firstAtomicElement(RuleBody body) {
        return switch (body) {
            case SequenceBody sequenceBody -> sequenceBody.elements().stream()
                .findFirst()
                .map(AnnotatedElement::element)
                .flatMap(this::normalizeCapturedElement);
            case ChoiceBody choiceBody -> choiceBody.alternatives().stream()
                .findFirst()
                .flatMap(this::firstAtomicElement);
        };
    }

    private String safeName(String name) {
        return name.replaceAll("[^A-Za-z0-9_]", "_");
    }

    private String defaultValueForType(String type) {
        if (type.startsWith("List<")) {
            return "List.of()";
        }
        if (type.startsWith("Optional<")) {
            return "Optional.empty()";
        }
        if ("String".equals(type)) {
            return "\"\"";
        }
        return "null";
    }

    private Optional<MappingAnnotation> getMappingAnnotation(RuleDecl rule) {
        return rule.annotations().stream()
            .filter(a -> a instanceof MappingAnnotation)
            .map(a -> (MappingAnnotation) a)
            .findFirst();
    }

    private String getPackageName(GrammarDecl grammar) {
        return grammar.settings().stream()
            .filter(s -> "package".equals(s.key()))
            .map(s -> s.value() instanceof StringSettingValue sv ? sv.value() : "")
            .findFirst()
            .orElse("generated");
    }

    // inferType logic is borrowed from ASTGenerator to keep generated constructor argument types compile-safe.
    private String inferType(GrammarDecl grammar, RuleDecl rule, String fieldName) {
        Optional<CaptureResult> result = findCapturedType(rule.body(), fieldName);
        if (result.isEmpty()) {
            return "Object";
        }
        CaptureResult capture = result.get();
        String innerType = inferTypeFromElement(grammar, capture.element());
        if (capture.inOptional()) {
            return "Optional<" + innerType + ">";
        }
        if (capture.inRepeat()) {
            return "List<" + innerType + ">";
        }
        return innerType;
    }

    private String inferTypeFromElement(GrammarDecl grammar, AtomicElement element) {
        String astClassName = grammar.name() + "AST";
        return switch (element) {
            case TerminalElement ignored -> "String";
            case RuleRefElement ruleRefElement -> {
                Optional<MappingAnnotation> mapping = grammar.rules().stream()
                    .filter(r -> r.name().equals(ruleRefElement.name()))
                    .flatMap(r -> r.annotations().stream())
                    .filter(a -> a instanceof MappingAnnotation)
                    .map(a -> (MappingAnnotation) a)
                    .findFirst();
                yield mapping.map(m -> astClassName + "." + m.className()).orElse("String");
            }
            case RepeatElement repeatElement -> {
                String inner = inferTypeFromBody(grammar, repeatElement.body());
                yield "List<" + inner + ">";
            }
            case OptionalElement optionalElement -> {
                String inner = inferTypeFromBody(grammar, optionalElement.body());
                yield "Optional<" + inner + ">";
            }
            case GroupElement ignored -> "Object";
        };
    }

    private String inferTypeFromBody(GrammarDecl grammar, RuleBody body) {
        AnnotatedElement single = switch (body) {
            case SequenceBody sequenceBody when sequenceBody.elements().size() == 1 -> sequenceBody.elements().get(0);
            case ChoiceBody choiceBody when choiceBody.alternatives().size() == 1 -> {
                SequenceBody sequenceBody = choiceBody.alternatives().get(0);
                yield sequenceBody.elements().size() == 1 ? sequenceBody.elements().get(0) : null;
            }
            default -> null;
        };
        if (single == null) {
            return "Object";
        }
        return inferTypeFromElement(grammar, single.element());
    }

    private Optional<CaptureResult> findCapturedType(RuleBody body, String captureName) {
        return findCapturedTypeInBody(body, captureName, false, false);
    }

    private Optional<CaptureResult> findCapturedTypeInBody(
        RuleBody body, String captureName, boolean inOptional, boolean inRepeat) {

        return switch (body) {
            case ChoiceBody choiceBody -> choiceBody.alternatives().stream()
                .flatMap(sequenceBody -> findCapturedTypeInSequence(sequenceBody, captureName, inOptional, inRepeat).stream())
                .findFirst();
            case SequenceBody sequenceBody -> findCapturedTypeInSequence(sequenceBody, captureName, inOptional, inRepeat);
        };
    }

    private Optional<CaptureResult> findCapturedTypeInSequence(
        SequenceBody sequenceBody, String captureName, boolean inOptional, boolean inRepeat) {

        for (AnnotatedElement element : sequenceBody.elements()) {
            if (element.captureName().isPresent() && element.captureName().get().equals(captureName)) {
                return Optional.of(new CaptureResult(element.element(), inOptional, inRepeat));
            }
            Optional<CaptureResult> nested = findCapturedTypeInAtomic(element.element(), captureName, inOptional, inRepeat);
            if (nested.isPresent()) {
                return nested;
            }
        }
        return Optional.empty();
    }

    private Optional<CaptureResult> findCapturedTypeInAtomic(
        AtomicElement element, String captureName, boolean inOptional, boolean inRepeat) {

        return switch (element) {
            case OptionalElement optionalElement ->
                findCapturedTypeInBody(optionalElement.body(), captureName, true, inRepeat);
            case RepeatElement repeatElement ->
                findCapturedTypeInBody(repeatElement.body(), captureName, inOptional, true);
            case GroupElement groupElement ->
                findCapturedTypeInBody(groupElement.body(), captureName, inOptional, inRepeat);
            default -> Optional.empty();
        };
    }

    private record CaptureResult(AtomicElement element, boolean inOptional, boolean inRepeat) {}

    private record AssocShape(AtomicElement leftElement, AtomicElement opElement, AtomicElement rightElement,
                              int repeatIndex) {}
}
