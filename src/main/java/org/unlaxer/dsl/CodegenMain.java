package org.unlaxer.dsl;

import org.unlaxer.dsl.bootstrap.UBNFAST.GrammarDecl;
import org.unlaxer.dsl.bootstrap.UBNFMapper;
import org.unlaxer.dsl.codegen.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * コマンドラインから UBNF grammar を読み込み、指定したジェネレーターで
 * Java ソースを生成してファイルに書き出す CLI ツール。
 *
 * <pre>
 * Usage: CodegenMain --grammar &lt;file&gt; --output &lt;dir&gt; [--generators &lt;list&gt;]
 *
 *   --grammar    .ubnf ファイルのパス
 *   --output     生成先ディレクトリ（package 構造で書き出す）
 *   --generators カンマ区切りのジェネレーター名（省略時: Parser,LSP,Launcher）
 *                使用可能: AST, Parser, Mapper, Evaluator, LSP, Launcher, DAP, DAPLauncher
 * </pre>
 */
public class CodegenMain {

    public static void main(String[] args) throws IOException {
        String grammarFile = null;
        String outputDir = null;
        List<String> generators = List.of("Parser", "LSP", "Launcher");

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--grammar"    -> grammarFile = args[++i];
                case "--output"     -> outputDir   = args[++i];
                case "--generators" -> generators  = Arrays.asList(args[++i].split(","));
                default -> {
                    System.err.println("Unknown argument: " + args[i]);
                    printUsage();
                    System.exit(1);
                }
            }
        }

        if (grammarFile == null || outputDir == null) {
            printUsage();
            System.exit(1);
        }

        String source = Files.readString(Path.of(grammarFile));
        GrammarDecl grammar = UBNFMapper.parse(source).grammars().get(0);

        Map<String, CodeGenerator> generatorMap = new LinkedHashMap<>();
        generatorMap.put("AST",          new ASTGenerator());
        generatorMap.put("Parser",       new ParserGenerator());
        generatorMap.put("Mapper",       new MapperGenerator());
        generatorMap.put("Evaluator",    new EvaluatorGenerator());
        generatorMap.put("LSP",          new LSPGenerator());
        generatorMap.put("Launcher",     new LSPLauncherGenerator());
        generatorMap.put("DAP",          new DAPGenerator());
        generatorMap.put("DAPLauncher",  new DAPLauncherGenerator());

        Path outPath = Path.of(outputDir);

        for (String name : generators) {
            CodeGenerator gen = generatorMap.get(name.trim());
            if (gen == null) {
                System.err.println("Unknown generator: " + name);
                System.err.println("Available: " + String.join(", ", generatorMap.keySet()));
                System.exit(1);
            }

            CodeGenerator.GeneratedSource src = gen.generate(grammar);

            Path pkgDir  = outPath.resolve(src.packageName().replace('.', '/'));
            Files.createDirectories(pkgDir);
            Path javaFile = pkgDir.resolve(src.className() + ".java");
            Files.writeString(javaFile, src.source());
            System.out.println("Generated: " + javaFile);
        }
    }

    private static void printUsage() {
        System.err.println(
            "Usage: CodegenMain --grammar <file.ubnf> --output <dir>" +
            " [--generators AST,Parser,Mapper,Evaluator,LSP,Launcher,DAP,DAPLauncher]"
        );
    }
}
