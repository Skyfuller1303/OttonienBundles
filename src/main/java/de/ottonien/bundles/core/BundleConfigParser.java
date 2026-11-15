package de.ottonien.bundles.core;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.MalformedJsonException;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Strict parser and validator for {@code bundles.json}. Works on a snapshot of the text and never touches
 * live state, so a failed parse cannot damage the active configuration.
 */
public final class BundleConfigParser {

    public static final int DEFAULT_BUNDLE_SIZE = BundleRules.VANILLA_BUNDLE_SIZE;
    public static final int MIN_BUNDLE_SIZE = 1;

    /**
     * Upper limit of {@code bundleSize}. The client derives its fullness bar from the stack size an entry
     * carries inside the bundle, and 96 is the largest stack size {@link StoredSize} allows. A piece that
     * costs one point can therefore be shown as 1/96 at best: up to a bundle size of 96 the bar never
     * shows "full" while the server still accepts items, above it the bar would turn full too early.
     * The limit also stays far below the size at which vanilla's 32-bit fraction arithmetic could
     * overflow, see {@link VanillaWeight#MAX_WEIGHT}.
     */
    public static final int MAX_BUNDLE_SIZE = 96;

    public static final int MIN_SIZE = 1;

    /** Upper limit of {@code size}; far above any bundle size, it only keeps the arithmetic small. */
    public static final int MAX_SIZE = 1_000_000;

    public record Result(int bundleSize, Map<String, Integer> sizes, List<String> errors) {
        public boolean ok() {
            return errors.isEmpty();
        }
    }

    /** JSON number literal exactly as written, so that 8.0 or 1e1 can be rejected. */
    private record NumberLiteral(String text) {
    }

    private static final Object JSON_NULL = new Object();

    private BundleConfigParser() {
    }

    public static Result parse(String json) {
        List<String> errors = new ArrayList<>();
        Object root;
        try {
            JsonReader reader = new JsonReader(new StringReader(json));
            reader.setStrictness(com.google.gson.Strictness.STRICT);
            root = readValue(reader, "$", errors);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                errors.add("Unerwarteter Inhalt nach dem JSON-Dokument");
            }
        } catch (MalformedJsonException | IllegalStateException | NumberFormatException e) {
            return failed(List.of("Ungültiges JSON: " + e.getMessage()));
        } catch (IOException e) {
            return failed(List.of("JSON konnte nicht gelesen werden: " + e.getMessage()));
        }
        if (!errors.isEmpty()) {
            return failed(errors);
        }
        Map<String, Integer> sizes = new LinkedHashMap<>();
        int bundleSize = validateRoot(root, sizes, errors);
        if (!errors.isEmpty()) {
            return failed(errors);
        }
        return new Result(bundleSize, Map.copyOf(sizes), List.of());
    }

    private static Result failed(List<String> errors) {
        return new Result(DEFAULT_BUNDLE_SIZE, Map.of(), List.copyOf(errors));
    }

    @SuppressWarnings("unchecked")
    private static int validateRoot(Object root, Map<String, Integer> out, List<String> errors) {
        if (!(root instanceof Map<?, ?> rootMap)) {
            errors.add("Die Wurzel muss ein JSON-Objekt sein");
            return DEFAULT_BUNDLE_SIZE;
        }
        for (String key : ((Map<String, Object>) rootMap).keySet()) {
            if (!key.equals("items") && !key.equals("bundleSize")) {
                errors.add("Unbekanntes Feld '" + key + "' auf oberster Ebene");
            }
        }
        int bundleSize = DEFAULT_BUNDLE_SIZE;
        Object rawBundleSize = rootMap.get("bundleSize");
        if (rawBundleSize != null && rawBundleSize != JSON_NULL) {
            Integer parsed = integerField(rawBundleSize, "bundleSize", MIN_BUNDLE_SIZE, MAX_BUNDLE_SIZE, errors);
            if (parsed != null) {
                bundleSize = parsed;
            }
        }
        Object items = rootMap.get("items");
        if (items == null || items == JSON_NULL) {
            return bundleSize;
        }
        if (!(items instanceof Map<?, ?> itemsMap)) {
            errors.add("'items' muss ein JSON-Objekt sein");
            return bundleSize;
        }
        for (Map.Entry<String, Object> entry : ((Map<String, Object>) itemsMap).entrySet()) {
            String id = entry.getKey();
            if (id.isEmpty()) {
                errors.add("Leere Item-ID in 'items'");
                continue;
            }
            Object value = entry.getValue();
            if (!(value instanceof Map<?, ?> itemMap)) {
                errors.add("Eintrag '" + id + "' muss ein JSON-Objekt sein");
                continue;
            }
            for (Map.Entry<String, Object> field : ((Map<String, Object>) itemMap).entrySet()) {
                if (field.getKey().equals("bundleCapacity")) {
                    errors.add("Eintrag '" + id + "': 'bundleCapacity' gibt es nicht mehr. Bitte 'size' (Punkte "
                            + "pro Stück) eintragen, Umrechnung size = 64 / bundleCapacity"
                            + sizeHint(field.getValue()));
                } else if (!field.getKey().equals("size")) {
                    errors.add("Eintrag '" + id + "': unbekanntes Feld '" + field.getKey() + "'");
                }
            }
            Object size = itemMap.get("size");
            if (size == null || size == JSON_NULL) {
                continue; // missing or null means vanilla cost
            }
            Integer parsed = integerField(size, "Eintrag '" + id + "': size", MIN_SIZE, MAX_SIZE, errors);
            if (parsed != null) {
                out.put(id, parsed);
            }
        }
        return bundleSize;
    }

    /** Concrete suggestion for the replacement of an old bundleCapacity value, if it converts evenly. */
    private static String sizeHint(Object oldValue) {
        if (oldValue instanceof NumberLiteral literal) {
            Integer capacity = parseInteger(literal.text());
            if (capacity != null && capacity >= 1 && capacity <= 64 && 64 % capacity == 0) {
                return " (hier: \"size\": " + 64 / capacity + ")";
            }
        }
        return "";
    }

    private static Integer integerField(Object value, String name, int min, int max, List<String> errors) {
        if (!(value instanceof NumberLiteral literal)) {
            errors.add(name + " muss eine ganze Zahl von " + min + " bis " + max + " sein (gefunden: "
                    + describe(value) + ")");
            return null;
        }
        Integer parsed = parseInteger(literal.text());
        if (parsed == null) {
            errors.add(name + " '" + literal.text() + "' ist keine ganze Zahl");
            return null;
        }
        if (parsed < min || parsed > max) {
            errors.add(name + " " + (parsed == Integer.MAX_VALUE ? literal.text() : parsed.toString())
                    + " liegt außerhalb von " + min + " bis " + max);
            return null;
        }
        return parsed;
    }

    private static Integer parseInteger(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!(c >= '0' && c <= '9') && !(i == 0 && c == '-')) {
                return null;
            }
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE; // huge integer: reported as out of range
        }
    }

    private static String describe(Object value) {
        if (value instanceof String s) {
            return s.isEmpty() ? "leerer String" : "String \"" + s + "\"";
        }
        if (value instanceof Boolean b) {
            return "Boolean " + b;
        }
        if (value instanceof Map<?, ?>) {
            return "Objekt";
        }
        return "Array";
    }

    private static Object readValue(JsonReader reader, String path, List<String> errors) throws IOException {
        JsonToken token = reader.peek();
        switch (token) {
            case BEGIN_OBJECT -> {
                Map<String, Object> map = new LinkedHashMap<>();
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    Object value = readValue(reader, path + "." + name, errors);
                    if (map.containsKey(name)) {
                        errors.add("Doppelter JSON-Schlüssel '" + name + "' unter " + path);
                    } else {
                        map.put(name, value);
                    }
                }
                reader.endObject();
                return map;
            }
            case BEGIN_ARRAY -> {
                List<Object> list = new ArrayList<>();
                reader.beginArray();
                int i = 0;
                while (reader.hasNext()) {
                    list.add(readValue(reader, path + "[" + i++ + "]", errors));
                }
                reader.endArray();
                return list;
            }
            case STRING -> {
                return reader.nextString();
            }
            case NUMBER -> {
                return new NumberLiteral(reader.nextString());
            }
            case BOOLEAN -> {
                return reader.nextBoolean();
            }
            case NULL -> {
                reader.nextNull();
                return JSON_NULL;
            }
            default -> throw new IllegalStateException("Unerwartetes Token " + token + " bei " + path);
        }
    }
}
