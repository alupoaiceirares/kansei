package org.kansei.tailwind.photo;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Maps a real Wikimedia Commons search response (A340 photos).
 */
class WikimediaCommonsClientTest {

    private final WikimediaCommonsClient client = new WikimediaCommonsClient("http://localhost", "test", 1280, JsonMapper.builder().build());

    private static String fixture() throws IOException {
        return new String(new ClassPathResource("commons-search-a340.json").getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    @Test
    void mapsCandidatesInSearchOrderWithPlainTextAttribution() throws IOException {
        List<CommonsCandidate> candidates = client.parse(fixture());

        assertThat(candidates).hasSize(4);
        CommonsCandidate first = candidates.get(0);
        assertThat(first.title()).isEqualTo("File:South African Airways Airbus A340-313 ZS-SXE MUC 2015 02.jpg");
        assertThat(first.thumbUrl()).startsWith("https://").contains("1280px");
        assertThat(first.pageUrl()).startsWith("https://commons.wikimedia.org/wiki/File:");
        assertThat(first.license()).isEqualTo("CC BY 4.0");
        assertThat(first.mime()).isEqualTo("image/jpeg");
        assertThat(first.width()).isEqualTo(3400);
        // the author arrives as an HTML link, only the text survives
        assertThat(first.author()).isEqualTo("Julian Herzog (Website)");
        assertThat(candidates).allSatisfy(c -> assertThat(c.author()).doesNotContain("<"));
    }

    @Test
    void stripsMarkupEntitiesAndOverlongText() {
        assertThat(WikimediaCommonsClient.plainText(new WikimediaCommonsClient.Meta("<a href=\"x\">Bob</a> &amp; co")))
                .isEqualTo("Bob & co");
        assertThat(WikimediaCommonsClient.plainText(new WikimediaCommonsClient.Meta("<script>alert(1)</script>")))
                .isEqualTo("alert(1)");
        assertThat(WikimediaCommonsClient.plainText(new WikimediaCommonsClient.Meta("<br>"))).isNull();
        assertThat(WikimediaCommonsClient.plainText(null)).isNull();
        assertThat(WikimediaCommonsClient.plainText(new WikimediaCommonsClient.Meta("x".repeat(500)))).hasSize(300);
    }

    @Test
    void emptyResultsAndUnreadableBodies() {
        assertThat(client.parse("{\"batchcomplete\":true}")).isEmpty();
        assertThatThrownBy(() -> client.parse("<html>")).isInstanceOf(CommonsUnavailableException.class);
    }

    @Test
    void skipsFilesThatAreNotJpegOrPng() throws IOException {
        String body = fixture().replace("\"image/jpeg\"", "\"image/svg+xml\"");

        assertThat(client.parse(body)).isEmpty();
    }
}
