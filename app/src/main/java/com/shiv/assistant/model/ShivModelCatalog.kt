package com.shiv.assistant.model

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.shiv.assistant.data.ShivVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ModelCandidate(val id: String, val name: String, val sizeBytes: Long, val status: String)
/**
 * Registers SAF references without copying multi-GB weights.
 * Intentionally reports UNVERIFIED, not LOADABLE, until a real Android runtime validates the bytes.
 */
class ShivModelCatalog(private val context: Context, private val vault: ShivVault) {
    suspend fun register(uri: Uri): ModelCandidate = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val metadata = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) null else {
                val name = cursor.getString(0) ?: "model"
                val size = if (cursor.isNull(1)) -1L else cursor.getLong(1)
                name to size
            }
        } ?: throw IllegalArgumentException("Unable to inspect selected model")
        require(metadata.second >= 1_000_000L) { "Model file too small or size unknown" }
        require(metadata.second <= 8_000_000_000L) { "Model file exceeds 8 GB" }
        // Check that the content is actually readable; an extension alone never validates a model.
        resolver.openInputStream(uri)?.use { stream ->
            require(stream.read() != -1) { "Empty model file" }
        } ?: throw IllegalArgumentException("Cannot read selected model")
        val candidate = ModelCandidate(UUID.randomUUID().toString(), metadata.first.take(150),
            metadata.second, "IMPORTED - FORMAT / RUNTIME NOT VERIFIED")
        val records = JSONArray(vault.get("model_catalog") ?: "[]")
        records.put(JSONObject().put("id", candidate.id).put("name", candidate.name)
            .put("size", candidate.sizeBytes).put("status", candidate.status)
            .put("uri", uri.toString()))
        vault.put("model_catalog", records.toString())
        candidate
    }
    fun all(): List<ModelCandidate> {
        val records = JSONArray(vault.get("model_catalog") ?: "[]")
        return (0 until records.length()).map { index ->
            val obj = records.getJSONObject(index)
            ModelCandidate(obj.getString("id"), obj.getString("name"),
                obj.getLong("size"), obj.getString("status"))
        }
    }
    fun remove(id: String) {
        val current = JSONArray(vault.get("model_catalog") ?: "[]")
        val updated = JSONArray()
        for (i in 0 until current.length()) if (current.getJSONObject(i).getString("id") != id) updated.put(current.get(i))
        vault.put("model_catalog", updated.toString())
    }
}
