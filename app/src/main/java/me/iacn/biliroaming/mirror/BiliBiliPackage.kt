package me.iacn.biliroaming.mirror

import android.content.Context
import android.os.Bundle
import android.util.SparseArray
import android.view.View
import de.robv.android.xposed.XposedHelpers.ClassNotFoundError
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.XposedHelpers.getStaticObjectField
import me.iacn.biliroaming.BILIBILI_PACKAGENAME
import me.iacn.biliroaming.inject.ClassLoaderInjector
import me.iacn.biliroaming.log
import me.iacn.biliroaming.searchIfAbsent
import me.iacn.biliroaming.searchIfMultipleAbsent
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType

/**
 * Created by Meolunr on 2019/4/5
 * Email meolunr@gmail.com
 */
class BiliBiliPackage private constructor() {

    private lateinit var mClassLoader: ClassLoader
    private var mHookInfo: MutableMap<String, String> = mutableMapOf()

    val retrofitResponse get() = mHookInfo["class_retrofit_response"]
    val fastJsonParse get() = mHookInfo["method_fastjson_parse"]
    val colorArray get() = mHookInfo["field_color_array"]
    val garbName get() = mHookInfo["class_garb_name"]
    val skinLoaded get() = mHookInfo["method_skin_loaded"]
    val themeListClickListener get() = mHookInfo["class_theme_list_click"]
    val saveThemeKey get() = mHookInfo["method_save_theme_key"]
    val themeErrorImpls get() = mHookInfo["methods_theme_error_impl"]
    val resolveRequestParams get() = mHookInfo["method_resolve_request_params"]
    val sharePlatformDispatch get() = mHookInfo["class_share_platform_dispatch"]
    val shareHandleBundle get() = mHookInfo["method_share_handle_bundle"]

    val fastJson: Class<*> by WeakClass { findClass(mHookInfo["class_fastjson"], mClassLoader) }
    val themeHelper: Class<*> by WeakClass { findClass(mHookInfo["class_theme_helper"], mClassLoader) }

    private val accessKeyInstance by lazy {
        getStaticObjectField(findClass("com.bilibili.bangumi.ui.page.detail.pay.BangumiPayHelperV2\$accessKey\$2", mClassLoader), "INSTANCE")
    }
    val accessKey: String get() = callMethod(accessKeyInstance, "invoke") as String

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

        if (hookInfoFile.isFile && hookInfoFile.canRead()) {
            val lastUpdateTime = context.packageManager.getPackageInfo(BILIBILI_PACKAGENAME, 0).lastUpdateTime
            ObjectInputStream(FileInputStream(hookInfoFile)).use {
                if (it.readLong() == lastUpdateTime) {
                    mHookInfo = it.readObject() as MutableMap<String, String>
                }
            }
        }

        log("Reading hook info is completed")
    }

    /**
     * @return Whether to update the serialization file.
     */
    private fun checkHookInfo(): Boolean {
        val startTime = System.currentTimeMillis()

        val needUpdate = mHookInfo.searchIfAbsent("class_retrofit_response") {
            searchRetrofitResponseClass()

        } or mHookInfo.searchIfMultipleAbsent("class_fastjson", "method_fastjson_parse") {
            searchFastJson()

        } or mHookInfo.searchIfMultipleAbsent("class_theme_helper", "field_color_array", "method_save_theme_key") {
            searchThemeHelper()

        } or mHookInfo.searchIfAbsent("class_garb_name") {
            searchGarbNameClass()

        } or mHookInfo.searchIfAbsent("method_skin_loaded") {
            searchSkinLoadedMethod()

        } or mHookInfo.searchIfAbsent("class_theme_list_click") {
            searchThemeListClickClass()

        } or mHookInfo.searchIfAbsent("methods_theme_error_impl") {
            searchThemeErrorImplMethods()

        } or mHookInfo.searchIfAbsent("method_resolve_request_params") {
            searchResolveRequestParamsMethod()

        } or mHookInfo.searchIfMultipleAbsent("class_share_platform_dispatch", "method_share_handle_bundle") {
            searchSharePlatformDispatch()
        }

        ClassLoaderInjector.releaseClassNames()
        val endTime = System.currentTimeMillis()
        log("Check hook info is completed, take ${endTime - startTime} ms: needUpdate = $needUpdate")
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
        val bangumiApiResponse = findClass("com.bilibili.bangumi.data.common.api.BangumiApiResponse", mClassLoader)
        for (method in bangumiApiResponse.methods) {
            if ("extractResult" == method.name) {
                val responseClass = method.parameterTypes[0]
                return responseClass.name
            }
        }
        return ""
    }

    private fun searchFastJson(): Array<String> {
        val fastJsonClass = try {
            findClass("com.alibaba.fastjson.JSON", mClassLoader)
        } catch (e: ClassNotFoundError) {
            findClass("com.alibaba.fastjson.a", mClassLoader)
        }
        return arrayOf(fastJsonClass.name, if ("JSON" == fastJsonClass.simpleName) "parseObject" else "a")
    }

    private fun searchThemeHelper(): Array<String> {
        val result = Array(3) { "" }

        val classNames = ClassLoaderInjector.getClassNames("tv.danmaku.bili.ui.theme", Regex("^tv\\.danmaku\\.bili\\.ui\\.theme\\.[^.]+$"))
        classNames?.map {
            findClass(it, mClassLoader)
        }?.firstOrNull { clazz ->
            // Search color array field
            clazz.declaredFields.filter { Modifier.isStatic(it.modifiers) }.filter { it.type == SparseArray::class.java }
                    .firstOrNull { "int[]" == (it.genericType as ParameterizedType).actualTypeArguments[0].toString() }
                    ?.let {
                        result[1] = it.name
                        return@firstOrNull true
                    }
            false
        }?.also {
            result[0] = it.name
        }?.declaredMethods?.forEach {
            // Search save theme key method
            val parameters = it.parameterTypes
            if (parameters.size == 2 && parameters[0] == Context::class.java && parameters[1] == Int::class.java) {
                result[2] = it.name
                return@forEach
            }
        }

        return result
    }

    private fun searchGarbNameClass(): String {
        val classNames = ClassLoaderInjector.getClassNames("tv.danmaku.bili.ui.garb", Regex("^tv\\.danmaku\\.bili\\.ui\\.garb\\.[^.]+$"))
        classNames?.forEach {
            val clazz = findClass(it, mClassLoader)
            for (field in clazz.declaredFields) {
                if (Modifier.isStatic(field.modifiers) && field.type == Map::class.java && field.name == "a")
                    return clazz.name
            }
        }
        return ""
    }

    private fun searchSkinLoadedMethod(): String {
        val themeStoreActivityClass = findClass("tv.danmaku.bili.ui.theme.ThemeStoreActivity", mClassLoader)
        val biliSkinListClass = findClass("tv.danmaku.bili.ui.theme.api.BiliSkinList", mClassLoader)
        for (method in themeStoreActivityClass.declaredMethods) {
            val parameters = method.parameterTypes
            if (parameters.size == 2 && parameters[0] == biliSkinListClass && parameters[1] == Boolean::class.java)
                return method.name
        }
        return ""
    }

    private fun searchThemeListClickClass(): String {
        val themeStoreActivityClass = findClass("tv.danmaku.bili.ui.theme.ThemeStoreActivity", mClassLoader)
        for (innerClass in themeStoreActivityClass.declaredClasses) {
            for (interfaceClass in innerClass.interfaces) {
                if (interfaceClass == View.OnClickListener::class.java)
                    return innerClass.name
            }
        }
        return ""
    }

    private fun searchThemeErrorImplMethods(): String {
        val mainActivityClass = findClass("tv.danmaku.bili.MainActivityV2", mClassLoader)
        for (interfaceClass in mainActivityClass.interfaces) {
            if (interfaceClass.name.startsWith("tv.danmaku.bili.ui.theme."))
                return interfaceClass.declaredMethods.joinToString("|") { it.name }
        }
        return ""
    }

    private fun searchResolveRequestParamsMethod(): String {
        val resolveParamsClass = findClass("com.bilibili.lib.media.resolver.params.ResolveMediaResourceParams", mClassLoader)
        for (method in resolveParamsClass.declaredMethods) {
            val parameterTypes = method.parameterTypes
            if (parameterTypes.size == 1 && parameterTypes[0] == JSONObject::class.java)
                return method.name
        }
        return ""
    }

    private fun searchSharePlatformDispatch(): Array<String> {
        val classNames = ClassLoaderInjector.getClassNames("com.bilibili.lib.sharewrapper", Regex("^com\\.bilibili\\.lib\\.sharewrapper\\.[^.]+$"))
        classNames?.forEach {
            val clazz = findClass(it, mClassLoader)
            for (method in clazz.declaredMethods) {
                val parameterTypes = method.parameterTypes
                if (parameterTypes.size == 2 && parameterTypes[0] == String::class.java && parameterTypes[1] == Bundle::class.java)
                    return arrayOf(clazz.name, method.name)
            }
        }
        return arrayOf()
    }
}