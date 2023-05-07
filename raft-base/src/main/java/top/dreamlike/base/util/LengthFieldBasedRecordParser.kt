package top.dreamlike.base.util

import io.vertx.core.Handler
import io.vertx.core.buffer.Buffer
import io.vertx.core.parsetools.RecordParser


class LengthFieldBasedRecordParser(private val fn :(Buffer) -> Unit) : Handler<Buffer> {
    private val internalParser = parser(fn)
    override fun handle(event: Buffer) {
        internalParser.handle(event)
    }

    private fun parser(fn : (Buffer) -> Unit) = RecordParser.newFixed(4).apply { switchToWaitLength(fn) }
    private fun RecordParser.switchToWaitLength(fn : (Buffer) -> Unit){
        fixedSizeMode(4)
        handler {
            val bodyHandler = it.getInt(0)
            switchToBodyParse(bodyHandler, fn)
        }
    }
    private fun RecordParser.switchToBodyParse(length: Int, fn : (Buffer) -> Unit) {
        fixedSizeMode(length)
        handler {
            fn(it)
            switchToWaitLength(fn)
        }
    }

}