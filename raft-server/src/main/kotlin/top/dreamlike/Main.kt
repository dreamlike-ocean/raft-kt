package top.dreamlike

import io.vertx.core.json.JsonObject
import top.dreamlike.base.util.block
import top.dreamlike.base.util.initJacksonMapper
import top.dreamlike.configurarion.Configuration
import top.dreamlike.server.RaftServer
import java.io.File

fun main(args: Array<String>) {
    initJacksonMapper()
    val configFile = File("config.json")
    val configuration =
        if (!configFile.exists()) {
            Configuration()
        } else {
            JsonObject(configFile.readText())
                .mapTo(Configuration::class.java)
        }
    RaftServer(configuration).start().block()
}

