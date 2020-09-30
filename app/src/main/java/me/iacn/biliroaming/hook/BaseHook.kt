package me.iacn.biliroaming.hook

/**
 * Created by Meolunr on 2019/3/27
 * Email meolunr@gmail.com
 */
abstract class BaseHook {

    abstract fun isEnable(): Boolean

    abstract fun startHook(classLoader: ClassLoader)
}