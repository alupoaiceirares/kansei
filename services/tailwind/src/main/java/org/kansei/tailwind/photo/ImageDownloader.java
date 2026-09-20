package org.kansei.tailwind.photo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Downloads a picked Commons image. Only https URLs on the configured image hosts are fetched, redirects
 * are not followed, and the body is cut off at the size cap, so a bad URL cannot make the server fetch
 * arbitrary places or buffer arbitrary amounts.
 */
@Slf4j
@Component
public class ImageDownloader {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final Set<String> allowedHosts;
    private final boolean requireHttps;
    private final String userAgent;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NEVER).build();

    @Autowired
    public ImageDownloader(@Value("${commons.image-hosts}") String imageHosts, @Value("${commons.user-agent}") String userAgent) {
        this(Arrays.stream(imageHosts.split(",")).map(h -> h.trim().toLowerCase(Locale.ROOT)).collect(Collectors.toSet()), true, userAgent);
    }

    ImageDownloader(Set<String> allowedHosts, boolean requireHttps, String userAgent) {
        this.allowedHosts = allowedHosts;
        this.requireHttps = requireHttps;
        this.userAgent = userAgent;
    }

    public byte[] download(String url, int maxBytes) {
        URI uri = validate(url);
        try {
            HttpResponse<InputStream> response = httpClient.send(
                    HttpRequest.newBuilder(uri).timeout(TIMEOUT).header("User-Agent", userAgent).GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() != 200) {
                    throw new ImageDownloadException("the image host answered " + response.statusCode());
                }
                if (response.headers().firstValueAsLong("Content-Length").orElse(0) > maxBytes) {
                    throw new ImageDownloadException("the image is larger than " + maxBytes + " bytes");
                }
                byte[] data = body.readNBytes(maxBytes + 1);
                if (data.length > maxBytes) {
                    throw new ImageDownloadException("the image is larger than " + maxBytes + " bytes");
                }
                return data;
            }
        } catch (IOException ex) {
            throw new ImageDownloadException("the image could not be downloaded: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ImageDownloadException("the download was interrupted");
        }
    }

    private URI validate(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException ex) {
            throw new ImageDownloadException("not a valid image URL");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if ((requireHttps && !"https".equalsIgnoreCase(uri.getScheme())) || !allowedHosts.contains(host)) {
            throw new ImageDownloadException("image host not allowed");
        }
        return uri;
    }

    public static class ImageDownloadException extends RuntimeException {
        public ImageDownloadException(String message) {
            super(message);
        }
    }
}
