package org.kansei.tailwind.photo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Wikimedia Commons through the public MediaWiki API, no key needed. Commons asks clients to send a
 * descriptive User-Agent, it comes from configuration.
 */
@Slf4j
@Component
public class WikimediaCommonsClient implements CommonsClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Set<String> ACCEPTED_MIME = Set.of("image/jpeg", "image/png");
    private static final String IMAGE_INFO_PROPS = "url|extmetadata|size|mime";
    private static final String METADATA_FILTER = "Artist|LicenseShortName|ObjectName";
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]*>");
    private static final int MAX_TEXT_LENGTH = 300;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final int thumbWidth;

    public WikimediaCommonsClient(
            @Value("${commons.api-url}") String apiUrl,
            @Value("${commons.user-agent}") String userAgent,
            @Value("${commons.thumb-width}") int thumbWidth,
            ObjectMapper objectMapper
    ) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
        requestFactory.setReadTimeout(TIMEOUT);
        this.restClient = RestClient.builder().baseUrl(apiUrl).defaultHeader("User-Agent", userAgent).requestFactory(requestFactory).build();
        this.objectMapper = objectMapper;
        this.thumbWidth = thumbWidth;
    }

    @Override
    public List<CommonsCandidate> search(String query, int limit) {
        String body = call(uri -> uri.queryParam("action", "query").queryParam("generator", "search")
                .queryParam("gsrsearch", query + " filetype:bitmap").queryParam("gsrnamespace", 6).queryParam("gsrlimit", limit));
        return parse(body);
    }

    @Override
    public Optional<CommonsCandidate> findByTitle(String title) {
        String body = call(uri -> uri.queryParam("action", "query").queryParam("titles", title));
        return parse(body).stream().findFirst();
    }

    private String call(java.util.function.Consumer<org.springframework.web.util.UriBuilder> params) {
        try {
            return restClient.get().uri(uriBuilder -> {
                uriBuilder.queryParam("prop", "imageinfo").queryParam("iiprop", IMAGE_INFO_PROPS)
                        .queryParam("iiextmetadatafilter", METADATA_FILTER).queryParam("iiurlwidth", thumbWidth)
                        .queryParam("format", "json").queryParam("formatversion", 2);
                params.accept(uriBuilder);
                return uriBuilder.build();
            }).retrieve().body(String.class);
        } catch (RestClientException ex) {
            throw new CommonsUnavailableException("Wikimedia Commons call failed: " + ex.getMessage(), ex);
        }
    }

    public List<CommonsCandidate> parse(String body) {
        try {
            Response response = objectMapper.readValue(body, Response.class);
            if (response.query() == null || response.query().pages() == null) {
                return List.of();
            }
            return response.query().pages().stream()
                    .filter(p -> p.title() != null && p.imageinfo() != null && !p.imageinfo().isEmpty())
                    .sorted(Comparator.comparingInt(p -> p.index() == null ? Integer.MAX_VALUE : p.index()))
                    .map(this::toCandidate)
                    .flatMap(Optional::stream)
                    .toList();
        } catch (RuntimeException ex) {
            throw new CommonsUnavailableException("Wikimedia Commons returned a body we could not read", ex);
        }
    }

    private Optional<CommonsCandidate> toCandidate(Page page) {
        ImageInfo info = page.imageinfo().get(0);
        if (info.thumburl() == null || info.mime() == null || !ACCEPTED_MIME.contains(info.mime())) {
            return Optional.empty();
        }
        Map<String, Meta> meta = info.extmetadata() == null ? Map.of() : info.extmetadata();
        return Optional.of(new CommonsCandidate(page.title(), info.thumburl(), info.descriptionurl(),
                plainText(meta.get("Artist")), plainText(meta.get("LicenseShortName")),
                info.width() == null ? 0 : info.width(), info.height() == null ? 0 : info.height(), info.mime()));
    }

    // Commons sends author and license as HTML, we keep plain text only so nothing downstream ever renders markup
    static String plainText(Meta meta) {
        if (meta == null || meta.value() == null) {
            return null;
        }
        String text = HTML_TAG.matcher(meta.value()).replaceAll(" ")
                .replace("&amp;", "&").replace("&quot;", "\"").replace("&#039;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ");
        // Tags became spaces, so tidy the gaps that leaves around brackets and punctuation
        text = text.replaceAll("\\s+", " ").replace("( ", "(").replace(" )", ")").replaceAll("\\s+([,.;:])", "$1").trim();
        if (text.isEmpty()) {
            return null;
        }
        return text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) : text;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(Query query) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Query(List<Page> pages) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Page(String title, Integer index, List<ImageInfo> imageinfo) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ImageInfo(String thumburl, String descriptionurl, Integer width, Integer height, String mime, Map<String, Meta> extmetadata) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Meta(String value) {
    }
}
