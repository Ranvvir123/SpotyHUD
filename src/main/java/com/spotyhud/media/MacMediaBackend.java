package com.spotyhud.media;

import java.io.IOException;
import java.util.List;

/** AppleScript against the Spotify desktop app. Never launches Spotify (checks "is running" first). */
public final class MacMediaBackend implements MediaBackend {
    private static final String SEP = "\u001f";
    private static final String POLL_SCRIPT = String.join("\n",
            "if application \"Spotify\" is running then",
            "  tell application \"Spotify\"",
            "    if player state is stopped then return \"stopped\"",
            "    set sep to (ASCII character 31)",
            "    set t to current track",
            "    set art to \"\"",
            "    try",
            "      set art to artwork url of t",
            "    end try",
            "    return (player state as text) & sep & (name of t) & sep & (artist of t) & sep & (album of t) & sep & art & sep & (player position as text) & sep & ((duration of t) as text)",
            "  end tell",
            "else",
            "  return \"closed\"",
            "end if");

    private final ArtFetcher art = new ArtFetcher();

    @Override public String name() { return "AppleScript (Spotify)"; }

    @Override
    public MediaInfo poll() throws IOException {
        ProcessUtil.Result r = ProcessUtil.run(List.of("osascript", "-e", POLL_SCRIPT), 4);
        if (r.exit() != 0) return MediaInfo.NONE;
        String[] f = r.out().split(SEP, -1);
        if (f.length < 7) return MediaInfo.NONE;
        return new MediaInfo(true, f[1], f[2], f[3], f[0].trim().equalsIgnoreCase("playing"),
                num(f[5]), num(f[6]) / 1000.0, art.get(f[4]), System.nanoTime());
    }

    private static double num(String s) {
        try { return Double.parseDouble(s.trim().replace(',', '.')); } catch (NumberFormatException e) { return 0; }
    }

    @Override
    public void send(MediaCommand command) throws IOException {
        String verb = switch (command) {
            case PLAY_PAUSE -> "playpause";
            case NEXT -> "next track";
            case PREVIOUS -> "previous track";
        };
        ProcessUtil.run(List.of("osascript", "-e",
                "if application \"Spotify\" is running then tell application \"Spotify\" to " + verb), 4);
    }

    @Override public void close() { }
}
