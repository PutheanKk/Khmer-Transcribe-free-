package com.example.khmersubtitle

import android.content.Context
import java.io.File

object SubtitleUtils {

    /** រក្សាទុកខ្លឹមសារ SRT ទៅជា file នៅ cache/subtitles/ ដើម្បីអាច share/export */
    fun saveSrt(context: Context, srtContent: String, baseName: String): File {
        val dir = File(context.cacheDir, "subtitles").apply { mkdirs() }
        val file = File(dir, "$baseName.srt")
        file.writeText(srtContent)
        return file
    }

    /** កំណត់ MIME type ពី extension ឈ្មោះឯកសារវីដេអូ (fallback ទៅ mp4) */
    fun guessMimeType(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "3gp" -> "video/3gpp"
            else -> "video/mp4"
        }
    }
}
