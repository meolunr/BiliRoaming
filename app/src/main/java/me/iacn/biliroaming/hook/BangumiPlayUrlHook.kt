package me.iacn.biliroaming.hook

import android.net.Uri
import com.bapis.bilibili.app.playurl.v1.DashItem
import com.bapis.bilibili.app.playurl.v1.VideoInfo
import com.bapis.bilibili.pgc.gateway.player.v1.PlayAbilityConf
import com.bapis.bilibili.pgc.gateway.player.v1.PlayViewReply
import com.bapis.bilibili.pgc.gateway.player.v1.PlayViewReq
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import me.iacn.biliroaming.ConfigManager
import me.iacn.biliroaming.log
import me.iacn.biliroaming.mirror.BiliBiliPackage
import me.iacn.biliroaming.network.BiliRoamingApi
import me.iacn.biliroaming.toIntString
import org.json.JSONObject

/**
 * Created by Meolunr on 2019/3/29
 * Email meolunr@gmail.com
 */
class BangumiPlayUrlHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    override fun startHook() {
        if (!ConfigManager.instance.enableMainFunc()) return
        log("Start hook: BangumiPlayUrl")

        findAndHookMethod("com.bapis.bilibili.pgc.gateway.player.v1.PlayURLMoss", mClassLoader, "playView",
                "com.bapis.bilibili.pgc.gateway.player.v1.PlayViewReq", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val response = param.result as PlayViewReply
                // Filter normal play url
                if (response.hasVideoInfo()) return

                println("---------- PlayReq ----------")
                val queryString = constructQueryString(param.args[0] as PlayViewReq)
                println(queryString)

                println("<<< constructProtoBufResponse >>>")
                val contentJson = JSONObject(BiliRoamingApi.getPlayUrl(queryString))
                val newResponse = constructProtoBufResponse(contentJson)
                println(newResponse)
            }
        })
    }

    private fun constructQueryString(request: PlayViewReq): String {
        return with(Uri.Builder()) {
            val accessKey = BiliBiliPackage.instance.accessKey
            if (accessKey.isNotEmpty()) {
                appendQueryParameter("access_key", accessKey)
            }
            appendQueryParameter("cid", request.cid.toString())
            appendQueryParameter("ep_id", request.epId.toString())
            appendQueryParameter("fnval", request.fnval.toString())
            appendQueryParameter("fnver", request.fnver.toString())
            appendQueryParameter("force_host", request.forceHost.toString())
            appendQueryParameter("fourk", request.fourk.toIntString())
            appendQueryParameter("qn", request.qn.toString())
        }.toString()
    }

    private fun constructProtoBufResponse(contentJson: JSONObject): PlayViewReply {
        val dash = contentJson.getJSONObject("dash")

        return PlayViewReply.newBuilder().apply {
            setPlayConf(PlayAbilityConf.newBuilder().apply {
                setDislikeDisable(true)
                setElecDisable(true)
                setShakeDisable(true)
            }.build())

            setVideoInfo(VideoInfo.newBuilder().apply {
                setFormat(contentJson.optString("format"))
                setQuality(contentJson.optInt("quality"))
                setTimelength(contentJson.optLong("timelength"))
                setVideoCodecid(contentJson.optInt("video_codecid"))

                val audios = dash.getJSONArray("audio")
                for (i in 0 until audios.length()) {
                    val audio = audios.getJSONObject(i)
                    addDashAudio(DashItem.newBuilder().apply {
                        setBaseUrl(audio.optString("base_url"))
                        setBandwidth(audio.optInt("bandwidth"))
                        setCodecid(audio.optInt("codecid"))
                        setId(audio.optInt("id"))
                        setMd5(audio.optString("md5"))
                        setSize(audio.optLong("size"))

                        val urls = audio.getJSONArray("backup_url")
                        for (j in 0 until urls.length()) {
                            addBackupUrl(urls.getString(j))
                        }
                    }.build())
                }
            }.build())
        }.build()
    }
}