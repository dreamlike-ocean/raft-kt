package top.dreamlike.raft.rpc.entity

import top.dreamlike.base.ServerId
import top.dreamlike.base.raft.RaftAddress

class AdderServerResponse(
    val ok: Boolean,
    val leader: RaftAddress? = null,
    val leaderId: ServerId,
    val peer: Map<ServerId, RaftAddress>
) {
}