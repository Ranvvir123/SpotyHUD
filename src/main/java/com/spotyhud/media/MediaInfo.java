package com.spotyhud.media;

/**
 * Raw snapshot produced by a {@link MediaBackend}.
 *
 * @param artBytes     encoded cover image (JPEG/PNG) or null if unknown; the same array instance is reused
 *                     between polls while the cover is unchanged
 * @param polledAtNanos {@link System#nanoTime()} at which positionSec was valid
 */
public record MediaInfo(boolean active, String title, String artist, String album, boolean playing,
                        double positionSec, double durationSec, byte[] artBytes, long polledAtNanos) {

    public static final MediaInfo NONE = new MediaInfo(false, "", "", "", false, 0, 0, null, 0L);

    public String trackKey() {
        return title + '\u0000' + artist + '\u0000' + album;
    }

    /** Position extrapolated to {@code nowNanos}, clamped to the track length. */
    public double positionAt(long nowNanos) {
        double p = positionSec;
        if (playing) p += Math.max(0, nowNanos - polledAtNanos) / 1.0e9;
        if (durationSec > 0) p = Math.min(p, durationSec);
        return Math.max(0, p);
    }

    public MediaInfo withPlaying(boolean nowPlaying, long nowNanos) {
        return new MediaInfo(active, title, artist, album, nowPlaying, positionAt(nowNanos), durationSec, artBytes, nowNanos);
    }
}
