package me.iacn.biliroaming.hook

/**
 * Created by Meolunr on 2019/3/27
 * Email meolunr@gmail.com
 */
abstract class BaseHook(protected var mClassLoader: ClassLoader) {
    abstract fun startHook()
}