package me.iacn.biliroaming.hook

/**
 * Created by Meolunr on 2020/7/16
 * Email meolunr@gmail.com
 */
class BangumiDownloadHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
//        if (!XposedInit.sPrefs.getBoolean("allow_download", false)) return
    }
}