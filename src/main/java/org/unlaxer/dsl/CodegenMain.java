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
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * CLI tool that reads UBNF grammars and generates Java sources.
 */
public class CodegenMain {
    static final int EXIT_OK = 0;
    static final int EXIT_CLI_ERROR = 2;
    static final int EXIT_VALIDATION_ERROR = 3;
    static final int EXIT_GENERATION_ERROR = 4;

    private static final String TOOL_VERSION = resolveToolVersion();

    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        if (exitCode != EXIT_OK) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        return runWithClock(args, out, err, Clock.systemUTC());
    }

    static int runWithClock(String[] args, PrintStream out, PrintStream err, Clock clock) {
        try {
            CodegenCliParser.CliOptions config = CodegenCliParser.parse(args);
            return execute(config, out, err, clock);
        } catch (CodegenCliParser.UsageException e) {
            if (e.getMessage() != null && !e.getMessage().isBlank()) {
                err.println(e.getMessage());
            }
            if (e.showUsage()) {
                printUsage(err);
            }
            return EXIT_CLI_ERROR;
        } catch (IOException e) {
            err.println("I/O error: " + e.getMessage());
            return EXIT_GENERATION_ERROR;
        } catch (RuntimeException e) {
            err.println("Generation failed: " + e.getMessage());
            return EXIT_GENERATION_ERROR;
        }
    }

    private static int execute(
        CodegenCliParser.CliOptions config,
        PrintStream out,
        PrintStream err,
        Clock clock
    ) throws IOException {
        String source = Files.readString(Path.of(config.grammarFile()));
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

        List<ReportJsonWriter.ValidationIssueRow> validationRows = new ArrayList<>();
        for (GrammarDecl grammar : file.grammars()) {
            List<GrammarValidator.ValidationIssue> issues = GrammarValidator.validate(grammar);
            if (!issues.isEmpty()) {
                for (GrammarValidator.ValidationIssue issue : issues) {
                    validationRows.add(new ReportJsonWriter.ValidationIssueRow(
                        grammar.name(),
                        issue.rule(),
                        issue.code(),
                        issue.severity(),
                        issue.category(),
                        issue.message(),
                        issue.hint()
                    ));
                }
            }
        }

        String generatedAt = Instant.now(clock).toString();

        if (!validationRows.isEmpty()) {
            List<ReportJsonWriter.ValidationIssueRow> sortedRows = sortValidationRows(validationRows);
            if ("json".equals(config.reportFormat())) {
                String json = ReportJsonWriter.validationFailure(
                    config.reportVersion(),
                    TOOL_VERSION,
                    generatedAt,
                    sortedRows
                );
                validateJsonIfRequested(config, json);
                writeReportIfNeeded(config.reportFile(), json);
                err.println(json);
                return EXIT_VALIDATION_ERROR;
            }

            List<String> validationErrors = sortedRows.stream()
                .map(row -> "grammar " + row.grammar() + ": " + toTextIssue(row))
                .toList();
            String text = "Grammar validation failed:\n - " + String.join("\n - ", validationErrors);
            writeReportIfNeeded(config.reportFile(), text);
            err.println(text);
            return EXIT_VALIDATION_ERROR;
        }

        if (config.validateOnly()) {
            if ("json".equals(config.reportFormat())) {
                String json = ReportJsonWriter.validationSuccess(
                    config.reportVersion(),
                    TOOL_VERSION,
                    generatedAt,
                    file.grammars().size()
                );
                validateJsonIfRequested(config, json);
                out.println(json);
                writeReportIfNeeded(config.reportFile(), json);
            } else {
                String text = "Validation succeeded for " + file.grammars().size() + " grammar(s).";
                out.println(text);
                writeReportIfNeeded(config.reportFile(), text);
            }
            return EXIT_OK;
        }

        Path outPath = Path.of(config.outputDir());
        List<String> generatedFiles = new ArrayList<>();

        for (GrammarDecl grammar : file.grammars()) {
            for (String name : config.generators()) {
                String key = name.trim();
                CodeGenerator gen = generatorMap.get(key);
                if (gen == null) {
                    err.println("Unknown generator: " + name);
                    err.println("Available: " + String.join(", ", generatorMap.keySet()));
                    return EXIT_CLI_ERROR;
                }

                CodeGenerator.GeneratedSource src = gen.generate(grammar);
                Path pkgDir = outPath.resolve(src.packageName().replace('.', '/'));
                Files.createDirectories(pkgDir);
                Path javaFile = pkgDir.resolve(src.className() + ".java");
                Files.writeString(javaFile, src.source());
                out.println("Generated: " + javaFile);
                generatedFiles.add(javaFile.toString());
            }
        }

        if ("json".equals(config.reportFormat())) {
            String json = ReportJsonWriter.generationSuccess(
                config.reportVersion(),
                TOOL_VERSION,
                generatedAt,
                file.grammars().size(),
                generatedFiles
            );
            validateJsonIfRequested(config, json);
            out.println(json);
            writeReportIfNeeded(config.reportFile(), json);
        } else if (config.reportFile() != null) {
            String text = "Generated files:\n" + generatedFiles.stream()
                .map(p -> " - " + p)
                .collect(Collectors.joining("\n"));
            writeReportIfNeeded(config.reportFile(), text);
        }

        return EXIT_OK;
    }

    private static void printUsage(PrintStream err) {
        err.println(
            "Usage: CodegenMain --grammar <file.ubnf> --output <dir>"
                + " [--generators AST,Parser,Mapper,Evaluator,LSP,Launcher,DAP,DAPLauncher]"
                + " [--validate-only]"
                + " [--report-format text|json]"
                + " [--report-file <path>]"
                + " [--report-version 1]"
                + " [--report-schema-check]"
        );
    }

    private static List<ReportJsonWriter.ValidationIssueRow> sortValidationRows(
        List<ReportJsonWriter.ValidationIssueRow> rows
    ) {
        return rows.stream()
            .sorted(
                Comparator.comparing(ReportJsonWriter.ValidationIssueRow::grammar)
                    .thenComparing(
                        ReportJsonWriter.ValidationIssueRow::rule,
                        Comparator.nullsFirst(String::compareTo)
                    )
                    .thenComparing(ReportJsonWriter.ValidationIssueRow::code)
                    .thenComparing(ReportJsonWriter.ValidationIssueRow::message)
            )
            .toList();
    }

    private static String toTextIssue(ReportJsonWriter.ValidationIssueRow row) {
        return row.message() + " [code: " + row.code() + "] [hint: " + row.hint() + "]";
    }

    private static String resolveToolVersion() {
        Package pkg = CodegenMain.class.getPackage();
        if (pkg == null) {
            return "dev";
        }
        String version = pkg.getImplementationVersion();
        if (version == null || version.isBlank()) {
            return "dev";
        }
        return version;
    }

    private static void validateJsonIfRequested(CodegenCliParser.CliOptions config, String json) {
        if (!config.reportSchemaCheck() || !"json".equals(config.reportFormat())) {
            return;
        }
        ReportJsonSchemaValidator.validate(config.reportVersion(), json);
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
