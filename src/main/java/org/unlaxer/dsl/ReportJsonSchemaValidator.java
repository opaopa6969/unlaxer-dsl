package org.unlaxer.dsl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.Instant;

/**
 * Validates JSON report schema contracts by reportVersion.
 */
final class ReportJsonSchemaValidator {

    private ReportJsonSchemaValidator() {}

    static void validate(int reportVersion, String json) {
        switch (reportVersion) {
            case 1 -> validateV1(json);
            default -> fail(
                "E-REPORT-SCHEMA-UNSUPPORTED-VERSION",
                "Unsupported reportVersion for validation: " + reportVersion
            );
        }
    }

    private static void validateV1(String json) {
        Map<String, Object> obj = parseObject(json);
        String mode = requireString(obj, "mode");
        boolean ok = requireBoolean(obj, "ok");

        if ("validate".equals(mode) && ok) {
            requireTopLevelOrder(
                obj,
                List.of(
                    "reportVersion",
                    "schemaVersion",
                    "schemaUrl",
                    "toolVersion",
                    "argsHash",
                    "generatedAt",
                    "mode",
                    "ok",
                    "grammarCount",
                    "warningsCount",
                    "issues"
                )
            );
            requireConstInteger(obj, "reportVersion", 1);
            requireConstString(obj, "schemaVersion", "1.0");
            requireConstString(obj, "schemaUrl", "https://unlaxer.dev/schema/report-v1.json");
            requireString(obj, "toolVersion");
            requireString(obj, "argsHash");
            requireDateTimeString(obj, "generatedAt");
            requireConstString(obj, "mode", "validate");
            requireConstBoolean(obj, "ok", true);
            requireIntegerMin(obj, "grammarCount", 0);
            requireIntegerMin(obj, "warningsCount", 0);
            List<Object> issues = requireArray(obj, "issues");
            if (!issues.isEmpty()) {
                fail("E-REPORT-SCHEMA-CONSTRAINT", "validate-success issues must be empty");
            }
            return;
        }

        if ("validate".equals(mode) && !ok) {
            requireTopLevelOrder(
                obj,
                List.of(
                    "reportVersion",
                    "schemaVersion",
                    "schemaUrl",
                    "toolVersion",
                    "argsHash",
                    "generatedAt",
                    "mode",
                    "ok",
                    "failReasonCode",
                    "issueCount",
                    "warningsCount",
                    "severityCounts",
                    "categoryCounts",
                    "issues"
                )
            );
            requireConstInteger(obj, "reportVersion", 1);
            requireConstString(obj, "schemaVersion", "1.0");
            requireConstString(obj, "schemaUrl", "https://unlaxer.dev/schema/report-v1.json");
            requireString(obj, "toolVersion");
            requireString(obj, "argsHash");
            requireDateTimeString(obj, "generatedAt");
            requireConstString(obj, "mode", "validate");
            requireConstBoolean(obj, "ok", false);
            requireNullableEnum(
                obj,
                "failReasonCode",
                Set.of("FAIL_ON_WARNING", "FAIL_ON_WARNINGS_COUNT")
            );
            requireIntegerMin(obj, "issueCount", 1);
            requireIntegerMin(obj, "warningsCount", 0);
            requireCountMap(requireObject(obj, "severityCounts"), "severityCounts");
            requireCountMap(requireObject(obj, "categoryCounts"), "categoryCounts");
            List<Object> issues = requireArray(obj, "issues");
            if (issues.isEmpty()) {
                fail("E-REPORT-SCHEMA-CONSTRAINT", "validate-failure issues must contain at least one item");
            }
            for (Object issueObj : issues) {
                if (!(issueObj instanceof Map<?, ?>)) {
                    fail("E-REPORT-SCHEMA-TYPE", "Expected issue object");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> issue = (Map<String, Object>) issueObj;
                validateIssue(issue);
            }
            return;
        }

        if ("generate".equals(mode)) {
            requireTopLevelOrder(
                obj,
                List.of(
                    "reportVersion",
                    "schemaVersion",
                    "schemaUrl",
                    "toolVersion",
                    "argsHash",
                    "generatedAt",
                    "mode",
                    "ok",
                    "failReasonCode",
                    "grammarCount",
                    "generatedCount",
                    "warningsCount",
                    "writtenCount",
                    "skippedCount",
                    "conflictCount",
                    "dryRunCount",
                    "generatedFiles"
                )
            );
            requireConstInteger(obj, "reportVersion", 1);
            requireConstString(obj, "schemaVersion", "1.0");
            requireConstString(obj, "schemaUrl", "https://unlaxer.dev/schema/report-v1.json");
            requireString(obj, "toolVersion");
            requireString(obj, "argsHash");
            requireDateTimeString(obj, "generatedAt");
            requireConstString(obj, "mode", "generate");
            requireBoolean(obj, "ok");
            requireNullableEnum(
                obj,
                "failReasonCode",
                Set.of("FAIL_ON_SKIPPED", "FAIL_ON_CONFLICT", "FAIL_ON_CLEANED")
            );
            requireIntegerMin(obj, "grammarCount", 0);
            requireIntegerMin(obj, "generatedCount", 0);
            requireIntegerMin(obj, "warningsCount", 0);
            requireIntegerMin(obj, "writtenCount", 0);
            requireIntegerMin(obj, "skippedCount", 0);
            requireIntegerMin(obj, "conflictCount", 0);
            requireIntegerMin(obj, "dryRunCount", 0);
            List<Object> generatedFiles = requireArray(obj, "generatedFiles");
            for (Object file : generatedFiles) {
                if (!(file instanceof String s) || s.isBlank()) {
                    fail("E-REPORT-SCHEMA-TYPE", "generatedFiles items must be non-empty strings");
                }
            }
            return;
        }

        fail(
            "E-REPORT-SCHEMA-INVALID-SHAPE",
            "Unsupported report payload shape for mode='" + mode + "' and ok=" + ok
        );
    }

    private static void requireTopLevelOrder(Map<String, Object> obj, List<String> expectedOrder) {
        List<String> keys = new ArrayList<>(obj.keySet());
        if (!keys.equals(expectedOrder)) {
            fail(
                "E-REPORT-SCHEMA-KEY-ORDER",
                "Unexpected JSON keys/order. expected=" + expectedOrder + " actual=" + keys
            );
        }
    }

    private static String requireString(Map<String, Object> obj, String key) {
        Object value = requireKey(obj, key);
        if (!(value instanceof String s) || s.isBlank()) {
            fail("E-REPORT-SCHEMA-TYPE", "Expected string for key: " + key);
        }
        return (String) value;
    }

    private static boolean requireBoolean(Map<String, Object> obj, String key) {
        Object value = requireKey(obj, key);
        if (!(value instanceof Boolean b)) {
            fail("E-REPORT-SCHEMA-TYPE", "Expected boolean for key: " + key);
        }
        return (Boolean) value;
    }

    private static Number requireNumber(Map<String, Object> obj, String key) {
        Object value = requireKey(obj, key);
        if (!(value instanceof Number n)) {
            fail("E-REPORT-SCHEMA-TYPE", "Expected number for key: " + key);
        }
        return (Number) value;
    }

    private static String requireNullableString(Map<String, Object> obj, String key) {
        Object value = requireKey(obj, key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String s) || s.isBlank()) {
            fail("E-REPORT-SCHEMA-TYPE", "Expected nullable string for key: " + key);
        }
        return (String) value;
    }

    private static long requireIntegerMin(Map<String, Object> obj, String key, long min) {
        Number n = requireNumber(obj, key);
        if (!(n instanceof Integer || n instanceof Long)) {
            fail("E-REPORT-SCHEMA-TYPE", "Expected integer for key: " + key);
        }
        long value = n.longValue();
        if (value < min) {
            fail("E-REPORT-SCHEMA-CONSTRAINT", "Expected " + key + " >= " + min);
        }
        return value;
    }

    private static void requireConstString(Map<String, Object> obj, String key, String expected) {
        String actual = requireString(obj, key);
        if (!expected.equals(actual)) {
            fail("E-REPORT-SCHEMA-CONSTRAINT", "Expected " + key + " == " + expected + " but was " + actual);
        }
    }

    private static void requireConstBoolean(Map<String, Object> obj, String key, boolean expected) {
        boolean actual = requireBoolean(obj, key);
        if (actual != expected) {
            fail("E-REPORT-SCHEMA-CONSTRAINT", "Expected " + key + " == " + expected + " but was " + actual);
        }
    }

    private static void requireConstInteger(Map<String, Object> obj, String key, long expected) {
        long actual = requireIntegerMin(obj, key, expected);
        if (actual != expected) {
            fail("E-REPORT-SCHEMA-CONSTRAINT", "Expected " + key + " == " + expected + " but was " + actual);
        }
    }

    private static void requireNullableEnum(Map<String, Object> obj, String key, Set<String> allowed) {
        String value = requireNullableString(obj, key);
        if (value == null) {
            return;
        }
        if (!allowed.contains(value)) {
            fail("E-REPORT-SCHEMA-CONSTRAINT", "Unsupported value for " + key + ": " + value);
        }
    }

    private static void requireDateTimeString(Map<String, Object> obj, String key) {
        String value = requireString(obj, key);
        try {
            Instant.parse(value);
        } catch (RuntimeException e) {
            fail("E-REPORT-SCHEMA-CONSTRAINT", "Expected ISO-8601 instant for key: " + key);
        }
    }

    private static void requireCountMap(Map<String, Object> map, String key) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String k = entry.getKey();
            if (k == null || k.isBlank()) {
                fail("E-REPORT-SCHEMA-TYPE", key + " must not contain blank keys");
            }
            Object value = entry.getValue();
            if (!(value instanceof Number n) || !(n instanceof Integer || n instanceof Long) || n.longValue() < 1) {
                fail("E-REPORT-SCHEMA-CONSTRAINT", key + " values must be integers >= 1");
            }
        }
    }

    private static void validateIssue(Map<String, Object> issue) {
        Set<String> expected = Set.of("grammar", "rule", "code", "severity", "category", "message", "hint");
        if (!new HashSet<>(issue.keySet()).equals(expected)) {
            fail("E-REPORT-SCHEMA-KEY-ORDER", "Unexpected issue keys: " + issue.keySet());
        }
        requireString(issue, "grammar");
        Object rule = requireKey(issue, "rule");
        if (!(rule == null || (rule instanceof String s && !s.isBlank()))) {
            fail("E-REPORT-SCHEMA-TYPE", "Expected nullable non-empty string for key: rule");
        }
        requireString(issue, "code");
        requireString(issue, "severity");
        requireString(issue, "category");
        requireString(issue, "message");
        requireString(issue, "hint");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> requireObject(Map<String, Object> obj, String key) {
        Object value = requireKey(obj, key);
        if (!(value instanceof Map<?, ?>)) {
            fail("E-REPORT-SCHEMA-TYPE", "Expected object for key: " + key);
        }
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> requireArray(Map<String, Object> obj, String key) {
        Object value = requireKey(obj, key);
        if (!(value instanceof List<?>)) {
            fail("E-REPORT-SCHEMA-TYPE", "Expected array for key: " + key);
        }
        return (List<Object>) value;
    }

    private static Object requireKey(Map<String, Object> obj, String key) {
        if (!obj.containsKey(key)) {
            fail("E-REPORT-SCHEMA-MISSING-KEY", "Missing key: " + key);
        }
        return obj.get(key);
    }

    private static Map<String, Object> parseObject(String json) {
        Parser p = new Parser(json);
        Object v;
        try {
            v = p.parseValue();
            p.skipWhitespace();
        } catch (ReportSchemaValidationException e) {
            throw e;
        } catch (RuntimeException e) {
            fail("E-REPORT-SCHEMA-PARSE", e.getMessage());
            return Map.of();
        }

        if (!p.isEnd()) {
            fail("E-REPORT-SCHEMA-PARSE", "unexpected trailing characters");
        }
        if (!(v instanceof Map<?, ?> m)) {
            fail("E-REPORT-SCHEMA-PARSE", "expected JSON object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) v;
        return out;
    }

    private static void fail(String code, String message) {
        throw new ReportSchemaValidationException(code, message);
    }

    private static final class Parser {
        private final String s;
        private int i;

        private Parser(String s) {
            this.s = s;
            this.i = 0;
        }

        private boolean isEnd() {
            return i >= s.length();
        }

        private void skipWhitespace() {
            while (!isEnd()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                    i++;
                } else {
                    break;
                }
            }
        }

        private Object parseValue() {
            skipWhitespace();
            if (isEnd()) {
                fail("E-REPORT-SCHEMA-PARSE", "unexpected end");
            }
            char c = s.charAt(i);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> {
                    if (c == '-' || Character.isDigit(c)) {
                        yield parseNumber();
                    }
                    fail("E-REPORT-SCHEMA-PARSE", "unexpected char: " + c);
                    yield null;
                }
            };
        }

        private Map<String, Object> parseObject() {
            expect('{');
            skipWhitespace();
            Map<String, Object> obj = new LinkedHashMap<>();
            if (peek('}')) {
                expect('}');
                return obj;
            }
            while (true) {
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                obj.put(key, value);
                skipWhitespace();
                if (peek('}')) {
                    expect('}');
                    break;
                }
                expect(',');
            }
            return obj;
        }

        private List<Object> parseArray() {
            expect('[');
            skipWhitespace();
            List<Object> arr = new ArrayList<>();
            if (peek(']')) {
                expect(']');
                return arr;
            }
            while (true) {
                arr.add(parseValue());
                skipWhitespace();
                if (peek(']')) {
                    expect(']');
                    break;
                }
                expect(',');
            }
            return arr;
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (!isEnd()) {
                char c = s.charAt(i++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (isEnd()) {
                        fail("E-REPORT-SCHEMA-PARSE", "invalid escape");
                    }
                    char e = s.charAt(i++);
                    switch (e) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (i + 4 > s.length()) {
                                fail("E-REPORT-SCHEMA-PARSE", "invalid unicode escape");
                            }
                            String hex = s.substring(i, i + 4);
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                            } catch (NumberFormatException ex) {
                                fail("E-REPORT-SCHEMA-PARSE", "invalid unicode escape: " + hex);
                            }
                            i += 4;
                        }
                        default -> fail("E-REPORT-SCHEMA-PARSE", "invalid escape: \\" + e);
                    }
                } else {
                    sb.append(c);
                }
            }
            fail("E-REPORT-SCHEMA-PARSE", "unterminated string");
            return null;
        }

        private Object parseLiteral(String text, Object value) {
            if (s.startsWith(text, i)) {
                i += text.length();
                return value;
            }
            fail("E-REPORT-SCHEMA-PARSE", "invalid literal");
            return null;
        }

        private Number parseNumber() {
            int start = i;
            if (peek('-')) {
                i++;
            }
            while (!isEnd() && Character.isDigit(s.charAt(i))) {
                i++;
            }
            boolean isDecimal = false;
            if (!isEnd() && s.charAt(i) == '.') {
                isDecimal = true;
                i++;
                while (!isEnd() && Character.isDigit(s.charAt(i))) {
                    i++;
                }
            }
            if (!isEnd() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                isDecimal = true;
                i++;
                if (!isEnd() && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
                    i++;
                }
                while (!isEnd() && Character.isDigit(s.charAt(i))) {
                    i++;
                }
            }
            String token = s.substring(start, i);
            try {
                if (isDecimal) {
                    return Double.parseDouble(token);
                }
                return Long.parseLong(token);
            } catch (NumberFormatException e) {
                fail("E-REPORT-SCHEMA-PARSE", "invalid number: " + token);
                return 0;
            }
        }

        private boolean peek(char c) {
            skipWhitespace();
            return !isEnd() && s.charAt(i) == c;
        }

        private void expect(char c) {
            skipWhitespace();
            if (isEnd() || s.charAt(i) != c) {
                fail("E-REPORT-SCHEMA-PARSE", "expected '" + c + "'");
            }
            i++;
        }
    }
}
