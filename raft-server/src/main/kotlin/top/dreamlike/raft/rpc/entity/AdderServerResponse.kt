package top.dreamlike.raft.rpc.entity

import top.dreamlike.base.raft.RaftAddress

class AdderServerResponse(val ok: Boolean, val leader: RaftAddress? = null) {
}