package org.unlaxer.dsl;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Report JSON writer implementation for reportVersion=1.
 */
final class ReportJsonWriterV1 {

    private ReportJsonWriterV1() {}

    static String validationSuccess(String toolVersion, String generatedAt, int grammarCount) {
        return "{\"reportVersion\":1"
            + ",\"toolVersion\":\"" + ReportJsonWriter.escapeJson(toolVersion) + "\""
            + ",\"generatedAt\":\"" + ReportJsonWriter.escapeJson(generatedAt) + "\""
            + ",\"mode\":\"validate\""
            + ",\"ok\":true,\"grammarCount\":" + grammarCount + ",\"issues\":[]}";
    }

    static String validationFailure(
        String toolVersion,
        String generatedAt,
        List<ReportJsonWriter.ValidationIssueRow> rows
    ) {
        Map<String, Integer> severityCounts = new TreeMap<>();
        Map<String, Integer> categoryCounts = new TreeMap<>();
        for (ReportJsonWriter.ValidationIssueRow row : rows) {
            severityCounts.merge(row.severity(), 1, Integer::sum);
            categoryCounts.merge(row.category(), 1, Integer::sum);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\"reportVersion\":1")
            .append(",\"toolVersion\":\"").append(ReportJsonWriter.escapeJson(toolVersion)).append("\"")
            .append(",\"generatedAt\":\"").append(ReportJsonWriter.escapeJson(generatedAt)).append("\"")
            .append(",\"mode\":\"validate\",\"ok\":false,\"issueCount\":")
            .append(rows.size())
            .append(",\"severityCounts\":").append(toCountsJson(severityCounts))
            .append(",\"categoryCounts\":").append(toCountsJson(categoryCounts))
            .append(",\"issues\":[");

        for (int i = 0; i < rows.size(); i++) {
            ReportJsonWriter.ValidationIssueRow row = rows.get(i);
            if (i > 0) {
                sb.append(",");
            }
            sb.append("{")
                .append("\"grammar\":\"").append(ReportJsonWriter.escapeJson(row.grammar())).append("\",")
                .append("\"rule\":");
            if (row.rule() == null) {
                sb.append("null,");
            } else {
                sb.append("\"").append(ReportJsonWriter.escapeJson(row.rule())).append("\",");
            }
            sb.append("\"code\":\"").append(ReportJsonWriter.escapeJson(row.code())).append("\",")
                .append("\"severity\":\"").append(ReportJsonWriter.escapeJson(row.severity())).append("\",")
                .append("\"category\":\"").append(ReportJsonWriter.escapeJson(row.category())).append("\",")
                .append("\"message\":\"").append(ReportJsonWriter.escapeJson(row.message())).append("\",")
                .append("\"hint\":\"").append(ReportJsonWriter.escapeJson(row.hint())).append("\"")
                .append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    static String generationSuccess(
        String toolVersion,
        String generatedAt,
        int grammarCount,
        List<String> generatedFiles
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"reportVersion\":1")
            .append(",\"toolVersion\":\"").append(ReportJsonWriter.escapeJson(toolVersion)).append("\"")
            .append(",\"generatedAt\":\"").append(ReportJsonWriter.escapeJson(generatedAt)).append("\"")
            .append(",\"mode\":\"generate\",\"ok\":true,\"grammarCount\":")
            .append(grammarCount)
            .append(",\"generatedCount\":").append(generatedFiles.size())
            .append(",\"generatedFiles\":[");

        for (int i = 0; i < generatedFiles.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("\"").append(ReportJsonWriter.escapeJson(generatedFiles.get(i))).append("\"");
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
            sb.append("\"").append(ReportJsonWriter.escapeJson(entry.getKey())).append("\":")
                .append(entry.getValue());
        }
        sb.append("}");
        return sb.toString();
    }
}
