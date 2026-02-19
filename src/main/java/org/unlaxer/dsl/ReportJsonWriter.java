package org.unlaxer.dsl;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Writes stable JSON reports for CodegenMain without external JSON dependencies.
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
        return "{\"reportVersion\":" + reportVersion
            + ",\"toolVersion\":\"" + escapeJson(toolVersion) + "\""
            + ",\"generatedAt\":\"" + escapeJson(generatedAt) + "\""
            + ",\"mode\":\"validate\""
            + ",\"ok\":true,\"grammarCount\":" + grammarCount + ",\"issues\":[]}";
    }

    static String validationFailure(
        int reportVersion,
        String toolVersion,
        String generatedAt,
        List<ValidationIssueRow> rows
    ) {
        Map<String, Integer> severityCounts = new TreeMap<>();
        Map<String, Integer> categoryCounts = new TreeMap<>();
        for (ValidationIssueRow row : rows) {
            severityCounts.merge(row.severity(), 1, Integer::sum);
            categoryCounts.merge(row.category(), 1, Integer::sum);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\"reportVersion\":").append(reportVersion)
            .append(",\"toolVersion\":\"").append(escapeJson(toolVersion)).append("\"")
            .append(",\"generatedAt\":\"").append(escapeJson(generatedAt)).append("\"")
            .append(",\"mode\":\"validate\",\"ok\":false,\"issueCount\":")
            .append(rows.size())
            .append(",\"severityCounts\":").append(toCountsJson(severityCounts))
            .append(",\"categoryCounts\":").append(toCountsJson(categoryCounts))
            .append(",\"issues\":[");

        for (int i = 0; i < rows.size(); i++) {
            ValidationIssueRow row = rows.get(i);
            if (i > 0) {
                sb.append(",");
            }
            sb.append("{")
                .append("\"grammar\":\"").append(escapeJson(row.grammar())).append("\",")
                .append("\"rule\":");
            if (row.rule() == null) {
                sb.append("null,");
            } else {
                sb.append("\"").append(escapeJson(row.rule())).append("\",");
            }
            sb.append("\"code\":\"").append(escapeJson(row.code())).append("\",")
                .append("\"severity\":\"").append(escapeJson(row.severity())).append("\",")
                .append("\"category\":\"").append(escapeJson(row.category())).append("\",")
                .append("\"message\":\"").append(escapeJson(row.message())).append("\",")
                .append("\"hint\":\"").append(escapeJson(row.hint())).append("\"")
                .append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    static String generationSuccess(
        int reportVersion,
        String toolVersion,
        String generatedAt,
        int grammarCount,
        List<String> generatedFiles
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"reportVersion\":").append(reportVersion)
            .append(",\"toolVersion\":\"").append(escapeJson(toolVersion)).append("\"")
            .append(",\"generatedAt\":\"").append(escapeJson(generatedAt)).append("\"")
            .append(",\"mode\":\"generate\",\"ok\":true,\"grammarCount\":")
            .append(grammarCount)
            .append(",\"generatedCount\":").append(generatedFiles.size())
            .append(",\"generatedFiles\":[");

        for (int i = 0; i < generatedFiles.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("\"").append(escapeJson(generatedFiles.get(i))).append("\"");
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String toCountsJson(Map<String, Integer> counts) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":").append(entry.getValue());
        }
        sb.append("}");
        return sb.toString();
    }

    static String escapeJson(String s) {
        return s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}
