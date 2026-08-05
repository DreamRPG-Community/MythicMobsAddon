package cn.mythicland.mythicmobsaddon.service;

import java.io.Serial;

/**
 * Domain failure raised while reading or mutating MythicMobs item definitions.
 */
public final class MythicItemException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String code;

    public MythicItemException(String code, String message) {
        super(message);
        this.code = code;
    }

    public MythicItemException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
