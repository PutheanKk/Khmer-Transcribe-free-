package com.example.khmersubtitle

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * សេវាកម្មសម្រាប់ហៅ Gemini API ២ជំហាន:
 * 1) Upload វីដេអូទៅ Gemini File API (files.upload)
 * 2) ហៅ generateContent ដោយភ្ជាប់ file URI ដើម្បីឲ្យ Gemini
 *    - ស្គាល់ភាសានិយាយក្នុងវីដេអូ (auto-detect)
 *    - បកប្រែជាភាសាខ្មែរ
 *    - បង្កើត Subtitle ជា SRT format ដែលមាន timestamp ត្រឹមត្រូវ
 *
 * ចំណាំ: ប្តូរ MODEL_NAME ខាងក្រោមប្រសិនបើ Google ដាក់ជំនាន់ថ្មី
 * (សូមពិនិត្យ https://ai.google.dev/gemini-api/docs/models សម្រាប់ឈ្មោះ model ចុងក្រោយ)
 */
class GeminiApiService(private val apiKey: String) {

    companion object {
        private const val MODEL_NAME = "gemini-2.5-flash"
        private const val BASE_URL = "https://generativelanguage.googleapis.com"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .build()

    /** លទ្ធផលចេញពី Gemini: អត្ថបទ SRT ជាភាសាខ្មែរ */
    data class SubtitleResult(val srtContent: String, val detectedLanguage: String)

    /**
     * ជំហានទី ១: Upload ឯកសារវីដេអូតាម resumable upload protocol របស់ Gemini File API
     * ត្រឡប់ជា file URI (uri) និង name ដែលត្រូវប្រើនៅជំហានបន្ទាប់
     */
    fun uploadVideo(videoFile: File, mimeType: String): Pair<String, String> {
        val numBytes = videoFile.length()

        // ជំហាន 1.1: ចាប់ផ្តើម resumable upload session
        val startBody = JSONObject().apply {
            put("file", JSONObject().apply { put("display_name", videoFile.name) })
        }.toString().toRequestBody("application/json".toMediaTypeOrNull())

        val startRequest = Request.Builder()
            .url("$BASE_URL/upload/v1beta/files?key=$apiKey")
            .addHeader("X-Goog-Upload-Protocol", "resumable")
            .addHeader("X-Goog-Upload-Command", "start")
            .addHeader("X-Goog-Upload-Header-Content-Length", numBytes.toString())
            .addHeader("X-Goog-Upload-Header-Content-Type", mimeType)
            .post(startBody)
            .build()

        val startResponse = client.newCall(startRequest).execute()
        if (!startResponse.isSuccessful) {
            throw RuntimeException("Upload start failed: ${startResponse.code} ${startResponse.body?.string()}")
        }
        val uploadUrl = startResponse.header("X-Goog-Upload-URL")
            ?: throw RuntimeException("មិនមាន upload URL ត្រឡប់មកវិញទេ")
        startResponse.close()

        // ជំហាន 1.2: Upload ខ្លឹមសារឯកសារពិត
        val uploadRequest = Request.Builder()
            .url(uploadUrl)
            .addHeader("X-Goog-Upload-Offset", "0")
            .addHeader("X-Goog-Upload-Command", "upload, finalize")
            .post(videoFile.asRequestBody(mimeType.toMediaTypeOrNull()))
            .build()

        val uploadResponse = client.newCall(uploadRequest).execute()
        if (!uploadResponse.isSuccessful) {
            throw RuntimeException("Upload failed: ${uploadResponse.code} ${uploadResponse.body?.string()}")
        }
        val json = JSONObject(uploadResponse.body?.string() ?: "{}")
        uploadResponse.close()

        val fileInfo = json.getJSONObject("file")
        val fileUri = fileInfo.getString("uri")
        val fileName = fileInfo.getString("name")
        return Pair(fileUri, fileName)
    }

    /**
     * ជំហានទី ២: រង់ចាំរហូតដល់ file state ក្លាយជា ACTIVE (Gemini ត្រូវការពេលដំណើរការវីដេអូ)
     */
    fun waitUntilActive(fileName: String, maxAttempts: Int = 30) {
        repeat(maxAttempts) {
            val req = Request.Builder()
                .url("$BASE_URL/v1beta/$fileName?key=$apiKey")
                .get()
                .build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: "{}"
            resp.close()
            val state = JSONObject(body).optString("state", "")
            if (state == "ACTIVE") return
            if (state == "FAILED") throw RuntimeException("ការដំណើរការឯកសារវីដេអូបានបរាជ័យ")
            Thread.sleep(2000)
        }
        throw RuntimeException("អស់ពេលរង់ចាំឯកសារឲ្យ Active")
    }

    /**
     * ជំហានទី ៣: ស្នើសុំ Gemini ឲ្យស្តាប់សំឡេងក្នុងវីដេអូ ស្គាល់ភាសា
     * បកប្រែជាខ្មែរ ហើយបង្កើត SRT subtitles ដែលមាន timestamp
     */
    fun generateKhmerSubtitles(fileUri: String, mimeType: String): SubtitleResult {
        val prompt = """
            អ្នកគឺជាអ្នកជំនាញបកប្រែនិងធ្វើ subtitle ជាអ្នកជំនាញ។
            សូមស្តាប់សំឡេងនិយាយក្នុងវីដេអូនេះ:
            1. ស្គាល់ភាសាដើមដែលគេនិយាយ (auto-detect language) ដោយខ្លួនឯង
            2. សរសេរអត្ថបទសំដីទាំងអស់ (transcript) តាមលំដាប់ពេលវេលា
            3. បកប្រែជាភាសាខ្មែរ ឲ្យបានត្រឹមត្រូវតាមបរិបទ (natural Khmer, not literal word-by-word)
            4. បង្កើតជា subtitle file ទម្រង់ SRT ស្តង់ដារ ដែលមាន:
               - លេខរៀង
               - timestamp ចាប់ផ្តើម --> បញ្ចប់ ទម្រង់ HH:MM:SS,mmm
               - អត្ថបទជាភាសាខ្មែរតែប៉ុណ្ណោះ (មិនត្រូវដាក់អត្ថបទភាសាដើមទេ)

            សូមឆ្លើយតបមកវិញតែក្នុងទម្រង់ JSON ដូចខាងក្រោម គ្មានអត្ថបទផ្សេងក្រៅពី JSON:
            {
              "detected_language": "ឈ្មោះភាសាដើមដែលរកឃើញ",
              "srt": "ខ្លឹមសារ SRT ពេញលេញ រួមទាំង newline (\n) រវាងជួរនីមួយៗ"
            }
        """.trimIndent()

        val requestJson = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().apply {
                    put("parts", JSONArray()
                        .put(JSONObject().apply {
                            put("file_data", JSONObject().apply {
                                put("mime_type", mimeType)
                                put("file_uri", fileUri)
                            })
                        })
                        .put(JSONObject().apply { put("text", prompt) })
                    )
                }
            ))
            put("generationConfig", JSONObject().apply {
                put("response_mime_type", "application/json")
                put("temperature", 0.2)
            })
        }

        val request = Request.Builder()
            .url("$BASE_URL/v1beta/models/$MODEL_NAME:generateContent?key=$apiKey")
            .post(requestJson.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        val response = client.newCall(request).execute()
        val bodyStr = response.body?.string() ?: "{}"
        response.close()
        if (!response.isSuccessful) {
            throw RuntimeException("Gemini API error: ${response.code} $bodyStr")
        }

        val root = JSONObject(bodyStr)
        val text = root.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")

        val resultJson = JSONObject(text)
        return SubtitleResult(
            srtContent = resultJson.getString("srt"),
            detectedLanguage = resultJson.optString("detected_language", "មិនស្គាល់")
        )
    }
}
