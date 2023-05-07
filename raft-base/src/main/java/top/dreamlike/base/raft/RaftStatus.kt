package top.dreamlike.base.raft

enum class RaftStatus {
    follower,
    candidate,
    lead
}