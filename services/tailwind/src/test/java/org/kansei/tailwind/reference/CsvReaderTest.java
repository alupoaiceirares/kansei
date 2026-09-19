package org.kansei.tailwind.reference;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CsvReaderTest {

    @Test
    void parsesHeaderQuotedFieldsCommasAndEmptyValues() {
        List<Map<String, String>> rows = CsvReader.parse("a,b,c\n1,\"x, y\",\n2,\"say \"\"hi\"\"\",z\n");

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("a", "1").containsEntry("b", "x, y").containsEntry("c", "");
        assertThat(rows.get(1)).containsEntry("b", "say \"hi\"").containsEntry("c", "z");
    }

    @Test
    void handlesWindowsLineEndingsAndMissingTrailingNewline() {
        List<Map<String, String>> rows = CsvReader.parse("a,b\r\n1,2\r\n3,4");

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("b", "2");
        assertThat(rows.get(1)).containsEntry("b", "4");
    }

    @Test
    void emptyInputGivesNoRows() {
        assertThat(CsvReader.parse("")).isEmpty();
        assertThat(CsvReader.parse("a,b\n")).isEmpty();
    }
}
