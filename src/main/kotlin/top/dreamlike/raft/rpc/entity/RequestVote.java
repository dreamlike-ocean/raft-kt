package top.dreamlike.raft.rpc.entity;

import io.netty.buffer.ByteBuf;
import io.vertx.core.buffer.Buffer;

import java.util.StringJoiner;

public class RequestVote {
    private final int term;
    private String candidateId;
    private final int lastLogIndex;
    private final int lastLogTerm;


    public RequestVote(String candidateId, Buffer buffer) {
        ByteBuf buf = buffer.getByteBuf();
        this.candidateId = candidateId;
        term = buf.readInt();
        lastLogIndex = buf.readInt();
        lastLogTerm = buf.readInt();
    }

    public RequestVote(int term, int lastLogIndex, int lastLogTerm) {
        this.term = term;
        this.lastLogIndex = lastLogIndex;
        this.lastLogTerm = lastLogTerm;
    }

    public Buffer toBuffer() {
        return Buffer.buffer()
                .appendInt(term)
                .appendInt(lastLogIndex)
                .appendInt(lastLogTerm);
    }

    public int getTerm() {
        return term;
    }


    public int getLastLogIndex() {
        return lastLogIndex;
    }

    public int getLastLogTerm() {
        return lastLogTerm;
    }

    public String getCandidateId() {
        return candidateId;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", RequestVote.class.getSimpleName() + "[", "]")
                .add("term=" + term)
                .add("candidateId='" + candidateId + "'")
                .add("lastLogIndex=" + lastLogIndex)
                .add("lastLogTerm=" + lastLogTerm)
                .toString();
    }
}
