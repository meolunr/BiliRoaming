package me.iacn.biliroaming.inject

import me.iacn.biliroaming.log

/**
 * Created by Meolunr on 2020/7/25
 * Email meolunr@gmail.com
 */
object ClassLoaderInjector {
    fun setupWithHost(hostParent: ClassLoader) {
        try {
            val parentField = ClassLoader::class.java.getDeclaredField("parent")
            parentField.isAccessible = true

            val childrenClassLoader = ClassLoaderInjector::class.java.classLoader
            val hybridParent = HybridClassLoader(hostParent)

            parentField.set(childrenClassLoader, hybridParent)
            log("ClassLoaderInjector: Inject self class loader is successful")
        } catch (e: Exception) {
            log("ClassLoaderInjector: Failed to inject self class loader! Use default class loader")
            e.printStackTrace()
        }
    }
}