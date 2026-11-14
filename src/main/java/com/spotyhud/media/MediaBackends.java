package com.spotyhud.media;

import java.util.Locale;

public final class MediaBackends {
    private MediaBackends() {}

    public static MediaBackend create() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return new WindowsMediaBackend();
        if (os.contains("mac") || os.contains("darwin")) return new MacMediaBackend();
        if (os.contains("nux") || os.contains("nix") || os.contains("bsd")) return new LinuxMediaBackend();
        return new NoopMediaBackend();
    }
}
