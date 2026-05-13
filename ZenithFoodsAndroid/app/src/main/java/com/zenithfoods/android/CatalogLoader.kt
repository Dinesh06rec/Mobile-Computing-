package com.zenithfoods.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object CatalogLoader {
    fun load(context: Context): List<Product> {
        context.assets.open("catalog.json").bufferedReader().use { reader ->
            val text = reader.readText()
            val arr = JSONArray(text)
            return buildList {
                for (i in 0 until arr.length()) {
                    add((arr.get(i) as JSONObject).toProduct())
                }
            }
        }
    }
}
