package com.spotyhud.media;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/** Loads cover art from a file:// or http(s):// URL and caches the last result (Linux/macOS backends). */
final class ArtFetcher {
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private String url = "";
    private byte[] bytes;

    byte[] get(String artUrl) {
        if (artUrl == null || artUrl.isBlank()) { url = ""; bytes = null; return null; }
        if (artUrl.equals(url)) return bytes;   // also caches failures so we don't hammer the network
        url = artUrl;
        bytes = null;
        try {
            if (artUrl.startsWith("file:")) {
                bytes = Files.readAllBytes(Path.of(URI.create(artUrl)));
            } else if (artUrl.startsWith("http")) {
                HttpResponse<byte[]> r = http.send(HttpRequest.newBuilder(URI.create(artUrl))
                        .timeout(Duration.ofSeconds(8)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
                if (r.statusCode() == 200) bytes = r.body();
            }
        } catch (Exception ignored) {
            bytes = null;
        }
        return bytes;
    }
}
