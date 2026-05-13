package com.zenithfoods.android

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONObject

private val Context.zenithDataStore: DataStore<Preferences> by preferencesDataStore(name = "zenith_prefs")

private val KEY_CART = stringPreferencesKey("zenith_cart")
private val KEY_FAV = stringPreferencesKey("zenith_favorites")

class ZenithPreferences(private val context: Context) {

    suspend fun loadCart(productsById: Map<Int, Product>): Map<Int, CartLine> {
        val prefs = context.zenithDataStore.data.first()
        val raw = prefs[KEY_CART] ?: return emptyMap()
        return try {
            val root = JSONObject(raw)
            buildMap {
                for (key in root.keys()) {
                    val id = key.toIntOrNull() ?: continue
                    val o = root.getJSONObject(key)
                    val qty = o.optInt("quantity", 1)
                    val base = productsById[id] ?: continue
                    put(id, base.toCartLine(qty))
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    suspend fun saveCart(cart: Map<Int, CartLine>) {
        val root = JSONObject()
        for ((id, line) in cart) {
            root.put(
                id.toString(),
                JSONObject()
                    .put("id", line.id)
                    .put("name", line.name)
                    .put("category", line.category)
                    .put("price", line.price)
                    .put("unit", line.unit)
                    .put("emoji", line.emoji)
                    .put("quantity", line.quantity),
            )
        }
        context.zenithDataStore.edit { it[KEY_CART] = root.toString() }
    }

    suspend fun loadFavoriteIds(): Set<Int> {
        val prefs = context.zenithDataStore.data.first()
        val raw = prefs[KEY_FAV] ?: return emptySet()
        return try {
            val arr = org.json.JSONArray(raw)
            buildSet {
                for (i in 0 until arr.length()) {
                    add(arr.getInt(i))
                }
            }
        } catch (_: Exception) {
            emptySet()
        }
    }

    suspend fun saveFavoriteIds(ids: Set<Int>) {
        val arr = org.json.JSONArray()
        ids.sorted().forEach { arr.put(it) }
        context.zenithDataStore.edit { it[KEY_FAV] = arr.toString() }
    }

    suspend fun clearLocal() {
        context.zenithDataStore.edit {
            it.remove(KEY_CART)
            it.remove(KEY_FAV)
        }
    }
}
