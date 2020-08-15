package me.iacn.biliroaming.hook

import android.content.Context
import android.graphics.Color
import android.util.SparseArray
import android.view.View
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.XposedHelpers.getIntField
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.XposedHelpers.getStaticObjectField
import de.robv.android.xposed.XposedHelpers.setBooleanField
import de.robv.android.xposed.XposedHelpers.setIntField
import de.robv.android.xposed.XposedHelpers.setObjectField
import me.iacn.biliroaming.BiliBiliPackage
import me.iacn.biliroaming.ColorChooseDialog
import me.iacn.biliroaming.ConfigManager
import me.iacn.biliroaming.log

/**
 * Created by iAcn on 2019/7/14
 * Email i@iacn.me
 */
class CustomThemeHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    companion object {
        private const val CUSTOM_THEME_ID = 114514  // ん？

        // Kotlin does not natively support negative values
        // See: https://youtrack.jetbrains.com/issue/KT-2780
        private const val DEFAULT_CUSTOM_COLOR = 0xFFF19483.toInt()
    }

    override fun startHook() {
        if (!ConfigManager.instance.enableCustomTheme()) return
        log("Start hook: CustomTheme")

        val biliPackage = BiliBiliPackage.instance
        val helperClass = biliPackage.themeHelper
        val colorArray = getStaticObjectField(helperClass, biliPackage.colorArray) as SparseArray<IntArray>

        val primaryColor = ConfigManager.instance.getCustomColor(DEFAULT_CUSTOM_COLOR)
        colorArray.put(CUSTOM_THEME_ID, generateColorArray(primaryColor))

        val garbNameClass = findClass(biliPackage.garbName, mClassLoader)
        val garbBundle = getStaticObjectField(garbNameClass, "a") as MutableMap<String, Int>
        garbBundle["custom"] = CUSTOM_THEME_ID

        findAndHookMethod("tv.danmaku.bili.ui.theme.ThemeStoreActivity", mClassLoader, biliPackage.skinLoaded,
                "tv.danmaku.bili.ui.theme.api.BiliSkinList", Boolean::class.java, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val biliSkinList = param.args[0]
                val mList = getObjectField(biliSkinList, "mList") as MutableList<Any>

                val biliSkin = findClass("tv.danmaku.bili.ui.theme.api.BiliSkin", mClassLoader).newInstance().apply {
                    setIntField(this, "mId", CUSTOM_THEME_ID)
                    setObjectField(this, "mName", "自选颜色")
                    setBooleanField(this, "mIsFree", true)
                }
                // Under the night mode item
                mList.add(3, biliSkin)

                log("Add a theme item: size = " + mList.size)
            }
        })

        findAndHookMethod(biliPackage.themeListClickListener, mClassLoader, "onClick", View::class.java, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val view = param.args[0] as View
                val idName = view.resources.getResourceEntryName(view.id)

                if ("list_item" != idName) return

                val biliSkin = view.tag ?: return
                val mId = getIntField(biliSkin, "mId")
                // Make colors updated immediately
                if (mId == CUSTOM_THEME_ID || mId == -1) {
                    log("Custom theme item has been clicked")

                    val colorDialog = ColorChooseDialog(view.context, ConfigManager.instance.getCustomColor(DEFAULT_CUSTOM_COLOR))
                    colorDialog.setPositiveButton("确定") { _, _ ->
                        val color = colorDialog.getColor()
                        val colors = generateColorArray(color)

                        val colorArray = getStaticObjectField(helperClass, BiliBiliPackage.instance.colorArray) as SparseArray<IntArray>
                        colorArray.put(CUSTOM_THEME_ID, colors)
                        colorArray.put(-1, colors)  // Add a new color id but it won't be saved

                        // If it is currently use the custom theme, it will use temporary id
                        // To make the theme color take effect immediately
                        val newId = if (mId == CUSTOM_THEME_ID) -1 else CUSTOM_THEME_ID
                        setIntField(biliSkin, "mId", newId)

                        ConfigManager.instance.putCustomColor(color)
                        log("Update new color: mId = $newId, color = ${String.format("0x%06X", 0xFFFFFF and color)}")

                        try {
                            XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    colorDialog.show()

                    // Stop executing the original method
                    param.result = null
                }
            }
        })

        findAndHookMethod(helperClass, biliPackage.saveThemeKey, Context::class.java, Int::class.java, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val currentThemeKey = param.args[1] as Int
                if (currentThemeKey == -1) {
                    param.args[1] = CUSTOM_THEME_ID
                }
            }
        })

        // Make sure that not invalidate when user not logging in
        val mainActivityClass = findClass("tv.danmaku.bili.MainActivityV2", mClassLoader)
        biliPackage.themeErrorImpls?.split("|")?.forEach {
            findAndHookMethod(mainActivityClass, it, XC_MethodReplacement.DO_NOTHING)
        }
    }
    
    /**
     * Color Array
     *
     * index0: color primary        e.g. global main color.
     * index1: color primary dark   e.g. tint when button be pressed.
     * index2: color primary light  e.g. temporarily not used.
     * index3: color primary trans  e.g. mini-tv cover on drawer.
     */
    private fun generateColorArray(primaryColor: Int): IntArray {
        val colors = IntArray(4)
        val hsv = FloatArray(3)
        val result = FloatArray(3)
        Color.colorToHSV(primaryColor, hsv)

        colors[0] = primaryColor

        // Decrease brightness
        System.arraycopy(hsv, 0, result, 0, hsv.size)
        result[2] -= result[2] * 0.2f
        colors[1] = Color.HSVToColor(result)

        // Increase brightness
        System.arraycopy(hsv, 0, result, 0, hsv.size)
        result[2] += result[2] * 0.1f
        colors[2] = Color.HSVToColor(result)

        // Increase transparency
        colors[3] = -0x4c000000 or 0xFFFFFF and colors[1]

        return colors
    }
}