package com.spotyhud.media;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Talks to Windows' System Media Transport Controls (the same thing the volume flyout uses) through one
 * long-lived PowerShell process. Java sends "POLL"/"NEXT"/... lines, the script answers with JSON lines.
 */
public final class WindowsMediaBackend implements MediaBackend {
    private static final String EOF = "\u0000EOF";

    private Process process;
    private BufferedWriter stdin;
    private LinkedBlockingQueue<String> lines;
    private Path script;
    private boolean firstPoll = true;

    private String lastKey;
    private byte[] lastArt;

    @Override public String name() { return "Windows SMTC (PowerShell)"; }

    private synchronized void ensureStarted() throws IOException {
        if (process != null && process.isAlive()) return;
        killProcess();
        script = Files.createTempFile("spotyhud-media", ".ps1");
        script.toFile().deleteOnExit();
        try (InputStream in = WindowsMediaBackend.class.getResourceAsStream("/spotyhud/media.ps1")) {
            if (in == null) throw new IOException("media.ps1 resource missing");
            Files.write(script, in.readAllBytes());
        }
        ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-File", script.toString());
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        process = pb.start();
        stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        LinkedBlockingQueue<String> q = new LinkedBlockingQueue<>();
        lines = q;
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        Thread t = new Thread(() -> {
            try {
                String l;
                while ((l = reader.readLine()) != null) q.offer(l);
            } catch (IOException ignored) {
            }
            q.offer(EOF);
        }, "SpotyHUD-PowerShell-Reader");
        t.setDaemon(true);
        t.start();
        firstPoll = true;
    }

    private void write(String cmd) throws IOException {
        try {
            stdin.write(cmd);
            stdin.write('\n');
            stdin.flush();
        } catch (IOException e) {
            killProcess();
            throw e;
        }
    }

    @Override
    public synchronized MediaInfo poll() throws IOException {
        ensureStarted();
        lines.clear();
        write("POLL");
        String line;
        try {
            line = lines.poll(firstPoll ? 20 : 6, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
        if (line == null || line.equals(EOF)) {
            killProcess();
            throw new IOException("PowerShell bridge did not answer");
        }
        firstPoll = false;
        try {
            JsonObject o = JsonParser.parseString(line).getAsJsonObject();
            if (!o.has("active") || !o.get("active").getAsBoolean()) {
                lastKey = null;
                lastArt = null;
                return MediaInfo.NONE;
            }
            String title = str(o, "title"), artist = str(o, "artist"), album = str(o, "album");
            String key = title + '\u0000' + artist + '\u0000' + album;
            if (!key.equals(lastKey)) {
                lastKey = key;
                lastArt = null;
            }
            if (o.has("thumb") && !o.get("thumb").isJsonNull()) {
                lastArt = Base64.getDecoder().decode(o.get("thumb").getAsString());
            }
            boolean playing = o.has("playing") && o.get("playing").getAsBoolean();
            double pos = o.has("pos") ? o.get("pos").getAsDouble() : 0;
            double dur = o.has("dur") ? o.get("dur").getAsDouble() : 0;
            return new MediaInfo(true, title, artist, album, playing, pos, dur, lastArt, System.nanoTime());
        } catch (JsonParseException | IllegalStateException | IllegalArgumentException e) {
            throw new IOException("Bad bridge output", e);
        }
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    @Override
    public synchronized void send(MediaCommand command) throws IOException {
        ensureStarted();
        write(command.name());
    }

    private synchronized void killProcess() {
        if (process != null) {
            try { stdin.write("EXIT\n"); stdin.flush(); } catch (Exception ignored) { }
            process.destroy();
            process = null;
        }
    }

    @Override
    public synchronized void close() {
        killProcess();
        if (script != null) {
            try { Files.deleteIfExists(script); } catch (IOException ignored) { }
        }
    }
}
