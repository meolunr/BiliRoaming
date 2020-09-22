package me.iacn.biliroaming.hook

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.getObjectField
import me.iacn.biliroaming.ConfigManager
import me.iacn.biliroaming.log
import me.iacn.biliroaming.logic.CommentFloor

/**
 * Created by Meolunr on 2020/2/27
 * Email meolunr@gmail.com
 */
class CommentConfigHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    override fun startHook() {
        if (!ConfigManager.instance.enableCommentFloor()) return
        log("Start hook: Comment")

        val floorHook: XC_MethodHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val config = getObjectField(param.thisObject, "config")
                config?.let {
                    CommentFloor.onApplyCommentConfig(it)
                }
            }
        }

        findAndHookMethod("com.bilibili.app.comm.comment2.model.BiliCommentCursorList", mClassLoader, "isShowFloor", floorHook)
        findAndHookMethod("com.bilibili.app.comm.comment2.model.BiliCommentDialogue", mClassLoader, "isShowFloor", floorHook)
        findAndHookMethod("com.bilibili.app.comm.comment2.model.BiliCommentDetail", mClassLoader, "isShowFloor", floorHook)
    }
}