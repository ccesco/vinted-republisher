package fr.vinted.republisher.adapter.out.browser;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The data a Next.js page streams in its scripts {@code self.__next_f.push([1,"…"])}: decoded and put end to end,
 * they form React Flight rows. A JSON row {@code <key>:<JSON>} ends with a line break; a text row
 * {@code <key>:T<length in bytes, hex>,<text>} holds a long text, which may span lines, referred to as
 * {@code "$<key>"}. Rows of other kinds are not JSON and hold no data the tool reads.
 */
final class FlightPayload {

    private static final String CHUNK_PREFIX = "self.__next_f.push([1,";
    private static final String CHUNK_SUFFIX = "])";
    private static final Pattern TEXT_REFERENCE = Pattern.compile("\\$([0-9a-f]+)");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final List<JsonNode> jsonRows;
    private final Map<String, String> texts;

    private FlightPayload(List<JsonNode> jsonRows, Map<String, String> texts) {
        this.jsonRows = List.copyOf(jsonRows);
        this.texts = Map.copyOf(texts);
    }

    /** Throws {@link JacksonException} or {@link IllegalArgumentException} on a chunk or a row out of shape. */
    static FlightPayload of(List<String> scripts) {
        String payload = scripts.stream().map(String::strip)
                .filter(script -> script.startsWith(CHUNK_PREFIX) && script.endsWith(CHUNK_SUFFIX))
                .map(script -> JSON.readValue(script.substring(CHUNK_PREFIX.length(),
                        script.length() - CHUNK_SUFFIX.length()), String.class))
                .collect(Collectors.joining());
        return parse(payload.getBytes(StandardCharsets.UTF_8));
    }

    List<JsonNode> jsonRows() {
        return jsonRows;
    }

    /** The objects of the rows, nested ones included, that are wanted; each distinct one once. */
    Set<JsonNode> objects(Predicate<JsonNode> wanted) {
        Set<JsonNode> found = new LinkedHashSet<>();
        jsonRows.forEach(row -> collect(row, wanted, found));
        return found;
    }

    private static void collect(JsonNode node, Predicate<JsonNode> wanted, Set<JsonNode> found) {
        if (node.isObject() && wanted.test(node)) {
            found.add(node);
        }
        if (node.isContainer()) {
            node.valueStream().forEach(child -> collect(child, wanted, found));
        }
    }

    /** React escapes a text starting with "$" as "$$", and sends a long one apart as "$<key>". */
    String text(String value) {
        if (value.startsWith("$$")) {
            return value.substring(1);
        }
        if (!value.startsWith("$")) {
            return value;
        }
        String key = TEXT_REFERENCE.matcher(value).matches() ? value.substring(1) : "";
        String text = texts.get(key);
        if (text == null) {
            throw new IllegalArgumentException("unresolved reference " + value);
        }
        return text;
    }

    /** Works on bytes, as the length of a text row is given in bytes. */
    private static FlightPayload parse(byte[] payload) {
        List<JsonNode> jsonRows = new ArrayList<>();
        Map<String, String> texts = new HashMap<>();
        int start = 0;
        while (start < payload.length) {
            int colon = indexOf(payload, (byte) ':', start);
            if (colon < 0) {
                break;
            }
            String key = new String(payload, start, colon - start, StandardCharsets.UTF_8);
            if (colon + 1 < payload.length && payload[colon + 1] == 'T') {
                start = readText(payload, colon + 2, key, texts);
            } else {
                int end = indexOf(payload, (byte) '\n', colon + 1);
                int rowEnd = end < 0 ? payload.length : end;
                parsed(new String(payload, colon + 1, rowEnd - colon - 1, StandardCharsets.UTF_8))
                        .ifPresent(jsonRows::add);
                start = rowEnd + 1;
            }
        }
        return new FlightPayload(jsonRows, texts);
    }

    private static int readText(byte[] payload, int lengthStart, String key, Map<String, String> texts) {
        int comma = indexOf(payload, (byte) ',', lengthStart);
        if (comma < 0) {
            throw new IllegalArgumentException("text row " + key + " without length");
        }
        int length = Integer.parseInt(new String(payload, lengthStart, comma - lengthStart, StandardCharsets.UTF_8), 16);
        if (comma + 1 + length > payload.length) {
            throw new IllegalArgumentException("text row " + key + " cut short");
        }
        texts.put(key, new String(payload, comma + 1, length, StandardCharsets.UTF_8));
        return comma + 1 + length;
    }

    private static int indexOf(byte[] payload, byte wanted, int from) {
        for (int index = from; index < payload.length; index++) {
            if (payload[index] == wanted) {
                return index;
            }
        }
        return -1;
    }

    private static Optional<JsonNode> parsed(String json) {
        try {
            return Optional.of(JSON.readTree(json));
        } catch (JacksonException notJson) {
            return Optional.empty();
        }
    }
}
