package me.iacn.biliroaming.network

import android.net.Uri
import me.iacn.biliroaming.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Created by iAcn on 2019/3/27
 * Email i@iacn.me
 */
object BiliRoamingApi {

    private const val BILIBILI_SEASON_URL = "api.bilibili.com/pgc/view/web/season"
    private const val BILIROAMING_SEASON_URL = "api.iacn.me/biliroaming/season"
    private const val BILIROAMING_PLAYURL_URL = "api.iacn.me/biliroaming/playurl"

    fun getSeason(id: String, accessKey: String, useCache: Boolean): String {
        return if (useCache) {
            getSeasonFromWeb(id, accessKey)
        } else {
            getSeasonFromBiliRoaming(id, accessKey)
        }
    }

    fun getPlayUrl(queryString: String): String = getContent("https://$BILIROAMING_PLAYURL_URL$queryString")

    private fun getSeasonFromWeb(id: String, accessKey: String): String {
        val url = with(Uri.Builder()) {
            scheme("https")
            encodedAuthority(BILIBILI_SEASON_URL)
            if (!id.startsWith("ep")) {
                appendQueryParameter("season_id", id)
            } else {
                appendQueryParameter("ep_id", id.substring(2))
            }
            if (accessKey.isNotEmpty())
                appendQueryParameter("access_key", accessKey)
            toString()
        }

        val contentJson = JSONObject(getContent(url))
        val result = contentJson.getJSONObject("result")
        val modules = JSONArray()

        val positiveModule = JSONObject()
                .put("data", JSONObject().put("episodes", result.getJSONArray("episodes")))
                .put("id", 1)
                .put("module_style", JSONObject().put("hidden", 0).put("line", 0))
                .put("more", "查看更多")
                .put("style", "positive")
                .put("more", "选集")
        modules.put(positiveModule)

        if (result.has("seasons")) {
            val seasons = result.getJSONArray("seasons")
            for (i in 0 until seasons.length()) {
                val season = seasons.getJSONObject(i)
                val seasonId = season.getInt("season_id")
                season.put("link", "https://www.bilibili.com/bangumi/play/ss$seasonId")
            }

            val seasonModule = JSONObject()
                    .put("data", JSONObject().put("seasons", seasons))
                    .put("id", modules.length() + 1)
                    .put("module_style", JSONObject().put("line", 1))
                    .put("style", "season")
                    .put("title", "")
            modules.put(seasonModule)
        }

        result.put("modules", modules)
        return contentJson.toString()
    }

    private fun getSeasonFromBiliRoaming(id: String, accessKey: String): String {
        val url = with(Uri.Builder()) {
            scheme("https")
            encodedAuthority(BILIROAMING_SEASON_URL)
            appendPath(id)
            if (accessKey.isNotEmpty())
                appendQueryParameter("access_key", accessKey)
            toString()
        }
        return getContent(url)
    }

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