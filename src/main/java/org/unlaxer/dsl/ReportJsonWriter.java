package org.unlaxer.dsl;

import java.util.List;

/**
 * Version-dispatch facade for JSON report writers.
 */
final class ReportJsonWriter {

    private ReportJsonWriter() {}

    record ValidationIssueRow(
        String grammar,
        String rule,
        String code,
        String severity,
        String category,
        String message,
        String hint
    ) {}

    static String validationSuccess(
        int reportVersion,
        String toolVersion,
        String generatedAt,
        int grammarCount
    ) {
        return switch (reportVersion) {
            case 1 -> ReportJsonWriterV1.validationSuccess(toolVersion, generatedAt, grammarCount);
            default -> throw unsupportedVersion(reportVersion);
        };
    }

    static String validationFailure(
        int reportVersion,
        String toolVersion,
        String generatedAt,
        List<ValidationIssueRow> rows
    ) {
        return switch (reportVersion) {
            case 1 -> ReportJsonWriterV1.validationFailure(toolVersion, generatedAt, rows);
            default -> throw unsupportedVersion(reportVersion);
        };
    }

    static String generationSuccess(
        int reportVersion,
        String toolVersion,
        String generatedAt,
        int grammarCount,
        List<String> generatedFiles
    ) {
        return switch (reportVersion) {
            case 1 -> ReportJsonWriterV1.generationSuccess(toolVersion, generatedAt, grammarCount, generatedFiles);
            default -> throw unsupportedVersion(reportVersion);
        };
    }

    static String escapeJson(String s) {
        return s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private static IllegalArgumentException unsupportedVersion(int reportVersion) {
        return new IllegalArgumentException("Unsupported reportVersion: " + reportVersion);
    }
}
