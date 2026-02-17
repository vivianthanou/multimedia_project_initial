package gr.ntua.multimedia.persistence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SimpleJson {
    private SimpleJson() {}

    static Object parse(String json) {
        return new Parser(json).parseValue();
    }

    static String stringify(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) return '"' + escape(s) + '"';
        if (value instanceof Number || value instanceof Boolean) return String.valueOf(value);
        if (value instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append(stringify(String.valueOf(e.getKey()))).append(':').append(stringify(e.getValue()));
            }
            return sb.append('}').toString();
        }
        if (value instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object o : list) {
                if (!first) sb.append(',');
                first = false;
                sb.append(stringify(o));
            }
            return sb.append(']').toString();
        }
        throw new IllegalArgumentException("Unsupported JSON type: " + value.getClass());
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) { this.s = s; }

        Object parseValue() {
            skipWs();
            if (i >= s.length()) throw new IllegalArgumentException("Unexpected end");
            char c = s.charAt(i);
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return parseString();
            if (c == 't' && s.startsWith("true", i)) { i += 4; return true; }
            if (c == 'f' && s.startsWith("false", i)) { i += 5; return false; }
            if (c == 'n' && s.startsWith("null", i)) { i += 4; return null; }
            return parseNumber();
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWs();
            if (peek('}')) { i++; return map; }
            while (true) {
                String key = parseString();
                skipWs();
                expect(':');
                Object value = parseValue();
                map.put(key, value);
                skipWs();
                if (peek('}')) { i++; break; }
                expect(',');
            }
            return map;
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWs();
            if (peek(']')) { i++; return list; }
            while (true) {
                list.add(parseValue());
                skipWs();
                if (peek(']')) { i++; break; }
                expect(',');
            }
            return list;
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') break;
                if (c == '\\') {
                    char n = s.charAt(i++);
                    if (n == 'n') sb.append('\n');
                    else sb.append(n);
                } else sb.append(c);
            }
            return sb.toString();
        }

        private Number parseNumber() {
            int start = i;
            while (i < s.length() && "-0123456789.eE+".indexOf(s.charAt(i)) >= 0) i++;
            String num = s.substring(start, i);
            if (num.contains(".") || num.contains("e") || num.contains("E")) return Double.parseDouble(num);
            return Long.parseLong(num);
        }

        private void skipWs() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        private boolean peek(char c) { skipWs(); return i < s.length() && s.charAt(i) == c; }
        private void expect(char c) { skipWs(); if (i >= s.length() || s.charAt(i) != c) throw new IllegalArgumentException("Expected " + c); i++; }
    }
}