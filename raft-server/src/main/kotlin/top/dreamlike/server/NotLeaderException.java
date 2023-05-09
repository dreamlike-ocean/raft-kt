package top.dreamlike.server;


import io.vertx.core.impl.NoStackTraceThrowable;
import top.dreamlike.base.raft.RaftAddress;

public class NotLeaderException extends NoStackTraceThrowable {

    public final RaftAddress leaderInfo;

    public NotLeaderException(String message, RaftAddress leaderInfo) {
        super(message);
        this.leaderInfo = leaderInfo;
    }
}
