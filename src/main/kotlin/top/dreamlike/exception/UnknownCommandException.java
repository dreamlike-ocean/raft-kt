package top.dreamlike.exception;

import io.vertx.core.impl.NoStackTraceThrowable;

public class UnknownCommandException extends NoStackTraceThrowable {
    public UnknownCommandException(String message) {
        super(message);
    }
}
