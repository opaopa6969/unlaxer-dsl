package org.unlaxer.dsl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Validates manifest JSON/NDJSON schema contracts.
 */
final class ManifestSchemaValidator {

    private ManifestSchemaValidator() {}

    static void validate(String manifestFormat, String payload) {
        if ("json".equals(manifestFormat)) {
            validateJson(payload);
            return;
        }
        if ("ndjson".equals(manifestFormat)) {
            validateNdjson(payload);
            return;
        }
        fail("E-MANIFEST-SCHEMA-UNSUPPORTED-FORMAT", "Unsupported manifest format: " + manifestFormat);
    }

    private static void validateJson(String payload) {
        Map<String, Object> obj = parseObject(payload);
        requireTopLevelOrder(
            obj,
            List.of(
                "mode",
                "generatedAt",
                "toolVersion",
                "argsHash",
                "ok",
                "failReasonCode",
                "exitCode",
                "warningsCount",
                "writtenCount",
                "skippedCount",
                "conflictCount",
                "dryRunCount",
                "files"
            )
        );
        requireString(obj, "mode");
        requireString(obj, "generatedAt");
        requireString(obj, "toolVersion");
        requireString(obj, "argsHash");
        requireBoolean(obj, "ok");
        requireNullableString(obj, "failReasonCode");
        requireNumber(obj, "exitCode");
        requireNumber(obj, "warningsCount");
        requireNumber(obj, "writtenCount");
        requireNumber(obj, "skippedCount");
        requireNumber(obj, "conflictCount");
        requireNumber(obj, "dryRunCount");
        List<Object> files = requireArray(obj, "files");
        for (Object item : files) {
            if (!(item instanceof Map<?, ?>)) {
                fail("E-MANIFEST-SCHEMA-TYPE", "Expected object in files array");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> fileObj = (Map<String, Object>) item;
            requireTopLevelOrder(fileObj, List.of("action", "path"));
            requireString(fileObj, "action");
            requireString(fileObj, "path");
        }
    }

    private static void validateNdjson(String payload) {
        String[] lines = payload.split("\\R");
        if (lines.length == 0) {
            fail("E-MANIFEST-SCHEMA-EMPTY", "NDJSON manifest payload is empty");
        }
        int summaryCount = 0;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            Map<String, Object> obj = parseObject(line);
            String event = requireString(obj, "event");
            if ("file".equals(event)) {
                requireTopLevelOrder(obj, List.of("event", "action", "path"));
                requireString(obj, "action");
                requireString(obj, "path");
                continue;
            }
            if ("manifest-summary".equals(event)) {
                summaryCount++;
                requireTopLevelOrder(
                    obj,
                    List.of(
                        "event",
                        "mode",
                        "generatedAt",
                        "toolVersion",
                        "argsHash",
                        "ok",
                        "failReasonCode",
                        "exitCode",
                        "warningsCount",
                        "writtenCount",
                        "skippedCount",
                        "conflictCount",
                        "dryRunCount"
                    )
                );
                requireString(obj, "mode");
                requireString(obj, "generatedAt");
                requireString(obj, "toolVersion");
                requireString(obj, "argsHash");
                requireBoolean(obj, "ok");
                requireNullableString(obj, "failReasonCode");
                requireNumber(obj, "exitCode");
                requireNumber(obj, "warningsCount");
                requireNumber(obj, "writtenCount");
                requireNumber(obj, "skippedCount");
                requireNumber(obj, "conflictCount");
                requireNumber(obj, "dryRunCount");
                continue;
            }
            fail("E-MANIFEST-SCHEMA-INVALID-EVENT", "Unsupported manifest event: " + event);
        }
        if (summaryCount != 1) {
            fail("E-MANIFEST-SCHEMA-SUMMARY", "NDJSON manifest must include exactly one manifest-summary event");
        }
    }

    private static void requireTopLevelOrder(Map<String, Object> obj, List<String> expectedOrder) {
        List<String> keys = new ArrayList<>(obj.keySet());
        if (!keys.equals(expectedOrder)) {
            fail(
                "E-MANIFEST-SCHEMA-KEY-ORDER",
                "Unexpected JSON keys/order. expected=" + expectedOrder + " actual=" + keys
            );
        }
    }

    private static String requireString(Map<String, Object> obj, String key) {
        Object value = requireKey(obj, key);
        if (!(value instanceof String s)) {
            fail("E-MANIFEST-SCHEMA-TYPE", "Expected string for key: " + key);
        }
        return (String) value;
    }

    private static String requireNullableString(Map<String, Object> obj, String key) {
        Object value = requireKey(obj, key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String s)) {
            fail("E-MANIFEST-SCHEMA-TYPE", "Expected nullable string for key: " + key);
        }
        return (String) value;
    }

    private static boolean requireBoolean(Map<String, Object> obj, String key) {
        Object value = requireKey(obj, key);
        if (!(value instanceof Boolean b)) {
            fail("E-MANIFEST-SCHEMA-TYPE", "Expected boolean for key: " + key);
        }
        return (Boolean) value;
    }

    private static Number requireNumber(Map<String, Object> obj, String key) {
        Object value = requireKey(obj, key);
        if (!(value instanceof Number n)) {
            fail("E-MANIFEST-SCHEMA-TYPE", "Expected number for key: " + key);
        }
        return (Number) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> requireArray(Map<String, Object> obj, String key) {
        Object value = requireKey(obj, key);
        if (!(value instanceof List<?>)) {
            fail("E-MANIFEST-SCHEMA-TYPE", "Expected array for key: " + key);
        }
        return (List<Object>) value;
    }

    private static Object requireKey(Map<String, Object> obj, String key) {
        if (!obj.containsKey(key)) {
            fail("E-MANIFEST-SCHEMA-MISSING-KEY", "Missing key: " + key);
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
            fail("E-MANIFEST-SCHEMA-PARSE", e.getMessage());
            return Map.of();
        }
        if (!p.isEnd()) {
            fail("E-MANIFEST-SCHEMA-PARSE", "unexpected trailing characters");
        }
        if (!(v instanceof Map<?, ?> m)) {
            fail("E-MANIFEST-SCHEMA-PARSE", "expected JSON object");
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
                fail("E-MANIFEST-SCHEMA-PARSE", "unexpected end");
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
                    fail("E-MANIFEST-SCHEMA-PARSE", "unexpected char: " + c);
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
                Object val = parseValue();
                obj.put(key, val);
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
                        fail("E-MANIFEST-SCHEMA-PARSE", "invalid escape");
                    }
                    char e = s.charAt(i++);
                    switch (e) {
                        case '"', '\\', '/' -> sb.append(e);
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (i + 4 > s.length()) {
                                fail("E-MANIFEST-SCHEMA-PARSE", "invalid unicode escape");
                            }
                            String hex = s.substring(i, i + 4);
                            i += 4;
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                            } catch (NumberFormatException ex) {
                                fail("E-MANIFEST-SCHEMA-PARSE", "invalid unicode escape");
                            }
                        }
                        default -> fail("E-MANIFEST-SCHEMA-PARSE", "invalid escape: \\" + e);
                    }
                } else {
                    sb.append(c);
                }
            }
            fail("E-MANIFEST-SCHEMA-PARSE", "unterminated string");
            return "";
        }

        private Object parseLiteral(String literal, Object value) {
            if (!s.startsWith(literal, i)) {
                fail("E-MANIFEST-SCHEMA-PARSE", "expected literal: " + literal);
            }
            i += literal.length();
            return value;
        }

        private Number parseNumber() {
            int start = i;
            if (s.charAt(i) == '-') {
                i++;
            }
            while (!isEnd() && Character.isDigit(s.charAt(i))) {
                i++;
            }
            boolean isFloat = false;
            if (!isEnd() && s.charAt(i) == '.') {
                isFloat = true;
                i++;
                while (!isEnd() && Character.isDigit(s.charAt(i))) {
                    i++;
                }
            }
            if (!isEnd() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                isFloat = true;
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
                if (isFloat) {
                    return Double.parseDouble(token);
                }
                return Long.parseLong(token);
            } catch (NumberFormatException e) {
                fail("E-MANIFEST-SCHEMA-PARSE", "invalid number: " + token);
                return 0L;
            }
        }

        private boolean peek(char c) {
            skipWhitespace();
            return !isEnd() && s.charAt(i) == c;
        }

        private void expect(char c) {
            skipWhitespace();
            if (isEnd() || s.charAt(i) != c) {
                fail("E-MANIFEST-SCHEMA-PARSE", "expected '" + c + "'");
            }
            i++;
        }
    }
}
