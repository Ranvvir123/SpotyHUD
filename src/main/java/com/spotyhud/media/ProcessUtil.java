package com.spotyhud.media;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Tiny helper to run a short-lived command and capture stdout. */
final class ProcessUtil {
    private ProcessUtil() {}

    record Result(int exit, String out) {}

    static Result run(List<String> cmd, long timeoutSec) throws IOException {
        Process p = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        p.getOutputStream().close();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        try {
            if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IOException("timeout running " + cmd.get(0));
            }
        } catch (InterruptedException e) {
            p.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
        return new Result(p.exitValue(), out.strip());
    }
}
