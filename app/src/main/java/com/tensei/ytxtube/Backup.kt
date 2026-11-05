package com.tensei.ytxtube

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * Every setting in one file, to keep or to bring to a new phone. Only the app's own settings
 * files are written; the sign-in (cookies, tokens) never goes into the backup.
 */
object SettingsBackup {
    private val FILES = listOf(
        "playback", "appearance", "ui_prefs", "sponsorblock", "resume", "more_prefs",
        "language_settings", "downloads", "recent_music", "search_history_prefs",
        "feed_filters", "watched", "channel_speeds",
    )

    fun export(context: Context, uri: Uri): String = runCatching {
        val files = JSONObject()
        FILES.forEach { name ->
            val all = context.getSharedPreferences(name, Context.MODE_PRIVATE).all
            if (all.isEmpty()) return@forEach
            val entries = JSONObject()
            all.forEach { (key, value) ->
                val entry = when (value) {
                    is Boolean -> JSONObject().put("t", "b").put("v", value)
                    is Int -> JSONObject().put("t", "i").put("v", value)
                    is Long -> JSONObject().put("t", "l").put("v", value)
                    is Float -> JSONObject().put("t", "f").put("v", value.toDouble())
                    is String -> JSONObject().put("t", "s").put("v", value)
                    is Set<*> -> JSONObject().put("t", "ss").put("v", JSONArray(value.map { it.toString() }))
                    else -> null
                }
                if (entry != null) entries.put(key, entry)
            }
            files.put(name, entries)
        }
        val root = JSONObject()
            .put("app", "YTXTube")
            .put("version", 1)
            .put("created", System.currentTimeMillis())
            .put("prefs", files)
        val out = context.contentResolver.openOutputStream(uri) ?: error("the file could not be opened")
        out.use { it.write(root.toString(2).toByteArray(Charsets.UTF_8)) }
        "Saved. Keep the file somewhere safe (Drive, a computer) to restore it later."
    }.getOrElse { "Backup failed: ${it.message}" }

    fun import(context: Context, uri: Uri): String {
        val restored = runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: error("the file could not be opened")
            val root = JSONObject(text)
            val files = root.optJSONObject("prefs") ?: error("this is not a YTXTube backup")
            var count = 0
            files.keys().forEach { name ->
                if (name !in FILES) return@forEach
                val entries = files.optJSONObject(name) ?: return@forEach
                val edit = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
                entries.keys().forEach { key ->
                    val e = entries.optJSONObject(key) ?: return@forEach
                    when (e.optString("t")) {
                        "b" -> edit.putBoolean(key, e.optBoolean("v"))
                        "i" -> edit.putInt(key, e.optInt("v"))
                        "l" -> edit.putLong(key, e.optLong("v"))
                        "f" -> edit.putFloat(key, e.optDouble("v").toFloat())
                        "s" -> edit.putString(key, e.optString("v"))
                        "ss" -> {
                            val arr = e.optJSONArray("v") ?: JSONArray()
                            edit.putStringSet(key, (0 until arr.length()).map { arr.optString(it) }.toSet())
                        }
                    }
                }
                edit.commit()
                count++
            }
            if (count == 0) error("nothing in this file could be restored")
        }
        restored.exceptionOrNull()?.let { return "Restore failed: ${it.message}" }
        // The settings are read when the app starts, so start it again with them.
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (intent != null) {
            context.startActivity(intent)
            Runtime.getRuntime().exit(0)
        }
        return "Restored. Close and reopen the app to apply."
    }
}
