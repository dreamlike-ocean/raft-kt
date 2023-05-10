package top.dreamlike.base

import java.util.UUID


typealias ServerId = String

const val SUCCESS = 200
const val FAIL = 500
const val NOT_LEAD = 520
const val COMMAND_PATH = "/command"
const val PEEK_PATH = "/peek"
const val ADD_SERVER_PATH = "/addServer"

fun RandomServerId(): ServerId = UUID.randomUUID().toString()