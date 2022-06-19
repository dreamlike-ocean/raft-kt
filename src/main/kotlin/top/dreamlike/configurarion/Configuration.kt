package top.dreamlike.configurarion

import io.vertx.core.VertxOptions

class Configuration {

    fun vertxConfig(): VertxOptions {
        return VertxOptions()
            .setEventLoopPoolSize(1)
            .setWorkerPoolSize(1)
            .setInternalBlockingPoolSize(1)
    }
}