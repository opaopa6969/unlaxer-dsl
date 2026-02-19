package org.unlaxer.dsl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
            requireNumber(obj, "reportVersion");
            requireString(obj, "schemaVersion");
            requireString(obj, "schemaUrl");
            requireString(obj, "toolVersion");
            requireString(obj, "argsHash");
            requireString(obj, "generatedAt");
            requireNumber(obj, "grammarCount");
            requireNumber(obj, "warningsCount");
            requireArray(obj, "issues");
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
                    "issueCount",
                    "warningsCount",
                    "severityCounts",
                    "categoryCounts",
                    "issues"
                )
            );
            requireNumber(obj, "reportVersion");
            requireString(obj, "schemaVersion");
            requireString(obj, "schemaUrl");
            requireString(obj, "toolVersion");
            requireString(obj, "argsHash");
            requireString(obj, "generatedAt");
            requireNumber(obj, "issueCount");
            requireNumber(obj, "warningsCount");
            requireObject(obj, "severityCounts");
            requireObject(obj, "categoryCounts");
            requireArray(obj, "issues");
            return;
        }

        if ("generate".equals(mode) && ok) {
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
                    "generatedCount",
                    "warningsCount",
                    "writtenCount",
                    "skippedCount",
                    "conflictCount",
                    "dryRunCount",
                    "generatedFiles"
                )
            );
            requireNumber(obj, "reportVersion");
            requireString(obj, "schemaVersion");
            requireString(obj, "schemaUrl");
            requireString(obj, "toolVersion");
            requireString(obj, "argsHash");
            requireString(obj, "generatedAt");
            requireNumber(obj, "grammarCount");
            requireNumber(obj, "generatedCount");
            requireNumber(obj, "warningsCount");
            requireNumber(obj, "writtenCount");
            requireNumber(obj, "skippedCount");
            requireNumber(obj, "conflictCount");
            requireNumber(obj, "dryRunCount");
            requireArray(obj, "generatedFiles");
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
        if (!(value instanceof String s)) {
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
