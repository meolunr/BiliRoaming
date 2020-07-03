package me.iacn.biliroaming

import android.content.Context
import android.util.SparseArray
import android.view.View
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XposedHelpers.ClassNotFoundError
import de.robv.android.xposed.XposedHelpers.findClass
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.lang.ref.WeakReference
import java.lang.reflect.ParameterizedType

/**
 * Created by iAcn on 2019/4/5
 * Email i@iacn.me
 */
class BiliBiliPackage private constructor() {
    private lateinit var mClassLoader: ClassLoader
    private var mHookInfo: MutableMap<String, String> = mutableMapOf()

    private var bangumiApiResponseClass: WeakReference<Class<*>?>? = null
    private var fastJsonClass: WeakReference<Class<*>?>? = null
    private var bangumiUniformSeasonClass: WeakReference<Class<*>?>? = null
    private var themeHelperClass: WeakReference<Class<*>?>? = null

    private var mHasModulesInResult = false

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

    fun retrofitResponse(): String? {
        return mHookInfo["class_retrofit_response"]
    }

    fun fastJsonParse(): String? {
        return mHookInfo["method_fastjson_parse"]
    }

    fun colorArray(): String? {
        return mHookInfo["field_color_array"]
    }

    fun themeListClickListener(): String? {
        return mHookInfo["class_theme_list_click"]
    }

    fun bangumiApiResponse(): Class<*> {
        bangumiApiResponseClass = checkNullOrReturn(bangumiApiResponseClass,
                "com.bilibili.bangumi.data.common.api.BangumiApiResponse")
        return bangumiApiResponseClass!!.get()!!
    }

    fun bangumiUniformSeason(): Class<*> {
        if (bangumiUniformSeasonClass == null || bangumiUniformSeasonClass!!.get() == null) {
            val clazz = findClass("com.bilibili.bangumi.data.page.detail.entity.BangumiUniformSeason", mClassLoader)
            bangumiUniformSeasonClass = WeakReference(clazz)

            try {
                clazz.getField("modules")
                mHasModulesInResult = true
            } catch (ignored: NoSuchFieldException) {
            }
        }
        return bangumiUniformSeasonClass!!.get()!!
    }

    fun fastJson(): Class<*>? {
        fastJsonClass = checkNullOrReturn(fastJsonClass, mHookInfo!!["class_fastjson"])
        return fastJsonClass!!.get()
    }

    fun themeHelper(): Class<*>? {
        themeHelperClass = checkNullOrReturn(themeHelperClass, "tv.danmaku.bili.ui.theme.a")
        return themeHelperClass!!.get()
    }

    fun hasModulesInResult(): Boolean {
        log("hasModulesInResult: $mHasModulesInResult")
        return mHasModulesInResult
    }

    private fun checkNullOrReturn(clazz: WeakReference<Class<*>?>?, className: String?): WeakReference<Class<*>?> {
        var clazz = clazz
        if (clazz == null || clazz.get() == null) {
            clazz = WeakReference(XposedHelpers.findClass(className, mClassLoader))
        }
        return clazz
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
            mHookInfo["class_retrofit_response"] = findRetrofitResponseClass()
            needUpdate = true
        }
        if ("class_fastjson" !in mHookInfo) {
            val fastJsonClass = findFastJsonClass()
            val notObfuscated = "JSON" == fastJsonClass.simpleName
            mHookInfo["class_fastjson"] = fastJsonClass.name
            mHookInfo["method_fastjson_parse"] = if (notObfuscated) "parseObject" else "a"
            needUpdate = true
        }
        if ("field_color_array" !in mHookInfo) {
            mHookInfo["field_color_array"] = findColorArrayField()
            needUpdate = true
        }
        if ("class_theme_list_click" !in mHookInfo) {
            mHookInfo["class_theme_list_click"] = findThemeListClickClass()
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

    private fun findRetrofitResponseClass(): String? {
        val methods = bangumiApiResponse()!!.methods
        for (method in methods) {
            if ("extractResult" == method.name) {
                val responseClass = method.parameterTypes[0]
                return responseClass.name
            }
        }
        return null
    }

    private fun findFastJsonClass(): Class<*> {
        val clazz: Class<*>
        clazz = try {
            XposedHelpers.findClass("com.alibaba.fastjson.JSON", mClassLoader)
        } catch (e: ClassNotFoundError) {
            XposedHelpers.findClass("com.alibaba.fastjson.a", mClassLoader)
        }
        return clazz
    }

    private fun findColorArrayField(): String? {
        val fields = themeHelper()!!.declaredFields
        for (field in fields) {
            if (field.type == SparseArray::class.java) {
                val genericType = field.genericType as ParameterizedType
                val types = genericType.actualTypeArguments
                if ("int[]" == types[0].toString()) {
                    return field.name
                }
            }
        }
        return null
    }

    private fun findThemeListClickClass(): String? {
        val themeStoreActivityClass = XposedHelpers.findClass("tv.danmaku.bili.ui.theme.ThemeStoreActivity", mClassLoader)
        for (innerClass in themeStoreActivityClass.declaredClasses) {
            for (interfaceClass in innerClass.interfaces) {
                if (interfaceClass == View.OnClickListener::class.java) {
                    return innerClass.name
                }
            }
        }
        return null
    }
}