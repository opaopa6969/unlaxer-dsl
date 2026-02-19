package org.unlaxer.dsl;

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

/**
 * Orchestrates validation and generation steps for the CLI.
 */
final class CodegenRunner {

    private CodegenRunner() {}

    interface FileSystemPort {
        String readString(Path path) throws IOException;
        boolean exists(Path path);
        void createDirectories(Path path) throws IOException;
        void writeString(Path path, String content) throws IOException;
    }

    static int execute(
        CodegenCliParser.CliOptions config,
        PrintStream out,
        PrintStream err,
        Clock clock,
        String toolVersion
    ) throws IOException {
        return execute(config, out, err, clock, toolVersion, new JdkFileSystemPort());
    }

    static int execute(
        CodegenCliParser.CliOptions config,
        PrintStream out,
        PrintStream err,
        Clock clock,
        String toolVersion,
        FileSystemPort fs
    ) throws IOException {
        String source = fs.readString(Path.of(config.grammarFile()));
        UBNFFile file = UBNFMapper.parse(source);

        Map<String, CodeGenerator> generatorMap = generatorMap();

        List<ReportJsonWriter.ValidationIssueRow> validationRows = collectValidationRows(file);
        List<ReportJsonWriter.ValidationIssueRow> sortedRows = sortValidationRows(validationRows);

        boolean hasErrors = hasErrorRows(sortedRows);
        boolean hasWarnings = hasWarningRows(sortedRows);
        int warningsCount = countWarnings(sortedRows);

        String generatedAt = Instant.now(clock).toString();
        String warningJson = hasWarnings
            ? ReportJsonWriter.validationFailure(config.reportVersion(), toolVersion, generatedAt, sortedRows)
            : null;

        if (hasErrors || (config.strict() && hasWarnings)) {
            int exitCode = hasErrors ? CodegenMain.EXIT_VALIDATION_ERROR : CodegenMain.EXIT_STRICT_VALIDATION_ERROR;
            boolean emitJson = "json".equals(config.reportFormat()) || (config.warningsAsJson() && !hasErrors);
            if (emitJson) {
                String json = warningJson != null
                    ? warningJson
                    : ReportJsonWriter.validationFailure(
                        config.reportVersion(),
                        toolVersion,
                        generatedAt,
                        sortedRows
                    );
                validateJsonIfRequested(config, json, true);
                writeReportIfNeeded(config.reportFile(), json, fs);
                err.println(json);
                return exitCode;
            }

            String prefix = hasErrors
                ? "Grammar validation failed"
                : "Strict validation failed (warnings present)";
            List<String> validationMessages = sortedRows.stream()
                .map(row -> "grammar " + row.grammar() + ": " + toTextIssue(row))
                .toList();
            String text = prefix + ":\n - " + String.join("\n - ", validationMessages);
            writeReportIfNeeded(config.reportFile(), text, fs);
            err.println(text);
            return exitCode;
        }

        if (hasWarnings) {
            if (config.warningsAsJson()) {
                validateJsonIfRequested(config, warningJson, config.reportSchemaCheck());
                err.println(warningJson);
            } else {
                emitWarnings(err, sortedRows);
            }
        }

        if (config.validateOnly()) {
            if ("json".equals(config.reportFormat())) {
                String json = ReportJsonWriter.validationSuccess(
                    config.reportVersion(),
                    toolVersion,
                    generatedAt,
                    file.grammars().size(),
                    warningsCount
                );
                validateJsonIfRequested(config, json, true);
                out.println(json);
                writeReportIfNeeded(config.reportFile(), json, fs);
            } else {
                String text = "Validation succeeded for " + file.grammars().size() + " grammar(s).";
                out.println(text);
                writeReportIfNeeded(config.reportFile(), text, fs);
            }
            return CodegenMain.EXIT_OK;
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
                    return CodegenMain.EXIT_CLI_ERROR;
                }

                CodeGenerator.GeneratedSource src = gen.generate(grammar);
                Path pkgDir = outPath.resolve(src.packageName().replace('.', '/'));
                Path javaFile = pkgDir.resolve(src.className() + ".java");
                WriteAction action = writeGeneratedSource(config, fs, pkgDir, javaFile, src.source());
                if (action == WriteAction.CONFLICT) {
                    err.println("Refusing to overwrite existing file: " + javaFile);
                    err.println("Use --overwrite if-different or --overwrite always, or pass --dry-run.");
                    return CodegenMain.EXIT_GENERATION_ERROR;
                }
                if (action == WriteAction.SKIPPED) {
                    out.println("Skipped (unchanged): " + javaFile);
                    continue;
                }
                if (action == WriteAction.DRY_RUN) {
                    out.println("Dry-run: would generate " + javaFile);
                } else {
                    out.println("Generated: " + javaFile);
                }
                generatedFiles.add(javaFile.toString());
            }
        }

        if ("json".equals(config.reportFormat())) {
            String json = ReportJsonWriter.generationSuccess(
                config.reportVersion(),
                toolVersion,
                generatedAt,
                file.grammars().size(),
                generatedFiles,
                warningsCount
            );
            validateJsonIfRequested(config, json, true);
            out.println(json);
            writeReportIfNeeded(config.reportFile(), json, fs);
        } else if (config.reportFile() != null) {
            String text = "Generated files:\n" + generatedFiles.stream()
                .map(p -> " - " + p)
                .collect(Collectors.joining("\n"));
            writeReportIfNeeded(config.reportFile(), text, fs);
        }

        return CodegenMain.EXIT_OK;
    }

    static boolean hasErrorRows(List<ReportJsonWriter.ValidationIssueRow> rows) {
        return rows.stream().anyMatch(row -> !"WARNING".equals(row.severity()));
    }

    static boolean hasWarningRows(List<ReportJsonWriter.ValidationIssueRow> rows) {
        return rows.stream().anyMatch(row -> "WARNING".equals(row.severity()));
    }

    private static int countWarnings(List<ReportJsonWriter.ValidationIssueRow> rows) {
        int count = 0;
        for (ReportJsonWriter.ValidationIssueRow row : rows) {
            if ("WARNING".equals(row.severity())) {
                count++;
            }
        }
        return count;
    }

    private static Map<String, CodeGenerator> generatorMap() {
        Map<String, CodeGenerator> generatorMap = new LinkedHashMap<>();
        generatorMap.put("AST", new ASTGenerator());
        generatorMap.put("Parser", new ParserGenerator());
        generatorMap.put("Mapper", new MapperGenerator());
        generatorMap.put("Evaluator", new EvaluatorGenerator());
        generatorMap.put("LSP", new LSPGenerator());
        generatorMap.put("Launcher", new LSPLauncherGenerator());
        generatorMap.put("DAP", new DAPGenerator());
        generatorMap.put("DAPLauncher", new DAPLauncherGenerator());
        return generatorMap;
    }

    private static List<ReportJsonWriter.ValidationIssueRow> collectValidationRows(UBNFFile file) {
        List<ReportJsonWriter.ValidationIssueRow> validationRows = new ArrayList<>();
        for (GrammarDecl grammar : file.grammars()) {
            List<GrammarValidator.ValidationIssue> issues = GrammarValidator.validate(grammar);
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
        return validationRows;
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

    private static void emitWarnings(PrintStream err, List<ReportJsonWriter.ValidationIssueRow> rows) {
        List<ReportJsonWriter.ValidationIssueRow> warnings = rows.stream()
            .filter(row -> "WARNING".equals(row.severity()))
            .toList();
        if (warnings.isEmpty()) {
            return;
        }
        err.println("Validation warnings:");
        for (ReportJsonWriter.ValidationIssueRow row : warnings) {
            err.println(" - grammar " + row.grammar() + ": " + toTextIssue(row));
        }
    }

    private static void validateJsonIfRequested(
        CodegenCliParser.CliOptions config,
        String json,
        boolean shouldValidate
    ) {
        if (!shouldValidate || !config.reportSchemaCheck()) {
            return;
        }
        ReportJsonSchemaValidator.validate(config.reportVersion(), json);
    }

    private static void writeReportIfNeeded(String reportFile, String content, FileSystemPort fs) throws IOException {
        if (reportFile == null) {
            return;
        }
        Path reportPath = Path.of(reportFile);
        Path parent = reportPath.getParent();
        if (parent != null) {
            fs.createDirectories(parent);
        }
        fs.writeString(reportPath, content);
    }

    private static final class JdkFileSystemPort implements FileSystemPort {
        @Override
        public String readString(Path path) throws IOException {
            return Files.readString(path);
        }

        @Override
        public boolean exists(Path path) {
            return Files.exists(path);
        }

        @Override
        public void createDirectories(Path path) throws IOException {
            Files.createDirectories(path);
        }

        @Override
        public void writeString(Path path, String content) throws IOException {
            Files.writeString(path, content);
        }
    }

    private enum WriteAction {
        WRITTEN,
        SKIPPED,
        DRY_RUN,
        CONFLICT
    }

    private static WriteAction writeGeneratedSource(
        CodegenCliParser.CliOptions config,
        FileSystemPort fs,
        Path pkgDir,
        Path javaFile,
        String source
    ) throws IOException {
        if (config.dryRun()) {
            return WriteAction.DRY_RUN;
        }

        fs.createDirectories(pkgDir);

        boolean exists = fs.exists(javaFile);
        if (!exists) {
            fs.writeString(javaFile, source);
            return WriteAction.WRITTEN;
        }

        return switch (config.overwrite()) {
            case "always" -> {
                fs.writeString(javaFile, source);
                yield WriteAction.WRITTEN;
            }
            case "if-different" -> {
                String existing = fs.readString(javaFile);
                if (existing.equals(source)) {
                    yield WriteAction.SKIPPED;
                }
                fs.writeString(javaFile, source);
                yield WriteAction.WRITTEN;
            }
            case "never" -> WriteAction.CONFLICT;
            default -> throw new IllegalArgumentException("Unsupported overwrite policy: " + config.overwrite());
        };
    }
}
