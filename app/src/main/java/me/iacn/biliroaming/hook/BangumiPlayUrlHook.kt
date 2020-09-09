package me.iacn.biliroaming.hook

import android.net.Uri
import com.bapis.bilibili.app.playurl.v1.DashItem
import com.bapis.bilibili.app.playurl.v1.DashVideo
import com.bapis.bilibili.app.playurl.v1.ResponseUrl
import com.bapis.bilibili.app.playurl.v1.SegmentVideo
import com.bapis.bilibili.app.playurl.v1.Stream
import com.bapis.bilibili.app.playurl.v1.StreamInfo
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
import org.json.JSONArray
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

                val queryString = constructQueryString(param.args[0] as PlayViewReq)
                val contentJson = JSONObject(BiliRoamingApi.getPlayUrl(queryString))

                if (contentJson.optInt("code") == 0) {
                    val newResponse = constructProtoBufResponse(contentJson)
                    param.result = newResponse
                    log("The play url was replaced with proxy server, type: protobuf")
                }
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

                val type = contentJson.optString("type")
                log("Construct video stream, type: $type")
                when (type) {
                    "DASH" -> buildInDash(this, contentJson)
                    "FLV" -> buildInSegment(this, contentJson)
                }
            })
        }.build()
    }

    private fun buildInDash(videoInfoBuilder: VideoInfo.Builder, contentJson: JSONObject) {
        videoInfoBuilder.run {
            val dash = contentJson.getJSONObject("dash")

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
                    for (j in 0 until urls.length())
                        addBackupUrl(urls.getString(j))
                })
            }

            var audioIndex = audioIds.size
            val noRexcode = contentJson.optInt("no_rexcode") != 0
            val videoMap = generateVideoMap(dash.getJSONArray("video"), contentJson.getInt("video_codecid"))

            val formats = contentJson.getJSONArray("support_formats")
            for (i in 0 until formats.length()) {
                val format = formats.getJSONObject(i)
                val quality = format.optInt("quality")

                addStreamList(Stream.newBuilder().apply {
                    setStreamInfo(StreamInfo.newBuilder().apply {
                        setAttribute(0)
                        setIntact(true)
                        setQuality(quality)
                        setNoRexcode(noRexcode)
                        setDescription(format.optString("description"))
                        setDisplayDesc(format.optString("display_desc"))
                        setFormat(format.optString("format"))
                        setNeedLogin(format.optBoolean("need_login"))
                        setNeedVip(format.optBoolean("need_vip"))
                        setNewDescription(format.optString("new_description"))
                        setSuperscript(format.optString("superscript"))
                    })

                    videoMap[quality]?.let {
                        setDashVideo(DashVideo.newBuilder().apply {
                            setBaseUrl(it.optString("base_url"))
                            setBandwidth(it.optInt("bandwidth"))
                            setCodecid(it.optInt("codecid"))
                            setMd5(it.optString("md5"))
                            setSize(it.optLong("size"))
                            setNoRexcode(noRexcode)

                            // Audio Bandwidth: narrow --> broad
                            // Video Bandwidth: broad  --> narrow
                            // Use the narrowest bandwidth audio when audio and video streams are not a pair. (Official logic)
                            audioIndex--
                            setAudioId(audioIds[if (audioIndex >= 0) audioIndex else 0])

                            val urls = it.getJSONArray("backup_url")
                            for (j in 0 until urls.length())
                                addBackupUrl(urls.getString(j))
                        })
                    }
                })
            }
        }
    }

    private fun buildInSegment(videoInfoBuilder: VideoInfo.Builder, contentJson: JSONObject) {
        videoInfoBuilder.run {
            val formats = contentJson.getJSONArray("support_formats")
            for (i in 0 until formats.length()) {
                val format = formats.getJSONObject(i)
                val quality = format.optInt("quality")

                addStreamList(Stream.newBuilder().apply {
                    setStreamInfo(StreamInfo.newBuilder().apply {
                        setAttribute(0)
                        setIntact(true)
                        setQuality(quality)
                        setNoRexcode(contentJson.optInt("no_rexcode") != 0)
                        setDescription(format.optString("description"))
                        setDisplayDesc(format.optString("display_desc"))
                        setFormat(format.optString("format"))
                        setNeedLogin(format.optBoolean("need_login"))
                        setNeedVip(format.optBoolean("need_vip"))
                        setNewDescription(format.optString("new_description"))
                        setSuperscript(format.optString("superscript"))
                    })

                    if (quality == contentJson.getInt("quality")) {
                        setSegmentVideo(SegmentVideo.newBuilder().apply {
                            val durls = contentJson.getJSONArray("durl")
                            for (j in 0 until durls.length()) {
                                val durl = durls.getJSONObject(j)

                                addSegment(ResponseUrl.newBuilder().apply {
                                    setLength(durl.optLong("length"))
                                    setMd5(durl.optString("md5"))
                                    setOrder(durl.optInt("order"))
                                    setSize(durl.optLong("size"))
                                    setUrl(durl.optString("url"))

                                    val urls = durl.getJSONArray("backup_url")
                                    for (k in 0 until urls.length())
                                        addBackupUrl(urls.getString(k))
                                })
                            }
                        })
                    }
                })
            }
        }
    }

    private fun generateVideoMap(videoJsonArray: JSONArray, videoCodecid: Int): MutableMap<Int, JSONObject> {
        val videoMap = mutableMapOf<Int, JSONObject>()

        for (i in 0 until videoJsonArray.length()) {
            val video = videoJsonArray.getJSONObject(i)
            // Unused video info
            if (video.optInt("codecid") != videoCodecid) continue

            val quality = video.optInt("id")
            videoMap[quality] = video
        }

        return videoMap
    }
}