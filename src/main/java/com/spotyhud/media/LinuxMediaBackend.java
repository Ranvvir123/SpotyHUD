package com.spotyhud.media;

import java.io.IOException;
import java.util.List;

/** MPRIS via the {@code playerctl} CLI. Prefers Spotify, falls back to whichever player is active. */
public final class LinuxMediaBackend implements MediaBackend {
    private static final String SEP = "\u001f";
    private static final String FORMAT = String.join(SEP, "{{status}}", "{{title}}", "{{artist}}", "{{album}}",
            "{{mpris:artUrl}}", "{{position}}", "{{mpris:length}}");

    private final ArtFetcher art = new ArtFetcher();

    @Override public String name() { return "playerctl (MPRIS)"; }

    @Override
    public MediaInfo poll() throws IOException {
        ProcessUtil.Result r = ProcessUtil.run(List.of("playerctl", "-p", "spotify", "metadata", "--format", FORMAT), 3);
        if (r.exit() != 0) r = ProcessUtil.run(List.of("playerctl", "metadata", "--format", FORMAT), 3);
        if (r.exit() != 0 || r.out().isBlank()) return MediaInfo.NONE;
        String[] f = r.out().split(SEP, -1);
        if (f.length < 7 || f[1].isBlank()) return MediaInfo.NONE;
        String status = f[0].toLowerCase();
        if (status.equals("stopped")) return MediaInfo.NONE;
        return new MediaInfo(true, f[1], f[2], f[3], status.equals("playing"),
                micros(f[5]) / 1.0e6, micros(f[6]) / 1.0e6, art.get(f[4]), System.nanoTime());
    }

    private static long micros(String s) {
        try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return 0; }
    }

    @Override
    public void send(MediaCommand command) throws IOException {
        String c = switch (command) {
            case PLAY_PAUSE -> "play-pause";
            case NEXT -> "next";
            case PREVIOUS -> "previous";
        };
        if (ProcessUtil.run(List.of("playerctl", "-p", "spotify", c), 3).exit() != 0) {
            ProcessUtil.run(List.of("playerctl", c), 3);
        }
    }

    @Override public void close() { }
}
