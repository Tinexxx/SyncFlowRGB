package me.kavishdevar.openrgb.models

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object LocalLightshowStorage {
    private const val PREFS_NAME = "local_lightshows"
    private const val KEY_SHOWS = "lightshows_json"

    fun saveLightshows(context: Context, shows: List<Lightshow>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = Gson().toJson(shows)
        prefs.edit().putString(KEY_SHOWS, json).apply()
    }

    fun loadLightshows(context: Context): List<Lightshow> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_SHOWS, "[]")
        return Gson().fromJson(json, object : TypeToken<List<Lightshow>>() {}.type)
    }
}
