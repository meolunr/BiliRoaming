package me.iacn.biliroaming.network

import android.net.Uri
import me.iacn.biliroaming.BuildConfig
import java.net.HttpURLConnection
import java.net.URL

/**
 * Created by iAcn on 2019/3/27
 * Email i@iacn.me
 */
object BiliRoamingApi {

    private const val BILIROAMING_SEASON_URL = "api.iacn.me/biliroaming/season"
    private const val BILIROAMING_PLAYURL_URL = "api.iacn.me/biliroaming/playurl"

    fun getSeason(id: String, accessKey: String, useCache: Boolean): String {
        val url = with(Uri.Builder()) {
            scheme("https")
            encodedAuthority(BILIROAMING_SEASON_URL)
            appendPath(id)
            if (accessKey.isNotEmpty()) {
                appendQueryParameter("access_key", accessKey)
            }
            appendQueryParameter("use_cache", if (useCache) "1" else "0")
            toString()
        }
        return getContent(url)
    }

    fun getPlayUrl(queryString: String): String = getContent("https://$BILIROAMING_PLAYURL_URL?$queryString")

    private fun getContent(urlString: String): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        with(connection) {
            requestMethod = "GET"
            setRequestProperty("Build", BuildConfig.VERSION_CODE.toString())
            connectTimeout = 4000
            connect()
            if (responseCode == HttpURLConnection.HTTP_OK) {
                return StreamUtils.getContent(connection.inputStream)
            }
        }
        return ""
    }
}