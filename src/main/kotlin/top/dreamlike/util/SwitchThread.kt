package top.dreamlike.util

import kotlin.reflect.KClass

/**
 * 标识一个方法内部并不在当前线程中执行
 * 只有个**标识**作用
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class SwitchThread(val thread: KClass<*>)
