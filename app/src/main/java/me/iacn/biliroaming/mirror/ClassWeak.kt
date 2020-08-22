package me.iacn.biliroaming.mirror

import java.lang.ref.WeakReference
import kotlin.reflect.KProperty

/**
 * Created by Meolunr on 2020/7/3
 * Email meolunr@gmail.com
 */
class ClassWeak(val initializer: () -> Class<*>?) {
    private var weakReference: WeakReference<Class<*>?>? = null

    operator fun getValue(thisRef: Any?, property: KProperty<*>): Class<*> {
        weakReference?.get() ?: let {
            weakReference = WeakReference(initializer())
        }
        return weakReference!!.get()!!
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Class<*>) {
        weakReference = WeakReference(value)
    }
}