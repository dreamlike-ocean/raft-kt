package top.dreamlike.base.raft

import top.dreamlike.base.RandomServerId
import top.dreamlike.base.ServerId

data class RaftServerInfo(var raftAddress: RaftAddress, val serverId: ServerId = RandomServerId()) {
    override fun toString(): String {
        return "RaftServerInfo(raftAddress=$raftAddress, serverId='$serverId')"
    }
}
