package top.dreamlike.raft.rpc.entity

import top.dreamlike.base.ServerId
import top.dreamlike.base.raft.RaftAddress

class AdderServerResponse(
    val ok: Boolean,
    val leader: RaftAddress,
    val leaderId: ServerId,
    val peer: Map<ServerId, RaftAddress>
) {
    override fun toString(): String {
        return "AdderServerResponse(ok=$ok, leader=$leader, leaderId='$leaderId', peer=$peer)"
    }
}