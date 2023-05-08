package top.dreamlike.base

import java.util.UUID


typealias ServerId = String

const val SUCCESS = 200
const val FAIL = 500
const val COMMAND_PATH = "/command"
const val PEEK_PATH = "/peek"

fun RandomServerId(): ServerId = UUID.randomUUID().toString()