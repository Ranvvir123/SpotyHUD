package com.spotyhud.media;

/** Used on unsupported platforms: never crashes, never shows anything. */
public final class NoopMediaBackend implements MediaBackend {
    @Override public String name() { return "none"; }
    @Override public MediaInfo poll() { return MediaInfo.NONE; }
    @Override public void send(MediaCommand command) { }
    @Override public void close() { }
}
