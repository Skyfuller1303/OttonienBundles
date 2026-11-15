package de.ottonien.bundles;

import java.util.logging.Logger;

/** Switchable diagnostic output for bundle interactions. Off by default, not persisted. */
public final class DebugLog {

    private final Logger logger;
    private volatile boolean enabled;

    public DebugLog(Logger logger) {
        this.logger = logger;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void log(String message) {
        if (enabled) {
            logger.info("[debug] " + message);
        }
    }
}
