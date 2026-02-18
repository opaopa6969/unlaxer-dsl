package org.unlaxer.dsl.codegen;

import org.unlaxer.dsl.bootstrap.UBNFAST.AnnotatedElement;
import org.unlaxer.dsl.bootstrap.UBNFAST.AtomicElement;
import org.unlaxer.dsl.bootstrap.UBNFAST.BlockSettingValue;
import org.unlaxer.dsl.bootstrap.UBNFAST.ChoiceBody;
import org.unlaxer.dsl.bootstrap.UBNFAST.GrammarDecl;
import org.unlaxer.dsl.bootstrap.UBNFAST.GroupElement;
import org.unlaxer.dsl.bootstrap.UBNFAST.OptionalElement;
import org.unlaxer.dsl.bootstrap.UBNFAST.RepeatElement;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * GrammarDecl から XxxParsers.java を生成する。
 *
 * <p>各ルールに対応するパーサークラスと、スペースデリミタを自動挿入する
 * 基底チェーンクラスを生成する。</p>
 */
public class ParserGenerator implements CodeGenerator {

    // =========================================================================
    // 内部型
    // =========================================================================

    /** 生成コンテキスト。grammar 全体の情報とヘルパー状態を保持する。 */
    private static class GenContext {
        final GrammarDecl grammar;
        final String grammarName;
        final Map<String, String> tokenParserMap;  // token name -> parser class name
        final Set<String> ruleNames;
        final Map<String, List<String>> helpers = new LinkedHashMap<>(); // rule -> helper codes
        final Map<String, int[]> helperCounters = new LinkedHashMap<>(); // rule -> [repeat,opt,group]
        boolean needsCPPComment = false;
        final List<String> delimitorClasses = new ArrayList<>();

        GenContext(GrammarDecl grammar) {
            this.grammar = grammar;
            this.grammarName = grammar.name();
            this.tokenParserMap = new LinkedHashMap<>();
            for (TokenDecl token : grammar.tokens()) {
                tokenParserMap.put(token.name(), token.parserClass());
            }
            this.ruleNames = grammar.rules().stream()
                .map(RuleDecl::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        void resetCounters(String ruleName) {
            helperCounters.put(ruleName, new int[]{0, 0, 0});
        }

        int nextRepeat(String ruleName) {
            return helperCounters.computeIfAbsent(ruleName, k -> new int[]{0,0,0})[0]++;
        }

        int nextOpt(String ruleName) {
            return helperCounters.computeIfAbsent(ruleName, k -> new int[]{0,0,0})[1]++;
        }

        int nextGroup(String ruleName) {
            return helperCounters.computeIfAbsent(ruleName, k -> new int[]{0,0,0})[2]++;
        }

        void addHelper(String ruleName, String code) {
            helpers.computeIfAbsent(ruleName, k -> new ArrayList<>()).add(code);
        }
    }

    // =========================================================================
    // メイン生成
    // =========================================================================

    @Override
    public GeneratedSource generate(GrammarDecl grammar) {
        String packageName = getPackageName(grammar);
        String grammarName = grammar.name();
        String className = grammarName + "Parsers";

        GenContext ctx = createContext(grammar);

        // Phase 1: 全ルールのヘルパーを事前収集
        for (RuleDecl rule : grammar.rules()) {
            ctx.resetCounters(rule.name());
            collectHelpers(ctx, rule);
        }

        StringBuilder sb = new StringBuilder();

        // パッケージ宣言
        sb.append("package ").append(packageName).append(";\n\n");

        // インポート
        sb.append("import java.util.Optional;\n");
        sb.append("import java.util.function.Supplier;\n");
        sb.append("import org.unlaxer.RecursiveMode;\n");
        sb.append("import org.unlaxer.parser.Parser;\n");
        sb.append("import org.unlaxer.parser.Parsers;\n");
        sb.append("import org.unlaxer.parser.combinator.*;\n");
        sb.append("import org.unlaxer.parser.elementary.WordParser;\n");
        sb.append("import org.unlaxer.parser.posix.SpaceParser;\n");
        if (ctx.needsCPPComment) {
            sb.append("import org.unlaxer.parser.clang.CPPComment;\n");
        }
        sb.append("import org.unlaxer.reducer.TagBasedReducer.NodeKind;\n");
        sb.append("import org.unlaxer.util.cache.SupplierBoundCache;\n");
        sb.append("\n");

        // クラス宣言
        sb.append("public class ").append(className).append(" {\n\n");

        // スペースデリミタクラス
        sb.append(generateDelimitorClass(ctx));

        // 基底チェーンクラス（@whitespace 設定がある場合）
        if (!ctx.delimitorClasses.isEmpty()) {
            sb.append(generateBaseChainClass(ctx));
        }

        // Phase 2: 各ルールのヘルパー + ルールクラスを出力
        for (RuleDecl rule : grammar.rules()) {
            ctx.resetCounters(rule.name());
            List<String> ruleHelpers = ctx.helpers.getOrDefault(rule.name(), List.of());
            for (String helper : ruleHelpers) {
                sb.append(helper);
            }
            sb.append(generateRuleClass(ctx, rule));
        }

        // ファクトリメソッド
        String rootRuleName = findRootRuleName(grammar);
        sb.append("    public static Parser getRootParser() {\n");
        sb.append("        return Parser.get(").append(rootRuleName).append("Parser.class);\n");
        sb.append("    }\n");

        sb.append("}\n");

        return new GeneratedSource(packageName, className, sb.toString());
    }

    // =========================================================================
    // コンテキスト初期化
    // =========================================================================

    private GenContext createContext(GrammarDecl grammar) {
        GenContext ctx = new GenContext(grammar);

        // @whitespace 設定を解析
        boolean hasWhitespace = grammar.settings().stream()
            .anyMatch(s -> "whitespace".equals(s.key()));
        if (hasWhitespace) {
            ctx.delimitorClasses.add("SpaceParser.class");
        }

        // @comment 設定を解析（block setting with "line" key）
        boolean hasComment = grammar.settings().stream()
            .anyMatch(s -> "comment".equals(s.key()) && s.value() instanceof BlockSettingValue bv
                && bv.entries().stream().anyMatch(kv -> "line".equals(kv.key())));
        if (hasComment) {
            ctx.needsCPPComment = true;
            ctx.delimitorClasses.add("CPPComment.class");
        }

        return ctx;
    }

    // =========================================================================
    // デリミタ・基底チェーン生成
    // =========================================================================

    private String generateDelimitorClass(GenContext ctx) {
        String gn = ctx.grammarName;
        String delimitorName = gn + "SpaceDelimitor";
        StringBuilder sb = new StringBuilder();

        sb.append("    // --- Whitespace Delimitor ---\n");
        sb.append("    public static class ").append(delimitorName).append(" extends LazyZeroOrMore {\n");
        sb.append("        private static final long serialVersionUID = 1L;\n");
        sb.append("        @Override\n");
        sb.append("        public Supplier<Parser> getLazyParser() {\n");

        if (ctx.delimitorClasses.isEmpty()) {
            sb.append("            return new SupplierBoundCache<>(() -> Parser.get(SpaceParser.class));\n");
        } else if (ctx.delimitorClasses.size() == 1) {
            sb.append("            return new SupplierBoundCache<>(() -> Parser.get(")
              .append(ctx.delimitorClasses.get(0)).append("));\n");
        } else {
            String args = String.join(", ", ctx.delimitorClasses);
            sb.append("            return new SupplierBoundCache<>(() -> new Choice(").append(args).append("));\n");
        }

        sb.append("        }\n");
        sb.append("        @Override\n");
        sb.append("        public java.util.Optional<Parser> getLazyTerminatorParser() { return java.util.Optional.empty(); }\n");
        sb.append("    }\n\n");

        return sb.toString();
    }

    private String generateBaseChainClass(GenContext ctx) {
        String gn = ctx.grammarName;
        String delimitorName = gn + "SpaceDelimitor";
        String chainName = gn + "LazyChain";
        StringBuilder sb = new StringBuilder();

        sb.append("    // --- Base Chain ---\n");
        sb.append("    public static abstract class ").append(chainName).append(" extends LazyChain {\n");
        sb.append("        private static final long serialVersionUID = 1L;\n");
        sb.append("        private static final ").append(delimitorName).append(" SPACE = createSpace();\n");
        sb.append("        private static ").append(delimitorName).append(" createSpace() {\n");
        sb.append("            ").append(delimitorName).append(" s = new ").append(delimitorName).append("();\n");
        sb.append("            s.addTag(NodeKind.notNode.getTag());\n");
        sb.append("            return s;\n");
        sb.append("        }\n");
        sb.append("        @Override\n");
        sb.append("        public void prepareChildren(Parsers c) {\n");
        sb.append("            if (!c.isEmpty()) return;\n");
        sb.append("            c.add(SPACE);\n");
        sb.append("            for (Parser p : getLazyParsers()) { c.add(p); c.add(SPACE); }\n");
        sb.append("        }\n");
        sb.append("        public abstract Parsers getLazyParsers();\n");
        sb.append("        @Override\n");
        sb.append("        public java.util.Optional<RecursiveMode> getNotAstNodeSpecifier() { return java.util.Optional.empty(); }\n");
        sb.append("    }\n\n");

        return sb.toString();
    }

    // =========================================================================
    // ヘルパー収集
    // =========================================================================

    private void collectHelpers(GenContext ctx, RuleDecl rule) {
        collectHelpersInBody(ctx, rule.name(), rule.body());
    }

    private void collectHelpersInBody(GenContext ctx, String ruleName, RuleBody body) {
        switch (body) {
            case ChoiceBody choice -> {
                for (SequenceBody alt : choice.alternatives()) {
                    collectHelpersInSequence(ctx, ruleName, alt);
                }
            }
            case SequenceBody seq -> collectHelpersInSequence(ctx, ruleName, seq);
        }
    }

    private void collectHelpersInSequence(GenContext ctx, String ruleName, SequenceBody seq) {
        for (AnnotatedElement ae : seq.elements()) {
            collectHelpersInElement(ctx, ruleName, ae.element());
        }
    }

    private void collectHelpersInElement(GenContext ctx, String ruleName, AtomicElement element) {
        switch (element) {
            case RepeatElement rep -> {
                if (!isSingleRuleRef(rep.body())) {
                    int n = ctx.nextRepeat(ruleName);
                    String helperName = ruleName + "Repeat" + n + "Parser";
                    // サブヘルパーを先に収集
                    collectHelpersInBody(ctx, ruleName, rep.body());
                    String helperCode = generateHelperCode(ctx, ruleName, helperName, rep.body());
                    ctx.addHelper(ruleName, helperCode);
                }
            }
            case OptionalElement opt -> {
                if (!isSingleAtomicElement(opt.body())) {
                    int n = ctx.nextOpt(ruleName);
                    String helperName = ruleName + "Opt" + n + "Parser";
                    collectHelpersInBody(ctx, ruleName, opt.body());
                    String helperCode = generateHelperCode(ctx, ruleName, helperName, opt.body());
                    ctx.addHelper(ruleName, helperCode);
                }
            }
            case GroupElement g -> {
                int n = ctx.nextGroup(ruleName);
                String helperName = ruleName + "Group" + n + "Parser";
                collectHelpersInBody(ctx, ruleName, g.body());
                String helperCode = generateHelperCode(ctx, ruleName, helperName, g.body());
                ctx.addHelper(ruleName, helperCode);
            }
            default -> {} // TerminalElement, RuleRefElement
        }
    }

    /**
     * ヘルパークラスのコードを生成する。
     * body が複数代替 ChoiceBody なら LazyChoice、
     * それ以外なら {GrammarName}LazyChain を継承する。
     */
    private String generateHelperCode(GenContext ctx, String ruleName, String helperName, RuleBody body) {
        boolean isChoice = isMultiChoice(body);
        String gn = ctx.grammarName;
        StringBuilder sb = new StringBuilder();
        String indent = "    ";

        sb.append(indent).append("public static class ").append(helperName);
        if (isChoice) {
            sb.append(" extends LazyChoice {\n");
        } else {
            sb.append(" extends ").append(gn).append("LazyChain {\n");
        }
        sb.append(indent).append("    private static final long serialVersionUID = 1L;\n");
        sb.append(indent).append("    @Override\n");
        sb.append(indent).append("    public Parsers getLazyParsers() {\n");
        sb.append(indent).append("        return new Parsers(\n");
        sb.append(generateBodyElements(ctx, ruleName, body, indent + "            "));
        sb.append(indent).append("        );\n");
        sb.append(indent).append("    }\n");
        if (isChoice) {
            sb.append(indent).append("    @Override\n");
            sb.append(indent).append("    public java.util.Optional<RecursiveMode> getNotAstNodeSpecifier() { return java.util.Optional.empty(); }\n");
        }
        sb.append(indent).append("}\n\n");

        return sb.toString();
    }

    // =========================================================================
    // ルールクラス生成
    // =========================================================================

    private String generateRuleClass(GenContext ctx, RuleDecl rule) {
        String ruleName = rule.name();
        String className = ruleName + "Parser";
        String gn = ctx.grammarName;
        boolean isChoice = isMultiChoice(rule.body());

        StringBuilder sb = new StringBuilder();
        String indent = "    ";

        sb.append(indent).append("public static class ").append(className);
        if (isChoice) {
            sb.append(" extends LazyChoice {\n");
        } else {
            sb.append(" extends ").append(gn).append("LazyChain {\n");
        }
        sb.append(indent).append("    private static final long serialVersionUID = 1L;\n");
        sb.append(indent).append("    @Override\n");
        sb.append(indent).append("    public Parsers getLazyParsers() {\n");
        sb.append(indent).append("        return new Parsers(\n");
        sb.append(generateBodyElements(ctx, ruleName, rule.body(), indent + "            "));
        sb.append(indent).append("        );\n");
        sb.append(indent).append("    }\n");
        if (isChoice) {
            sb.append(indent).append("    @Override\n");
            sb.append(indent).append("    public java.util.Optional<RecursiveMode> getNotAstNodeSpecifier() { return java.util.Optional.empty(); }\n");
        }
        sb.append(indent).append("}\n\n");

        return sb.toString();
    }

    // =========================================================================
    // ボディ要素コード生成
    // =========================================================================

    /**
     * RuleBody から getLazyParsers() の中身（カンマ区切り要素リスト）を生成する。
     */
    private String generateBodyElements(GenContext ctx, String ruleName, RuleBody body, String indent) {
        List<String> elementCodes = new ArrayList<>();

        switch (body) {
            case ChoiceBody choice -> {
                if (choice.alternatives().size() == 1) {
                    // 単一代替 → SequenceBody として扱う
                    for (AnnotatedElement ae : choice.alternatives().get(0).elements()) {
                        elementCodes.add(generateElementCode(ctx, ruleName, ae.element()));
                    }
                } else {
                    // 複数代替 → 各代替を1エントリに
                    for (SequenceBody alt : choice.alternatives()) {
                        elementCodes.add(generateAlternativeCode(ctx, ruleName, alt, indent));
                    }
                }
            }
            case SequenceBody seq -> {
                for (AnnotatedElement ae : seq.elements()) {
                    elementCodes.add(generateElementCode(ctx, ruleName, ae.element()));
                }
            }
        }

        return elementCodes.stream()
            .map(c -> indent + c)
            .collect(Collectors.joining(",\n")) + "\n";
    }

    /**
     * ChoiceBody の1つの代替（SequenceBody）をコードに変換する。
     * - 単一要素: その要素コードをそのまま返す
     * - 複数要素: 匿名 {GrammarName}LazyChain サブクラスを生成
     */
    private String generateAlternativeCode(GenContext ctx, String ruleName, SequenceBody alt, String baseIndent) {
        List<AnnotatedElement> elements = alt.elements();

        if (elements.size() == 1) {
            return generateElementCode(ctx, ruleName, elements.get(0).element());
        }

        // 複数要素 → 匿名 TinyCalcLazyChain
        String gn = ctx.grammarName;
        String innerIndent = baseIndent + "    ";
        StringBuilder sb = new StringBuilder();
        sb.append("new ").append(gn).append("LazyChain() {\n");
        sb.append(innerIndent).append("private static final long serialVersionUID = 1L;\n");
        sb.append(innerIndent).append("@Override\n");
        sb.append(innerIndent).append("public Parsers getLazyParsers() {\n");
        sb.append(innerIndent).append("    return new Parsers(\n");

        List<String> elemCodes = new ArrayList<>();
        for (AnnotatedElement ae : elements) {
            elemCodes.add(generateElementCode(ctx, ruleName, ae.element()));
        }
        String elemsJoined = elemCodes.stream()
            .map(c -> innerIndent + "        " + c)
            .collect(Collectors.joining(",\n"));
        sb.append(elemsJoined).append("\n");

        sb.append(innerIndent).append("    );\n");
        sb.append(innerIndent).append("}\n");
        sb.append(baseIndent).append("}");

        return sb.toString();
    }

    /**
     * 単一の AtomicElement をコードに変換する。
     */
    private String generateElementCode(GenContext ctx, String ruleName, AtomicElement element) {
        return switch (element) {
            case TerminalElement t -> "new WordParser(\"" + escapeString(t.value()) + "\")";

            case RuleRefElement r -> "Parser.get(" + resolveParserClass(ctx, r.name()) + ")";

            case RepeatElement rep -> {
                if (isSingleRuleRef(rep.body())) {
                    String parserClass = getSingleRuleRefClass(ctx, rep.body());
                    yield "new ZeroOrMore(" + parserClass + ")";
                } else {
                    int n = ctx.nextRepeat(ruleName);
                    String helperName = ruleName + "Repeat" + n + "Parser";
                    yield "new ZeroOrMore(" + helperName + ".class)";
                }
            }

            case OptionalElement opt -> {
                if (isSingleAtomicElement(opt.body())) {
                    AtomicElement inner = getSingleAtomicElementFrom(opt.body());
                    if (inner instanceof RuleRefElement ref) {
                        yield "new Optional(" + resolveParserClass(ctx, ref.name()) + ")";
                    } else if (inner instanceof TerminalElement t) {
                        yield "new Optional(new WordParser(\"" + escapeString(t.value()) + "\"))";
                    } else {
                        int n = ctx.nextOpt(ruleName);
                        String helperName = ruleName + "Opt" + n + "Parser";
                        yield "new Optional(" + helperName + ".class)";
                    }
                } else {
                    int n = ctx.nextOpt(ruleName);
                    String helperName = ruleName + "Opt" + n + "Parser";
                    yield "new Optional(" + helperName + ".class)";
                }
            }

            case GroupElement g -> {
                int n = ctx.nextGroup(ruleName);
                String helperName = ruleName + "Group" + n + "Parser";
                yield "Parser.get(" + helperName + ".class)";
            }
        };
    }

    // =========================================================================
    // ユーティリティ
    // =========================================================================

    /** body が複数代替の ChoiceBody かどうか */
    private boolean isMultiChoice(RuleBody body) {
        return body instanceof ChoiceBody choice && choice.alternatives().size() > 1;
    }

    /** body が単一の RuleRefElement だけを含むか */
    private boolean isSingleRuleRef(RuleBody body) {
        AtomicElement single = getSingleAtomicElementFrom(body);
        return single instanceof RuleRefElement;
    }

    /** body が単一の AtomicElement だけを含むか */
    private boolean isSingleAtomicElement(RuleBody body) {
        return getSingleAtomicElementFrom(body) != null;
    }

    /** body から単一の AtomicElement を取り出す（なければ null） */
    private AtomicElement getSingleAtomicElementFrom(RuleBody body) {
        return switch (body) {
            case SequenceBody seq when seq.elements().size() == 1 ->
                seq.elements().get(0).element();
            case ChoiceBody choice when choice.alternatives().size() == 1 -> {
                SequenceBody seq = choice.alternatives().get(0);
                yield seq.elements().size() == 1 ? seq.elements().get(0).element() : null;
            }
            default -> null;
        };
    }

    /** 単一 RuleRef body からパーサークラス参照を取り出す */
    private String getSingleRuleRefClass(GenContext ctx, RuleBody body) {
        AtomicElement single = getSingleAtomicElementFrom(body);
        if (single instanceof RuleRefElement ref) {
            return resolveParserClass(ctx, ref.name());
        }
        throw new IllegalStateException("Expected single RuleRef body");
    }

    /**
     * ルール参照名をパーサークラス参照文字列に変換する。
     * - トークン宣言に存在する → token.parserClass() + ".class"
     * - それ以外 → {Name}Parser.class
     */
    private String resolveParserClass(GenContext ctx, String name) {
        String tokenClass = ctx.tokenParserMap.get(name);
        if (tokenClass != null) {
            return tokenClass + ".class";
        }
        return name + "Parser.class";
    }

    /** ルートルール名を返す（@root アノテーション付き） */
    private String findRootRuleName(GrammarDecl grammar) {
        return grammar.rules().stream()
            .filter(r -> r.annotations().stream().anyMatch(a -> a instanceof RootAnnotation))
            .map(RuleDecl::name)
            .findFirst()
            .orElse(grammar.rules().isEmpty() ? "Root" : grammar.rules().get(0).name());
    }

    /** 文字列内の特殊文字をエスケープする */
    private String escapeString(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** @package 設定からパッケージ名を取得する */
    private String getPackageName(GrammarDecl grammar) {
        return grammar.settings().stream()
            .filter(s -> "package".equals(s.key()))
            .map(s -> s.value() instanceof StringSettingValue sv ? sv.value() : "")
            .findFirst()
            .orElse("generated");
    }
}
