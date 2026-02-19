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
import java.util.Arrays;
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

    private static final int DEFAULT_REPORT_VERSION = 1;
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
            CliConfig config = parseArgs(args);
            return execute(config, out, err, clock);
        } catch (CliUsageException e) {
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

    private static CliConfig parseArgs(String[] args) throws CliUsageException {
        String grammarFile = null;
        String outputDir = null;
        List<String> generators = List.of("Parser", "LSP", "Launcher");
        boolean validateOnly = false;
        String reportFormat = "text";
        String reportFile = null;
        int reportVersion = DEFAULT_REPORT_VERSION;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--grammar" -> {
                    if (i + 1 >= args.length) {
                        throw new CliUsageException("Missing value for --grammar", true);
                    }
                    grammarFile = args[++i];
                }
                case "--output" -> {
                    if (i + 1 >= args.length) {
                        throw new CliUsageException("Missing value for --output", true);
                    }
                    outputDir = args[++i];
                }
                case "--generators" -> {
                    if (i + 1 >= args.length) {
                        throw new CliUsageException("Missing value for --generators", true);
                    }
                    generators = Arrays.asList(args[++i].split(","));
                }
                case "--validate-only" -> validateOnly = true;
                case "--report-format" -> {
                    if (i + 1 >= args.length) {
                        throw new CliUsageException("Missing value for --report-format", true);
                    }
                    reportFormat = args[++i].trim().toLowerCase();
                    if (!"text".equals(reportFormat) && !"json".equals(reportFormat)) {
                        throw new CliUsageException(
                            "Unsupported --report-format: " + reportFormat + "\nAllowed values: text, json",
                            false
                        );
                    }
                }
                case "--report-file" -> {
                    if (i + 1 >= args.length) {
                        throw new CliUsageException("Missing value for --report-file", true);
                    }
                    reportFile = args[++i];
                }
                case "--report-version" -> {
                    if (i + 1 >= args.length) {
                        throw new CliUsageException("Missing value for --report-version", true);
                    }
                    String raw = args[++i].trim();
                    try {
                        reportVersion = Integer.parseInt(raw);
                    } catch (NumberFormatException e) {
                        throw new CliUsageException(
                            "Unsupported --report-version: " + raw + "\nAllowed values: 1",
                            false
                        );
                    }
                    if (reportVersion != 1) {
                        throw new CliUsageException(
                            "Unsupported --report-version: " + reportVersion + "\nAllowed values: 1",
                            false
                        );
                    }
                }
                default -> throw new CliUsageException("Unknown argument: " + args[i], true);
            }
        }

        if (grammarFile == null || (!validateOnly && outputDir == null)) {
            throw new CliUsageException(null, true);
        }

        return new CliConfig(
            grammarFile,
            outputDir,
            generators,
            validateOnly,
            reportFormat,
            reportFile,
            reportVersion
        );
    }

    private static int execute(CliConfig config, PrintStream out, PrintStream err, Clock clock) throws IOException {
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

    private record CliConfig(
        String grammarFile,
        String outputDir,
        List<String> generators,
        boolean validateOnly,
        String reportFormat,
        String reportFile,
        int reportVersion
    ) {}

    private static final class CliUsageException extends Exception {
        private final boolean showUsage;

        private CliUsageException(String message, boolean showUsage) {
            super(message);
            this.showUsage = showUsage;
        }

        private boolean showUsage() {
            return showUsage;
        }
    }
}
