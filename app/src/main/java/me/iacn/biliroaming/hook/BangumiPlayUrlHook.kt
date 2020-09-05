package me.iacn.biliroaming.hook

import android.net.Uri
import com.bapis.bilibili.app.playurl.v1.DashItem
import com.bapis.bilibili.app.playurl.v1.DashVideo
import com.bapis.bilibili.app.playurl.v1.Stream
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
//                println(response)
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
            })

            setVideoInfo(VideoInfo.newBuilder().apply {
                setFormat(contentJson.optString("format"))
                setQuality(contentJson.optInt("quality"))
                setTimelength(contentJson.optLong("timelength"))
                setVideoCodecid(contentJson.optInt("video_codecid"))

                val audios = dash.getJSONArray("audio")
                val audioIds = mutableListOf<Int>()
                for (i in 0 until audios.length()) {
                    val audio = audios.getJSONObject(i)
                    val id = audio.optInt("id")
                    audioIds.add(id)

                    addDashAudio(DashItem.newBuilder().apply {
                        setBaseUrl(audio.optString("base_url"))
                        setBandwidth(audio.optInt("bandwidth"))
                        setCodecid(audio.optInt("codecid"))
                        setMd5(audio.optString("md5"))
                        setSize(audio.optLong("size"))
                        setId(id)

                        val urls = audio.getJSONArray("backup_url")
                        for (j in 0 until urls.length()) {
                            addBackupUrl(urls.getString(j))
                        }
                    })
                }

                var audioIndex = audioIds.size
                val videos = dash.getJSONArray("video")
                for (i in 0 until videos.length()) {
                    val video = videos.getJSONObject(i)
                    // Unused video info
                    if (video.optInt("codecid") != contentJson.optInt("video_codecid")) continue

                    addStreamList(Stream.newBuilder().apply {
                        setDashVideo(DashVideo.newBuilder().apply {
                            setBaseUrl(video.optString("base_url"))
                            setBandwidth(video.optInt("bandwidth"))
                            setCodecid(video.optInt("codecid"))
                            setMd5(video.optString("md5"))
                            setNoRexcode(video.optInt("no_rexcode") != 0)
                            setSize(video.optLong("size"))

                            // Audio Bandwidth: narrow --> broad
                            // Video Bandwidth: broad  --> narrow
                            // Use the narrowest bandwidth audio when audio and video streams are not a pair. (Official logic)
                            audioIndex--
                            setAudioId(audioIds[if (audioIndex >= 0) audioIndex else 0])

                            val urls = video.getJSONArray("backup_url")
                            for (j in 0 until urls.length()) {
                                addBackupUrl(urls.getString(j))
                            }
                        })
                    })
                }
            })
        }.build()
    }
}