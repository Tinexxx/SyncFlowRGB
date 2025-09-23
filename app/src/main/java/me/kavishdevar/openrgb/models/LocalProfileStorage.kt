package me.kavishdevar.openrgb.models

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class LocalProfile(
    val name: String,
    val deviceIndex: Int,
    val mode: OpenRGBMode
)

object LocalProfileStorage {
    private const val PREFS_NAME = "local_profiles"
    private const val KEY_PROFILES = "profiles_json"

    fun saveProfiles(context: Context, profiles: List<LocalProfile>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = Gson().toJson(profiles)
        prefs.edit().putString(KEY_PROFILES, json).apply()
    }

    fun loadProfiles(context: Context): List<LocalProfile> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_PROFILES, "[]")
        return Gson().fromJson(json, object : TypeToken<List<LocalProfile>>() {}.type)
    }
}
