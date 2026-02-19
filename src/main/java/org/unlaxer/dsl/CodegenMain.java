package org.unlaxer.dsl;

import org.unlaxer.dsl.bootstrap.UBNFAST.GrammarDecl;
import org.unlaxer.dsl.bootstrap.UBNFAST.UBNFFile;
import org.unlaxer.dsl.bootstrap.UBNFMapper;
import org.unlaxer.dsl.codegen.ASTGenerator;
import org.unlaxer.dsl.codegen.CodeGenerator;
import org.unlaxer.dsl.codegen.DAPGenerator;
import org.unlaxer.dsl.codegen.DAPLauncherGenerator;
import org.unlaxer.dsl.codegen.EvaluatorGenerator;
import org.unlaxer.dsl.codegen.LSPGenerator;
import org.unlaxer.dsl.codegen.LSPLauncherGenerator;
import org.unlaxer.dsl.codegen.MapperGenerator;
import org.unlaxer.dsl.codegen.ParserGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CLI tool that reads UBNF grammars and generates Java sources.
 */
public class CodegenMain {

    public static void main(String[] args) throws IOException {
        String grammarFile = null;
        String outputDir = null;
        List<String> generators = List.of("Parser", "LSP", "Launcher");

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--grammar" -> {
                    if (i + 1 >= args.length) {
                        System.err.println("Missing value for --grammar");
                        printUsage();
                        System.exit(1);
                    }
                    grammarFile = args[++i];
                }
                case "--output" -> {
                    if (i + 1 >= args.length) {
                        System.err.println("Missing value for --output");
                        printUsage();
                        System.exit(1);
                    }
                    outputDir = args[++i];
                }
                case "--generators" -> {
                    if (i + 1 >= args.length) {
                        System.err.println("Missing value for --generators");
                        printUsage();
                        System.exit(1);
                    }
                    generators = Arrays.asList(args[++i].split(","));
                }
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
        UBNFFile file = UBNFMapper.parse(source);

        Map<String, CodeGenerator> generatorMap = new LinkedHashMap<>();
        generatorMap.put("AST", new ASTGenerator());
        generatorMap.put("Parser", new ParserGenerator());
        generatorMap.put("Mapper", new MapperGenerator());
        generatorMap.put("Evaluator", new EvaluatorGenerator());
        generatorMap.put("LSP", new LSPGenerator());
        generatorMap.put("Launcher", new LSPLauncherGenerator());
        generatorMap.put("DAP", new DAPGenerator());
        generatorMap.put("DAPLauncher", new DAPLauncherGenerator());

        Path outPath = Path.of(outputDir);

        for (GrammarDecl grammar : file.grammars()) {
            for (String name : generators) {
                String key = name.trim();
                CodeGenerator gen = generatorMap.get(key);
                if (gen == null) {
                    System.err.println("Unknown generator: " + name);
                    System.err.println("Available: " + String.join(", ", generatorMap.keySet()));
                    System.exit(1);
                }

                CodeGenerator.GeneratedSource src = gen.generate(grammar);
                Path pkgDir = outPath.resolve(src.packageName().replace('.', '/'));
                Files.createDirectories(pkgDir);
                Path javaFile = pkgDir.resolve(src.className() + ".java");
                Files.writeString(javaFile, src.source());
                System.out.println("Generated: " + javaFile);
            }
        }
    }

    private static void printUsage() {
        System.err.println(
            "Usage: CodegenMain --grammar <file.ubnf> --output <dir>"
                + " [--generators AST,Parser,Mapper,Evaluator,LSP,Launcher,DAP,DAPLauncher]"
        );
    }
}
