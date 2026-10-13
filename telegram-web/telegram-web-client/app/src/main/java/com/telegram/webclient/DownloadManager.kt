package com.telegram.webclient

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import java.io.File
import java.net.URLDecoder

class DownloadManager(private val context: Context) {
    fun download(url: String, userAgent: String, contentDisposition: String, mimetype: String) {
        val fileName = extractFileName(url, contentDisposition) ?: return
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val dest = File(dir, fileName)
        try {
            val conn = java.net.URL(url).openConnection() as java.net.HttpsURLConnection
            conn.setRequestProperty("User-Agent", userAgent)
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000
            conn.inputStream.use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.provider", dest
            )
            val i = Intent(Intent.ACTION_VIEW).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setDataAndType(uri, mimetype)
            }
            context.startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(context, "Download gagal: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun extractFileName(url: String, contentDisposition: String?): String? {
        if (!contentDisposition.isNullOrBlank()) {
            val match = Regex("filename\\*?=(?:UTF-8'')?\"?([^\";]+)\"?").find(contentDisposition)
            if (match != null) return URLDecoder.decode(match.groupValues[1], "UTF-8")
        }
        val last = url.substringAfterLast('/')
        return if (last.isBlank()) "download_${System.currentTimeMillis()}" else last.substringAfter('?')
    }
}