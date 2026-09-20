package org.kansei.tailwind.photo;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageDownloaderTest {

    private HttpServer server;
    private ImageDownloader downloader;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/small", exchange -> respond(exchange, 200, new byte[100]));
        server.createContext("/big", exchange -> respond(exchange, 200, new byte[5000]));
        server.createContext("/missing", exchange -> respond(exchange, 404, new byte[0]));
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "/small");
            respond(exchange, 302, new byte[0]);
        });
        server.start();
        downloader = new ImageDownloader(Set.of("127.0.0.1"), false, "test");
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    @Test
    void downloadsAFileWithinTheSizeCap() {
        assertThat(downloader.download(url("/small"), 1000)).hasSize(100);
    }

    @Test
    void refusesAFileOverTheCap() {
        assertThatThrownBy(() -> downloader.download(url("/big"), 1000))
                .isInstanceOf(ImageDownloader.ImageDownloadException.class)
                .hasMessageContaining("larger than");
    }

    @Test
    void refusesAnErrorResponseAndDoesNotFollowRedirects() {
        assertThatThrownBy(() -> downloader.download(url("/missing"), 1000)).isInstanceOf(ImageDownloader.ImageDownloadException.class);
        assertThatThrownBy(() -> downloader.download(url("/redirect"), 1000)).isInstanceOf(ImageDownloader.ImageDownloadException.class);
    }

    @Test
    void onlyAllowedHostsAndHttpsAreFetched() {
        ImageDownloader strict = new ImageDownloader(Set.of("upload.wikimedia.org"), true, "test");

        assertThatThrownBy(() -> strict.download("https://evil.example.com/x.jpg", 1000))
                .isInstanceOf(ImageDownloader.ImageDownloadException.class).hasMessageContaining("host not allowed");
        assertThatThrownBy(() -> strict.download("http://upload.wikimedia.org/x.jpg", 1000))
                .isInstanceOf(ImageDownloader.ImageDownloadException.class).hasMessageContaining("host not allowed");
        assertThatThrownBy(() -> strict.download("file:///etc/passwd", 1000))
                .isInstanceOf(ImageDownloader.ImageDownloadException.class);
        assertThatThrownBy(() -> strict.download("http://127.0.0.1:1/internal", 1000))
                .isInstanceOf(ImageDownloader.ImageDownloadException.class).hasMessageContaining("host not allowed");
    }
}
