package me.iacn.biliroaming.logic

import android.content.Context
import android.graphics.Color
import android.util.SparseArray
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.XposedHelpers.getStaticObjectField
import me.iacn.biliroaming.ColorChooseDialog
import me.iacn.biliroaming.ConfigManager
import me.iacn.biliroaming.log
import me.iacn.biliroaming.mirror.BiliBiliPackage
import tv.danmaku.bili.ui.theme.api.BiliSkin

/**
 * Created by Meolunr on 2020/9/21
 * Email meolunr@gmail.com
 */
object CustomTheme {

    private const val CUSTOM_THEME_ID = 114514  // ん？

    // Kotlin does not natively support negative values
    // See: https://youtrack.jetbrains.com/issue/KT-2780
    private const val DEFAULT_CUSTOM_COLOR = 0xFFF19483.toInt()

    fun onStartHook(classLoader: ClassLoader) {
        val biliPackage = BiliBiliPackage.instance
        val colorArray = getStaticObjectField(biliPackage.themeHelper, biliPackage.colorArray) as SparseArray<IntArray>

        val primaryColor = ConfigManager.instance.getCustomColor(DEFAULT_CUSTOM_COLOR)
        colorArray.put(CUSTOM_THEME_ID, generateColorArray(primaryColor))

        val garbNameClass = findClass(biliPackage.garbName, classLoader)
        val garbBundle = getStaticObjectField(garbNameClass, "a") as MutableMap<String, Int>
        garbBundle["custom"] = CUSTOM_THEME_ID
    }

    fun onLoadSkinList(skinList: MutableList<Any>) {
        val biliSkin = BiliSkin().apply {
            mId = CUSTOM_THEME_ID
            mIsFree = true
            mName = "自选颜色"
        }
        // Under the night mode item
        skinList.add(3, biliSkin)

        log("Add a theme item: size = " + skinList.size)
    }

    /**
     * @return Whether to stop executing the original method
     */
    fun onSkinItemClick(param: MethodHookParam, context: Context, biliSkin: BiliSkin): Boolean {
        // Make colors updated immediately
        if (biliSkin.mId != CUSTOM_THEME_ID && biliSkin.mId != -1) return false
        log("Custom theme item has been clicked")

        val colorDialog = ColorChooseDialog(context, ConfigManager.instance.getCustomColor(DEFAULT_CUSTOM_COLOR))
        colorDialog.setPositiveButton("确定") { _, _ ->
            val color = colorDialog.getColor()
            val colors = generateColorArray(color)

            val biliPackage = BiliBiliPackage.instance
            val colorArray = getStaticObjectField(biliPackage.themeHelper, biliPackage.colorArray) as SparseArray<IntArray>
            colorArray.put(CUSTOM_THEME_ID, colors)
            colorArray.put(-1, colors)  // Add a new color id but it won't be saved

            // If it is currently use the custom theme, it will use temporary id
            // To make the theme color take effect immediately
            val newId = if (biliSkin.mId == CUSTOM_THEME_ID) -1 else CUSTOM_THEME_ID
            biliSkin.mId = newId

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
        return true
    }

    /**
     * @return The new skin id that needs to be saved
     */
    fun onSaveThemeId(id: Int) = if (id == -1) CUSTOM_THEME_ID else id

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