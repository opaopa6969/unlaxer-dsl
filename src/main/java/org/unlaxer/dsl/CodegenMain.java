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
import java.util.stream.Collectors;

/**
 * CLI tool that reads UBNF grammars and generates Java sources.
 */
public class CodegenMain {
    private static final int REPORT_VERSION = 1;

    public static void main(String[] args) throws IOException {
        String grammarFile = null;
        String outputDir = null;
        List<String> generators = List.of("Parser", "LSP", "Launcher");
        boolean validateOnly = false;
        String reportFormat = "text";
        String reportFile = null;

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
                case "--report-file" -> {
                    if (i + 1 >= args.length) {
                        System.err.println("Missing value for --report-file");
                        printUsage();
                        System.exit(1);
                    }
                    reportFile = args[++i];
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
                        grammar.name(),
                        issue.code(),
                        issue.severity(),
                        issue.category(),
                        issue.message(),
                        issue.hint()
                    ));
                }
            }
        }
        if (!validationErrors.isEmpty()) {
            if ("json".equals(reportFormat)) {
                String json = toValidationJsonReport(validationRows);
                writeReportIfNeeded(reportFile, json);
                throw new IllegalArgumentException(json);
            }
            String text =
                "Grammar validation failed:\n - " + String.join("\n - ", validationErrors)
            ;
            writeReportIfNeeded(reportFile, text);
            throw new IllegalArgumentException(text);
        }
        if (validateOnly) {
            if ("json".equals(reportFormat)) {
                String json = "{\"reportVersion\":" + REPORT_VERSION + ",\"mode\":\"validate\","
                    + "\"ok\":true,\"grammarCount\":" + file.grammars().size() + ",\"issues\":[]}";
                System.out.println(json);
                writeReportIfNeeded(reportFile, json);
            } else {
                String text = "Validation succeeded for " + file.grammars().size() + " grammar(s).";
                System.out.println(text);
                writeReportIfNeeded(reportFile, text);
            }
            return;
        }
        Path outPath = Path.of(outputDir);
        List<String> generatedFiles = new ArrayList<>();

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
                generatedFiles.add(javaFile.toString());
            }
        }

        if ("json".equals(reportFormat)) {
            String json = toGenerationJsonReport(file.grammars().size(), generatedFiles);
            System.out.println(json);
            writeReportIfNeeded(reportFile, json);
        } else if (reportFile != null) {
            String text = "Generated files:\n" + generatedFiles.stream()
                .map(p -> " - " + p)
                .collect(Collectors.joining("\n"));
            writeReportIfNeeded(reportFile, text);
        }
    }

    private static void printUsage() {
        System.err.println(
            "Usage: CodegenMain --grammar <file.ubnf> --output <dir>"
                + " [--generators AST,Parser,Mapper,Evaluator,LSP,Launcher,DAP,DAPLauncher]"
                + " [--validate-only]"
                + " [--report-format text|json]"
                + " [--report-file <path>]"
        );
    }

    private record ValidationRow(
        String grammar,
        String code,
        String severity,
        String category,
        String message,
        String hint
    ) {}

    private static String toValidationJsonReport(List<ValidationRow> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"reportVersion\":").append(REPORT_VERSION)
            .append(",\"mode\":\"validate\",\"ok\":false,\"issueCount\":")
            .append(rows.size()).append(",\"issues\":[");
        for (int i = 0; i < rows.size(); i++) {
            ValidationRow row = rows.get(i);
            if (i > 0) sb.append(",");
            sb.append("{")
                .append("\"grammar\":\"").append(escapeJson(row.grammar())).append("\",")
                .append("\"code\":\"").append(escapeJson(row.code())).append("\",")
                .append("\"severity\":\"").append(escapeJson(row.severity())).append("\",")
                .append("\"category\":\"").append(escapeJson(row.category())).append("\",")
                .append("\"message\":\"").append(escapeJson(row.message())).append("\",")
                .append("\"hint\":\"").append(escapeJson(row.hint())).append("\"")
                .append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String toGenerationJsonReport(int grammarCount, List<String> generatedFiles) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"reportVersion\":").append(REPORT_VERSION)
            .append(",\"mode\":\"generate\",\"ok\":true,\"grammarCount\":").append(grammarCount)
            .append(",\"generatedCount\":").append(generatedFiles.size())
            .append(",\"generatedFiles\":[");
        for (int i = 0; i < generatedFiles.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escapeJson(generatedFiles.get(i))).append("\"");
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

    private static void writeReportIfNeeded(String reportFile, String content) throws IOException {
        if (reportFile == null) {
            return;
        }
        Path reportPath = Path.of(reportFile);
        Path parent = reportPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(reportPath, content);
    }
}
