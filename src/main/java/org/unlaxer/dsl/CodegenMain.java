package org.unlaxer.dsl;

import org.unlaxer.dsl.bootstrap.UBNFAST.GrammarDecl;
import org.unlaxer.dsl.bootstrap.UBNFAST.UBNFFile;
import org.unlaxer.dsl.bootstrap.UBNFMapper;
import org.unlaxer.dsl.codegen.ASTGenerator;
import org.unlaxer.dsl.codegen.CodeGenerator;
import org.unlaxer.dsl.codegen.DAPGenerator;
import org.unlaxer.dsl.codegen.DAPLauncherGenerator;
import org.unlaxer.dsl.codegen.EvaluatorGenerator;
import org.unlaxer.dsl.codegen.GrammarValidator;
import org.unlaxer.dsl.codegen.LSPGenerator;
import org.unlaxer.dsl.codegen.LSPLauncherGenerator;
import org.unlaxer.dsl.codegen.MapperGenerator;
import org.unlaxer.dsl.codegen.ParserGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
        boolean validateOnly = false;
        String reportFormat = "text";

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
                case "--validate-only" -> validateOnly = true;
                case "--report-format" -> {
                    if (i + 1 >= args.length) {
                        System.err.println("Missing value for --report-format");
                        printUsage();
                        System.exit(1);
                    }
                    reportFormat = args[++i].trim().toLowerCase();
                    if (!"text".equals(reportFormat) && !"json".equals(reportFormat)) {
                        System.err.println("Unsupported --report-format: " + reportFormat);
                        System.err.println("Allowed values: text, json");
                        System.exit(1);
                    }
                }
                default -> {
                    System.err.println("Unknown argument: " + args[i]);
                    printUsage();
                    System.exit(1);
                }
            }
        }

        if (grammarFile == null || (!validateOnly && outputDir == null)) {
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

        List<String> validationErrors = new ArrayList<>();
        List<ValidationRow> validationRows = new ArrayList<>();
        for (GrammarDecl grammar : file.grammars()) {
            List<GrammarValidator.ValidationIssue> issues = GrammarValidator.validate(grammar);
            if (!issues.isEmpty()) {
                for (GrammarValidator.ValidationIssue issue : issues) {
                    validationErrors.add("grammar " + grammar.name() + ": " + issue.format());
                    validationRows.add(new ValidationRow(
                        grammar.name(), issue.code(), issue.message(), issue.hint()
                    ));
                }
            }
        }
        if (!validationErrors.isEmpty()) {
            if ("json".equals(reportFormat)) {
                throw new IllegalArgumentException(toJsonReport(validationRows));
            }
            throw new IllegalArgumentException(
                "Grammar validation failed:\n - " + String.join("\n - ", validationErrors)
            );
        }
        if (validateOnly) {
            if ("json".equals(reportFormat)) {
                System.out.println("{\"ok\":true,\"grammarCount\":" + file.grammars().size() + ",\"issues\":[]}");
            } else {
                System.out.println("Validation succeeded for " + file.grammars().size() + " grammar(s).");
            }
            return;
        }
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
                + " [--validate-only]"
                + " [--report-format text|json]"
        );
    }

    private record ValidationRow(String grammar, String code, String message, String hint) {}

    private static String toJsonReport(List<ValidationRow> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"ok\":false,\"issueCount\":").append(rows.size()).append(",\"issues\":[");
        for (int i = 0; i < rows.size(); i++) {
            ValidationRow row = rows.get(i);
            if (i > 0) sb.append(",");
            sb.append("{")
                .append("\"grammar\":\"").append(escapeJson(row.grammar())).append("\",")
                .append("\"code\":\"").append(escapeJson(row.code())).append("\",")
                .append("\"message\":\"").append(escapeJson(row.message())).append("\",")
                .append("\"hint\":\"").append(escapeJson(row.hint())).append("\"")
                .append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        return s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}
