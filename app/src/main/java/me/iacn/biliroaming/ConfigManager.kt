package me.iacn.biliroaming

import android.app.AndroidAppHelper
import android.content.Context
import android.content.SharedPreferences
import de.robv.android.xposed.XSharedPreferences

/**
 * Created by Meolunr on 2020/7/21
 * Email meolunr@gmail.com
 */
class ConfigManager {

    lateinit var xPrefs: XSharedPreferences
    lateinit var biliPrefs: SharedPreferences

    companion object {
        private const val KEY_BANGUMI_DOWNLOAD = "bangumi_download"

        val instance: ConfigManager by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            ConfigManager()
        }
    }

    fun init() {
        xPrefs = XSharedPreferences(BuildConfig.APPLICATION_ID)
        biliPrefs = AndroidAppHelper.currentApplication().getSharedPreferences("bili_preference", Context.MODE_PRIVATE)
    }

    fun enableBangumiDownload(): Boolean {
        return xPrefs.getBoolean(KEY_BANGUMI_DOWNLOAD, false)
    }
}