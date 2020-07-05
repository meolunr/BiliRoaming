package me.iacn.biliroaming

import android.content.Context
import android.util.SparseArray
import android.view.View
import de.robv.android.xposed.XposedHelpers.ClassNotFoundError
import de.robv.android.xposed.XposedHelpers.findClass
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.lang.ref.WeakReference
import java.lang.reflect.ParameterizedType
import kotlin.reflect.KProperty

/**
 * Created by iAcn on 2019/4/5
 * Email i@iacn.me
 */
class BiliBiliPackage private constructor() {
    private lateinit var mClassLoader: ClassLoader
    private var mHookInfo: MutableMap<String, String> = mutableMapOf()

    val retrofitResponse get() = mHookInfo["class_retrofit_response"]
    val fastJsonParse get() = mHookInfo["method_fastjson_parse"]
    val colorArray get() = mHookInfo["field_color_array"]
    val themeListClickListener get() = mHookInfo["class_theme_list_click"]

    val bangumiApiResponse: Class<*> by Weak { findClass("com.bilibili.bangumi.data.common.api.BangumiApiResponse", mClassLoader) }
    val fastJson: Class<*> by Weak { findClass(mHookInfo["class_fastjson"], mClassLoader) }
    val bangumiUniformSeason: Class<*> by Weak(::searchBangumiUniformSeasonClass)
    val themeHelper: Class<*> by Weak { findClass("tv.danmaku.bili.ui.theme.a", mClassLoader) }

    var hasModulesInResult = false
        get() {
            log("hasModulesInResult: $field")
            return field
        }

    companion object {
        private const val HOOK_INFO_FILE_NAME = "hookinfo.dat"

        val instance: BiliBiliPackage by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            BiliBiliPackage()
        }
    }

    fun init(classLoader: ClassLoader, context: Context) {
        mClassLoader = classLoader

        readHookInfo(context)
        if (checkHookInfo()) {
            writeHookInfo(context)
        }
    }

    private fun readHookInfo(context: Context) {
        val hookInfoFile = File(context.cacheDir, HOOK_INFO_FILE_NAME)
        log("Reading hook info: $hookInfoFile")
        val startTime = System.currentTimeMillis()

        if (hookInfoFile.isFile && hookInfoFile.canRead()) {
            val lastUpdateTime = context.packageManager.getPackageInfo(BILIBILI_PACKAGENAME, 0).lastUpdateTime
            ObjectInputStream(FileInputStream(hookInfoFile)).use {
                if (it.readLong() == lastUpdateTime) {
                    mHookInfo = it.readObject() as MutableMap<String, String>
                }
            }
        }

        val endTime = System.currentTimeMillis()
        log("Read hook info completed: take ${endTime - startTime} ms")
    }

    /**
     * @return Whether to update the serialization file.
     */
    private fun checkHookInfo(): Boolean {
        var needUpdate = false

        if ("class_retrofit_response" !in mHookInfo) {
            mHookInfo["class_retrofit_response"] = searchRetrofitResponseClass()
            needUpdate = true
        }
        if ("class_fastjson" !in mHookInfo) {
            val fastJsonClass = searchFastJsonClass()
            val notObfuscated = "JSON" == fastJsonClass.simpleName
            mHookInfo["class_fastjson"] = fastJsonClass.name
            mHookInfo["method_fastjson_parse"] = if (notObfuscated) "parseObject" else "a"
            needUpdate = true
        }
        if ("field_color_array" !in mHookInfo) {
            mHookInfo["field_color_array"] = searchColorArrayField()
            needUpdate = true
        }
        if ("class_theme_list_click" !in mHookInfo) {
            mHookInfo["class_theme_list_click"] = searchThemeListClickClass()
            needUpdate = true
        }

        log("Check hook info completed: needUpdate = $needUpdate")
        return needUpdate
    }

    private fun writeHookInfo(context: Context) {
        val hookInfoFile = File(context.cacheDir, HOOK_INFO_FILE_NAME)
        val lastUpdateTime = context.packageManager.getPackageInfo(BILIBILI_PACKAGENAME, 0).lastUpdateTime

        if (hookInfoFile.exists()) hookInfoFile.delete()

        ObjectOutputStream(FileOutputStream(hookInfoFile)).use {
            it.writeLong(lastUpdateTime)
            it.writeObject(mHookInfo)
            it.flush()
        }
        log("Write hook info completed")
    }

    private fun searchBangumiUniformSeasonClass(): Class<*> {
        val clazz = findClass("com.bilibili.bangumi.data.page.detail.entity.BangumiUniformSeason", mClassLoader)
        try {
            clazz.getField("modules")
            hasModulesInResult = true
        } catch (ignored: NoSuchFieldException) {
        }
        return clazz
    }

    private fun searchRetrofitResponseClass(): String {
        for (method in bangumiApiResponse.methods) {
            if ("extractResult" == method.name) {
                val responseClass = method.parameterTypes[0]
                return responseClass.name
            }
        }
        return ""
    }

    private fun searchFastJsonClass(): Class<*> {
        return try {
            findClass("com.alibaba.fastjson.JSON", mClassLoader)
        } catch (e: ClassNotFoundError) {
            findClass("com.alibaba.fastjson.a", mClassLoader)
        }
    }

    private fun searchColorArrayField(): String {
        for (field in themeHelper.declaredFields) {
            if (field.type == SparseArray::class.java) {
                val genericType = field.genericType as ParameterizedType
                val types = genericType.actualTypeArguments
                if ("int[]" == types[0].toString()) {
                    return field.name
                }
            }
        }
        return ""
    }

    private fun searchThemeListClickClass(): String {
        val themeStoreActivityClass = findClass("tv.danmaku.bili.ui.theme.ThemeStoreActivity", mClassLoader)
        for (innerClass in themeStoreActivityClass.declaredClasses) {
            for (interfaceClass in innerClass.interfaces) {
                if (interfaceClass == View.OnClickListener::class.java) {
                    return innerClass.name
                }
            }
        }
        return ""
    }

    private class Weak(val initializer: () -> Class<*>?) {
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
}