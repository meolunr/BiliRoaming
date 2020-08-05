package me.iacn.biliroaming

import android.content.Context
import android.util.SparseArray
import android.view.View
import com.bilibili.bangumi.data.common.api.BangumiApiResponse
import com.bilibili.bangumi.data.page.detail.entity.BangumiUniformSeason
import de.robv.android.xposed.XposedHelpers.ClassNotFoundError
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.XposedHelpers.getStaticObjectField
import me.iacn.biliroaming.inject.ClassLoaderInjector
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.lang.ref.WeakReference
import java.lang.reflect.Modifier
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
    val resolveRequestParams get() = mHookInfo["method_resolve_request_params"]

    val fastJson: Class<*> by ClassWeak { findClass(mHookInfo["class_fastjson"], mClassLoader) }
    val themeHelper: Class<*> by ClassWeak { findClass(mHookInfo["class_theme_helper"], mClassLoader) }

    private val accessKeyInstance by lazy {
        getStaticObjectField(findClass("com.bilibili.bangumi.ui.page.detail.pay.BangumiPayHelperV2\$accessKey\$2", mClassLoader), "INSTANCE")
    }
    val accessKey: String get() = callMethod(accessKeyInstance, "invoke") as String

    val hasModulesInResult: Boolean by lazy {
        try {
            BangumiUniformSeason::class.java.getField("modules")
            true
        } catch (ignored: NoSuchFieldException) {
            false
        }
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
        log("Read hook info: $hookInfoFile")
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
        log("Reading hook info is completed, take ${endTime - startTime} ms")
    }

    /**
     * @return Whether to update the serialization file.
     */
    private fun checkHookInfo(): Boolean {
        val needUpdate = mHookInfo.searchIfAbsent("class_retrofit_response") {
            searchRetrofitResponseClass()

        } or mHookInfo.searchIfAbsent("class_fastjson") {
            val fastJsonClass = searchFastJsonClass()
            val notObfuscated = "JSON" == fastJsonClass.simpleName
            mHookInfo["method_fastjson_parse"] = if (notObfuscated) "parseObject" else "a"
            fastJsonClass.name

        } or mHookInfo.searchIfAbsent("class_theme_helper") {
            val themeHelperClass = searchThemeHelperClass()
            mHookInfo["field_color_array"] = searchColorArrayField(themeHelperClass)
            themeHelperClass.name

        } or mHookInfo.searchIfAbsent("class_theme_list_click") {
            searchThemeListClickClass()

        } or mHookInfo.searchIfAbsent("method_resolve_request_params") {
            searchResolveRequestParamsMethod()
        }

        ClassLoaderInjector.releaseClassNames()
        log("Check hook info is completed: needUpdate = $needUpdate")
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
        log("Writing hook info is completed")
    }

    private fun searchRetrofitResponseClass(): String {
        for (method in BangumiApiResponse::class.java.methods) {
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

    private fun searchThemeHelperClass(): Class<*> {
        val classNames = ClassLoaderInjector.getClassNames(Regex("^tv\\.danmaku\\.bili\\.ui\\.theme\\..$"))
        classNames?.forEach {
            val clazz = findClass(it, mClassLoader)
            for (field in clazz.declaredFields) {
                if (Modifier.isStatic(field.modifiers) && field.type == SparseArray::class.java) {
                    return clazz
                }
            }
        }
        return Class.forName("")
    }

    private fun searchColorArrayField(themeHelperClass: Class<*>): String {
        for (field in themeHelperClass.declaredFields) {
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

    private fun searchResolveRequestParamsMethod(): String {
        val resolveParamsClass = findClass("com.bilibili.lib.media.resolver.params.ResolveMediaResourceParams", mClassLoader)
        for (method in resolveParamsClass.declaredMethods) {
            val parameterTypes = method.parameterTypes
            if (parameterTypes.size == 1 && parameterTypes[0] == JSONObject::class.java) {
                return method.name
            }
        }
        return ""
    }

    private class ClassWeak(val initializer: () -> Class<*>?) {
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