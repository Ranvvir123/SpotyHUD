package com.spotyhud.media;

/** What the HUD renders: raw info plus the decoded cover art. Immutable. */
public record MediaState(MediaInfo info, ArtData art) {
    public static final MediaState EMPTY = new MediaState(MediaInfo.NONE, null);
    private static final MediaState DEMO = new MediaState(
            new MediaInfo(true, "Song Title", "Artist Name", "Album", true, 19, 112, null, 0L), null);

    /** Placeholder shown by the HUD editor when nothing is playing. */
    public static MediaState demo() { return DEMO; }
}
