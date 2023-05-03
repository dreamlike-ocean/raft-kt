package top.dreamlike.util;


import io.vertx.core.impl.NoStackTraceThrowable;
import io.vertx.core.net.SocketAddress;

public class NotLeaderException extends NoStackTraceThrowable {

    public final SocketAddress leaderInfo;
    public NotLeaderException(String message, SocketAddress leaderInfo) {
        super(message);
        this.leaderInfo = leaderInfo;
    }
}
