package com.spotyhud.media;

import com.spotyhud.client.SpotyHudClient;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Polls the platform backend on a dedicated daemon thread (never the render thread) and publishes the latest
 * {@link MediaState}. Commands run on the same thread so the backend needs no locking.
 */
public final class MediaService {
    private static final long POLL_MS = 750;
    private static MediaService instance;

    private final MediaBackend backend;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "SpotyHUD-Media");
        t.setDaemon(true);
        return t;
    });

    private volatile MediaState state = MediaState.EMPTY;
    private byte[] lastArtBytes;
    private ArtData lastArt;
    private int failures;
    private long retryAtNanos;

    private MediaService(MediaBackend backend) { this.backend = backend; }

    public static synchronized void start() {
        if (instance != null) return;
        MediaBackend backend = MediaBackends.create();
        SpotyHudClient.LOGGER.info("SpotyHUD media backend: {}", backend.name());
        instance = new MediaService(backend);
        instance.executor.scheduleWithFixedDelay(instance::pollOnce, 0, POLL_MS, TimeUnit.MILLISECONDS);
    }

    public static synchronized void stop() {
        if (instance == null) return;
        instance.executor.shutdownNow();
        instance.backend.close();
        instance = null;
    }

    /** Never null; before {@link #start()} an inert instance is returned. */
    public static synchronized MediaService get() {
        if (instance == null) start();
        return instance;
    }

    public MediaState state() { return state; }

    public void send(MediaCommand command) {
        try {
            executor.execute(() -> {
                try {
                    backend.send(command);
                    MediaState s = state;
                    if (command == MediaCommand.PLAY_PAUSE && s.info().active()) {   // optimistic UI update
                        state = new MediaState(s.info().withPlaying(!s.info().playing(), System.nanoTime()), s.art());
                    }
                    Thread.sleep(command == MediaCommand.PLAY_PAUSE ? 200 : 350);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    SpotyHudClient.LOGGER.warn("SpotyHUD command {} failed: {}", command, e.toString());
                }
                pollOnce();
            });
        } catch (RuntimeException ignored) {
            // executor already shut down
        }
    }

    private void pollOnce() {
        if (System.nanoTime() < retryAtNanos) return;
        try {
            MediaInfo info = backend.poll();
            failures = 0;
            if (!info.active()) {
                state = MediaState.EMPTY;
                return;
            }
            if (info.artBytes() != lastArtBytes) {   // identity: backends reuse the array until the cover changes
                lastArtBytes = info.artBytes();
                lastArt = ArtData.decode(lastArtBytes);
            }
            state = new MediaState(info, lastArt);
        } catch (Throwable t) {
            if (failures++ == 0) SpotyHudClient.LOGGER.warn("SpotyHUD media poll failed ({}): {}", backend.name(), t.toString());
            state = MediaState.EMPTY;
            lastArtBytes = null;
            lastArt = null;
            long backoffSec = Math.min(30, 1L << Math.min(failures, 5));
            retryAtNanos = System.nanoTime() + backoffSec * 1_000_000_000L;
        }
    }
}
