package org.kansei.tailwind.reference;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal RFC 4180 reader for the bundled reference data files, first line is the header.
 */
public final class CsvReader {

    private CsvReader() {
    }

    public static List<Map<String, String>> read(InputStream in) throws IOException {
        return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    }

    public static List<Map<String, String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\n') {
                row.add(stripTrailingCr(field));
                rows.add(row);
                row = new ArrayList<>();
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(stripTrailingCr(field));
            rows.add(row);
        }

        List<Map<String, String>> result = new ArrayList<>();
        if (rows.isEmpty()) {
            return result;
        }
        List<String> header = rows.get(0);
        for (int r = 1; r < rows.size(); r++) {
            List<String> values = rows.get(r);
            Map<String, String> record = new LinkedHashMap<>();
            for (int c = 0; c < header.size(); c++) {
                record.put(header.get(c), c < values.size() ? values.get(c) : "");
            }
            result.add(record);
        }
        return result;
    }

    private static String stripTrailingCr(StringBuilder field) {
        int end = field.length();
        if (end > 0 && field.charAt(end - 1) == '\r') {
            end--;
        }
        return field.substring(0, end);
    }
}
