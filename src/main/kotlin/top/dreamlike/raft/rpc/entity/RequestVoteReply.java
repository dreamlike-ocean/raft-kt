package top.dreamlike.raft.rpc.entity;

import io.netty.buffer.ByteBuf;
import io.vertx.core.buffer.Buffer;

public class RequestVoteReply {
    private final int term;
    private final boolean voteGranted;

    public RequestVoteReply(int term, boolean voteGranted) {
        this.term = term;
        this.voteGranted = voteGranted;
    }

    public RequestVoteReply(Buffer buffer) {
        ByteBuf buf = buffer.getByteBuf();
        term = buf.readInt();
        voteGranted = buf.readByte() == 1;
    }

    public Buffer toBuffer() {
        return Buffer.buffer()
                .appendInt(term)
                .appendByte(voteGranted ? (byte) 1 : 0);
    }

    public int getTerm() {
        return term;
    }

    public boolean isVoteGranted() {
        return voteGranted;
    }
}
