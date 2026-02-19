package org.unlaxer.dsl;

import java.io.IOException;
import java.io.PrintStream;
import java.time.Clock;

/**
 * CLI tool entry point for UBNF validation and source generation.
 */
public class CodegenMain {
    static final int EXIT_OK = 0;
    static final int EXIT_CLI_ERROR = 2;
    static final int EXIT_VALIDATION_ERROR = 3;
    static final int EXIT_GENERATION_ERROR = 4;
    static final int EXIT_STRICT_VALIDATION_ERROR = 5;

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
            if (config.help()) {
                printUsage(out);
                return EXIT_OK;
            }
            if (config.version()) {
                out.println(TOOL_VERSION);
                return EXIT_OK;
            }
            return CodegenRunner.execute(config, out, err, clock, TOOL_VERSION);
        } catch (CodegenCliParser.UsageException e) {
            if (e.getMessage() != null && !e.getMessage().isBlank()) {
                err.println(e.getMessage());
            }
            if (e.showUsage()) {
                printUsage(err);
            }
            return EXIT_CLI_ERROR;
        } catch (ReportSchemaValidationException e) {
            err.println(e.code() + ": " + e.getMessage());
            return EXIT_GENERATION_ERROR;
        } catch (IOException e) {
            err.println("I/O error: " + e.getMessage());
            return EXIT_GENERATION_ERROR;
        } catch (RuntimeException e) {
            err.println("Generation failed: " + e.getMessage());
            return EXIT_GENERATION_ERROR;
        }
    }

    private static void printUsage(PrintStream err) {
        err.println(
            "Usage: CodegenMain [--help] [--version] --grammar <file.ubnf> --output <dir>"
                + " [--generators AST,Parser,Mapper,Evaluator,LSP,Launcher,DAP,DAPLauncher]"
                + " [--validate-only]"
                + " [--dry-run]"
                + " [--clean-output]"
                + " [--overwrite never|if-different|always]"
                + " [--strict]"
                + " [--report-format text|json|ndjson]"
                + " [--report-file <path>]"
                + " [--report-version 1]"
                + " [--report-schema-check]"
                + " [--warnings-as-json]"
        );
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
}
