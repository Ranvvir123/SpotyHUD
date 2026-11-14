package com.spotyhud.media;

import java.io.IOException;

/** OS specific way of reading and controlling the current media session. Used from ONE thread only. */
public interface MediaBackend extends AutoCloseable {
    String name();

    /** Returns {@link MediaInfo#NONE} when nothing is playing / the player is closed. */
    MediaInfo poll() throws IOException;

    void send(MediaCommand command) throws IOException;

    @Override
    void close();
}
