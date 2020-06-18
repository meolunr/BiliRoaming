package me.iacn.biliroaming.network

import java.io.InputStream

/**
 * Created by iAcn on 2019/3/26
 * Email i@iacn.me
 */
object StreamUtils {

    fun getContent(inputStream: InputStream): String {
        inputStream.bufferedReader().use {
            return it.readText()
        }
    }
}