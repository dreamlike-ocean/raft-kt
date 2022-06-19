package top.dreamlike.raft.rpc.entity;

import io.vertx.core.buffer.Buffer;

public class AppendReply {
    private final int term;
    private final boolean success;

    public AppendReply(int term, boolean success) {
        this.term = term;
        this.success = success;
    }

    public AppendReply(Buffer body) {
        this(body.getInt(0), body.getByte(4) == 1);
    }

    public Buffer toBuffer() {
        return Buffer.buffer()
                .appendInt(term)
                .appendByte(success ? (byte) 1 : (byte) 0);
    }

    public int getTerm() {
        return term;
    }

    public boolean isSuccess() {
        return success;
    }
}
