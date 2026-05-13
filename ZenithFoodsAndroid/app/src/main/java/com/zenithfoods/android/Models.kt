package com.zenithfoods.android

import org.json.JSONObject

data class Product(
    val id: Int,
    val name: String,
    val category: String,
    val price: Int,
    val unit: String,
    val emoji: String,
) {
    fun toCartLine(quantity: Int): CartLine =
        CartLine(id = id, name = name, category = category, price = price, unit = unit, emoji = emoji, quantity = quantity)
}

data class CartLine(
    val id: Int,
    val name: String,
    val category: String,
    val price: Int,
    val unit: String,
    val emoji: String,
    val quantity: Int,
)

data class ShopCategory(
    val id: String,
    val name: String,
    val emoji: String,
)

data class DeliveryUi(
    val distanceKm: Double = 0.0,
    val charge: Int = 0,
    val lat: Double? = null,
    val lng: Double? = null,
    val located: Boolean = false,
    val error: String? = null,
)

fun JSONObject.toProduct(): Product = Product(
    id = getInt("id"),
    name = getString("name"),
    category = getString("category"),
    price = getInt("price"),
    unit = getString("unit"),
    emoji = getString("emoji"),
)

const val DELIVERY_RATE_PER_KM = 4

val SHOP_CATEGORIES: List<ShopCategory> = listOf(
    ShopCategory("essentials", "Essential Products", "🏠"),
    ShopCategory("rice", "Rice Variety", "🍚"),
    ShopCategory("fruits_veggies", "Fruits & Vegetables", "🥬"),
    ShopCategory("snacks", "Snacks", "🍿"),
    ShopCategory("soft_drinks", "Soft Drinks", "🥤"),
    ShopCategory("milk_products", "Milk Products", "🥛"),
    ShopCategory("soaps_cleaning", "Soaps & Cleaning", "🧴"),
)
