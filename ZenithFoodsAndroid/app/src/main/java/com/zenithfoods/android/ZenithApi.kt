package com.zenithfoods.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class ZenithApi(
    private val baseUrl: String,
    private val getIdToken: suspend () -> String?,
) {
    private val client = OkHttpClient()
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun postSession(): Result<Unit> = withContext(Dispatchers.IO) {
        authedRequest(
            Request.Builder()
                .url("$baseUrl/api/auth/session")
                .post("{}".toRequestBody(jsonMedia)),
        ).map { }
    }

    suspend fun getFavorites(): Result<Set<Int>> = withContext(Dispatchers.IO) {
        authedRequest(Request.Builder().url("$baseUrl/api/favorites").get()).map { body ->
            val o = JSONObject(body)
            if (!o.optBoolean("success", false)) return@map emptySet()
            val arr = o.optJSONArray("favorites") ?: return@map emptySet()
            buildSet {
                for (i in 0 until arr.length()) {
                    add(arr.getInt(i))
                }
            }
        }
    }

    suspend fun toggleFavorite(product: Product): Result<Unit> = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("productId", product.id)
            .put("productName", product.name)
            .put("price", product.price)
            .put("category", product.category)
            .toString()
        authedRequest(
            Request.Builder()
                .url("$baseUrl/api/favorites/toggle")
                .post(body.toRequestBody(jsonMedia)),
        ).map { }
    }

    suspend fun checkout(
        lines: List<CartLine>,
        subtotal: Int,
        deliveryCharge: Int,
        distanceKm: Double,
        grandTotal: Int,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val items = JSONArray()
        for (line in lines) {
            items.put(
                JSONObject()
                    .put("productId", line.id)
                    .put("productName", line.name)
                    .put("price", line.price)
                    .put("quantity", line.quantity)
                    .put("total", line.price * line.quantity),
            )
        }
        val root = JSONObject()
            .put("items", items)
            .put("subtotal", subtotal)
            .put("deliveryCharge", deliveryCharge)
            .put("distance", distanceKm)
            .put("grandTotal", grandTotal)
        authedRequest(
            Request.Builder()
                .url("$baseUrl/api/cart/checkout")
                .post(root.toString().toRequestBody(jsonMedia)),
        ).map { }
    }

    private suspend fun authedRequest(builder: Request.Builder): Result<String> {
        val token = getIdToken() ?: return Result.failure(IllegalStateException("Not signed in"))
        val request = builder.header("Authorization", "Bearer $token").build()
        val response = client.newCall(request).execute()
        val body = response.body?.string().orEmpty()
        if (response.code == 401 || response.code == 403) {
            return Result.failure(IllegalStateException("Session expired"))
        }
        if (!response.isSuccessful) {
            return Result.failure(IllegalStateException("HTTP ${response.code}: $body"))
        }
        return Result.success(body)
    }
}
