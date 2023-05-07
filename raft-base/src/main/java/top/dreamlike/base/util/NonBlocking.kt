package top.dreamlike.base.util

/**
 * 标识一个方法不会阻塞当前线程
 * 只有个**标识**作用
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class NonBlocking()
