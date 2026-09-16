package com.spoofingmobileapp.data

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.spoofingmobileapp.geo.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

data class Favorite(val id: String, val name: String, val position: LatLng)

class FavoritesRepository(context: Context) {

    private val prefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)
    private val _favorites = MutableStateFlow(load())
    val favorites: StateFlow<List<Favorite>> = _favorites.asStateFlow()

    fun add(name: String, position: LatLng) {
        update { it + Favorite(UUID.randomUUID().toString(), name.trim(), position) }
    }

    fun remove(id: String) {
        update { favorites -> favorites.filterNot { it.id == id } }
    }

    private fun update(transform: (List<Favorite>) -> List<Favorite>) {
        val updated = _favorites.updateAndGet(transform)
        prefs.edit { putString(KEY_FAVORITES, encode(updated)) }
    }

    private fun load(): List<Favorite> {
        val json = prefs.getString(KEY_FAVORITES, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                Favorite(
                    id = item.getString("id"),
                    name = item.getString("name"),
                    position = LatLng(item.getDouble("lat"), item.getDouble("lng")),
                )
            }
        } catch (e: JSONException) {
            Log.w(TAG, "Discarding unreadable favorites", e)
            emptyList()
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Discarding favorites with invalid coordinates", e)
            emptyList()
        }
    }

    private fun encode(favorites: List<Favorite>): String {
        val array = JSONArray()
        favorites.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("lat", it.position.latitude)
                    .put("lng", it.position.longitude),
            )
        }
        return array.toString()
    }

    private companion object {
        const val TAG = "FavoritesRepository"
        const val KEY_FAVORITES = "favorites"
    }
}
