package org.unlaxer.dsl.codegen;

import org.unlaxer.dsl.bootstrap.UBNFAST.GrammarDecl;
import org.unlaxer.dsl.bootstrap.UBNFAST.LeftAssocAnnotation;
import org.unlaxer.dsl.bootstrap.UBNFAST.MappingAnnotation;
import org.unlaxer.dsl.bootstrap.UBNFAST.RootAnnotation;
import org.unlaxer.dsl.bootstrap.UBNFAST.RuleDecl;
import org.unlaxer.dsl.bootstrap.UBNFAST.StringSettingValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

        Optional<RuleDecl> rootRule = grammar.rules().stream()
            .filter(r -> r.annotations().stream().anyMatch(a -> a instanceof RootAnnotation))
            .findFirst();

        Map<String, RuleDecl> mappingRules = new LinkedHashMap<>();
        for (RuleDecl rule : grammar.rules()) {
            getMappingAnnotation(rule).ifPresent(m -> mappingRules.putIfAbsent(m.className(), rule));
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

        String rootClassName = rootRule.flatMap(this::getMappingAnnotation)
            .map(m -> astClass + "." + m.className())
            .orElse(astClass);

        sb.append("    // =========================================================================\n");
        sb.append("    // Entry Point\n");
        sb.append("    // =========================================================================\n\n");
        sb.append("    public static ").append(rootClassName).append(" parse(String source) {\n");
        sb.append("        // TODO: implement after ").append(parsersClass).append(" is generated\n");
        sb.append("        // StringSource stringSource = StringSource.createRootSource(source);\n");
        sb.append("        // try (ParseContext context = new ParseContext(stringSource)) {\n");
        sb.append("        //     Parser rootParser = ").append(parsersClass).append(".getRootParser();\n");
        sb.append("        //     Parsed parsed = rootParser.parse(context);\n");
        sb.append("        //     if (!parsed.isSucceeded()) {\n");
        sb.append("        //         throw new IllegalArgumentException(\"Parse failed: \" + source);\n");
        sb.append("        //     }\n");
        sb.append("        //     return to").append(rootRule.flatMap(this::getMappingAnnotation)
            .map(MappingAnnotation::className).orElse("Root"))
          .append("(parsed.getRootToken());\n");
        sb.append("        // }\n");
        sb.append("        throw new UnsupportedOperationException(\"")
          .append(parsersClass).append(": not implemented\");\n");
        sb.append("    }\n\n");

        sb.append("    // =========================================================================\n");
        sb.append("    // Mapping Methods\n");
        sb.append("    // =========================================================================\n\n");

        for (Map.Entry<String, RuleDecl> entry : mappingRules.entrySet()) {
            String className = entry.getKey();
            RuleDecl rule = entry.getValue();
            MappingAnnotation mapping = getMappingAnnotation(rule).orElseThrow();
            boolean leftAssoc = isLeftAssocRule(rule, mapping);
            String methodName = "to" + className;

            sb.append("    static ").append(astClass).append(".").append(className)
              .append(" ").append(methodName).append("(Token token) {\n");

            if (leftAssoc) {
                sb.append("        // TODO: extract left seed node from token\n");
                sb.append("        // TODO: extract repeated operators and right operands from token\n");
                sb.append("        ").append(astClass).append(".").append(className).append(" left = null;\n");
                sb.append("        List<String> ops = List.of();\n");
                sb.append("        List<").append(astClass).append(".").append(className).append("> rights = List.of();\n");
                sb.append("        return foldLeftAssoc").append(className).append("(left, ops, rights);\n");
            } else {
                for (String param : mapping.paramNames()) {
                    sb.append("        // TODO: extract ").append(param).append("\n");
                }
                sb.append("        return new ").append(astClass).append(".").append(className).append("(\n");
                for (int i = 0; i < mapping.paramNames().size(); i++) {
                    String suffix = i < mapping.paramNames().size() - 1 ? "," : "";
                    sb.append("            null").append(suffix).append(" // ").append(mapping.paramNames().get(i)).append("\n");
                }
                sb.append("        );\n");
            }
            sb.append("    }\n\n");
        }

        for (Map.Entry<String, RuleDecl> entry : mappingRules.entrySet()) {
            String className = entry.getKey();
            RuleDecl rule = entry.getValue();
            MappingAnnotation mapping = getMappingAnnotation(rule).orElseThrow();
            if (!isLeftAssocRule(rule, mapping)) {
                continue;
            }

            sb.append("    static ").append(astClass).append(".").append(className)
              .append(" foldLeftAssoc").append(className).append("(\n");
            sb.append("        ").append(astClass).append(".").append(className).append(" left,\n");
            sb.append("        List<String> ops,\n");
            sb.append("        List<").append(astClass).append(".").append(className).append("> rights\n");
            sb.append("    ) {\n");
            sb.append("        if (left == null) return null;\n");
            sb.append("        if (ops.size() != rights.size()) {\n");
            sb.append("            throw new IllegalArgumentException(\"ops/rights length mismatch\");\n");
            sb.append("        }\n");
            sb.append("        ").append(astClass).append(".").append(className).append(" current = left;\n");
            sb.append("        for (int i = 0; i < ops.size(); i++) {\n");
            sb.append("            current = new ").append(astClass).append(".").append(className)
              .append("(current, ops.get(i), rights.get(i));\n");
            sb.append("        }\n");
            sb.append("        return current;\n");
            sb.append("    }\n\n");
        }

        sb.append("    // =========================================================================\n");
        sb.append("    // Utilities\n");
        sb.append("    // =========================================================================\n\n");

        sb.append("    static List<Token> findDescendants(Token token, Class<? extends Parser> parserClass) {\n");
        sb.append("        List<Token> results = new ArrayList<>();\n");
        sb.append("        for (Token child : token.filteredChildren) {\n");
        sb.append("            if (child.parser.getClass() == parserClass) {\n");
        sb.append("                results.add(child);\n");
        sb.append("            } else {\n");
        sb.append("                results.addAll(findDescendants(child, parserClass));\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("        return results;\n");
        sb.append("    }\n\n");

        sb.append("    static String stripQuotes(String quoted) {\n");
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
}
