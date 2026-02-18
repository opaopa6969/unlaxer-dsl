package org.unlaxer.dsl.codegen;

import org.unlaxer.dsl.bootstrap.UBNFAST.GrammarDecl;
import org.unlaxer.dsl.bootstrap.UBNFAST.MappingAnnotation;
import org.unlaxer.dsl.bootstrap.UBNFAST.RootAnnotation;
import org.unlaxer.dsl.bootstrap.UBNFAST.RuleDecl;
import org.unlaxer.dsl.bootstrap.UBNFAST.StringSettingValue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * GrammarDecl から XxxMapper.java を生成する。
 *
 * <p>手書きの UBNFMapper.java と同じ構造を持つクラスを生成する。
 * 各 @mapping ルールに対応する toXxx メソッドと、findDescendants / stripQuotes
 * ユーティリティメソッドを含む。</p>
 *
 * <p>注意: 生成されたコードは XxxParsers（ParserGenerator で Phase 4 以降に生成）
 * を参照するため、ParserGenerator が実装されるまで TODO マーカーを含む。</p>
 */
public class MapperGenerator implements CodeGenerator {

    @Override
    public GeneratedSource generate(GrammarDecl grammar) {
        String packageName = getPackageName(grammar);
        String grammarName = grammar.name();
        String astClass = grammarName + "AST";
        String mapperClass = grammarName + "Mapper";
        String parsersClass = grammarName + "Parsers";

        // ルートルールと @mapping ルールを収集
        Optional<RuleDecl> rootRule = grammar.rules().stream()
            .filter(r -> r.annotations().stream().anyMatch(a -> a instanceof RootAnnotation))
            .findFirst();

        // クラス名で重複排除した @mapping ルールを収集
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
        sb.append(" * ").append(grammarName).append(" パースツリー（Token）を ")
          .append(astClass).append(" ノードに変換するマッパー。\n");
        sb.append(" *\n");
        sb.append(" * NOTE: ").append(parsersClass)
          .append(" は Phase 4 以降で生成されるため、parse メソッドは現在未実装です。\n");
        sb.append(" */\n");
        sb.append("public class ").append(mapperClass).append(" {\n\n");
        sb.append("    private ").append(mapperClass).append("() {}\n\n");

        // エントリーポイント: parse
        String rootClassName = rootRule.flatMap(r -> getMappingAnnotation(r))
            .map(m -> astClass + "." + m.className())
            .orElse(astClass);

        sb.append("    // =========================================================================\n");
        sb.append("    // エントリーポイント\n");
        sb.append("    // =========================================================================\n\n");
        sb.append("    /**\n");
        sb.append("     * ").append(grammarName).append(" ソース文字列をパースして AST に変換する。\n");
        sb.append("     *\n");
        sb.append("     * @param source ソース文字列\n");
        sb.append("     * @return パース＋変換された AST ノード\n");
        sb.append("     * @throws UnsupportedOperationException ")
          .append(parsersClass).append(" が未実装のため\n");
        sb.append("     */\n");
        sb.append("    public static ").append(rootClassName).append(" parse(String source) {\n");
        sb.append("        // TODO: ").append(parsersClass).append(" が生成されたら実装する\n");
        sb.append("        // StringSource stringSource = StringSource.createRootSource(source);\n");
        sb.append("        // try (ParseContext context = new ParseContext(stringSource)) {\n");
        sb.append("        //     Parser rootParser = ").append(parsersClass).append(".getRootParser();\n");
        sb.append("        //     Parsed parsed = rootParser.parse(context);\n");
        sb.append("        //     if (!parsed.isSucceeded()) {\n");
        sb.append("        //         throw new IllegalArgumentException(\"パース失敗: \" + source);\n");
        sb.append("        //     }\n");
        sb.append("        //     return to").append(rootRule.flatMap(r -> getMappingAnnotation(r))
            .map(MappingAnnotation::className).orElse("Root"))
          .append("(parsed.getRootToken());\n");
        sb.append("        // }\n");
        sb.append("        throw new UnsupportedOperationException(\"")
          .append(parsersClass).append(": Phase 4以降で実装\");\n");
        sb.append("    }\n\n");

        // @mapping ルールごとの toXxx メソッド
        sb.append("    // =========================================================================\n");
        sb.append("    // 変換メソッド\n");
        sb.append("    // =========================================================================\n\n");

        for (Map.Entry<String, RuleDecl> entry : mappingRules.entrySet()) {
            String className = entry.getKey();
            RuleDecl rule = entry.getValue();
            MappingAnnotation mapping = getMappingAnnotation(rule).get();
            String methodName = "to" + className;

            sb.append("    static ").append(astClass).append(".").append(className)
              .append(" ").append(methodName).append("(Token token) {\n");

            // 各パラメータの抽出ロジックのスケルトンを生成
            for (String param : mapping.paramNames()) {
                sb.append("        // TODO: extract ").append(param).append("\n");
            }

            // コンストラクタ呼び出し（全パラメータを null で仮置き）
            sb.append("        return new ").append(astClass).append(".").append(className).append("(\n");
            for (int i = 0; i < mapping.paramNames().size(); i++) {
                String suffix = i < mapping.paramNames().size() - 1 ? "," : "";
                sb.append("            null").append(suffix).append(" // ").append(mapping.paramNames().get(i)).append("\n");
            }
            sb.append("        );\n");
            sb.append("    }\n\n");
        }

        // ユーティリティメソッド
        sb.append("    // =========================================================================\n");
        sb.append("    // ユーティリティ\n");
        sb.append("    // =========================================================================\n\n");

        sb.append("    /**\n");
        sb.append("     * 指定パーサークラスの子孫 Token を深さ優先で探す。\n");
        sb.append("     */\n");
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

        sb.append("    /**\n");
        sb.append("     * シングルクォートを除いた文字列値を返す。\n");
        sb.append("     */\n");
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
