package top.dreamlike.base.raft

import top.dreamlike.base.ServerId

data class RaftSnap(
    val nextIndex: Map<ServerId, Int>,
    val matchIndex: Map<ServerId, Int>,
    val peers: Map<ServerId, RaftAddress>,
    val raftState: RaftState
)