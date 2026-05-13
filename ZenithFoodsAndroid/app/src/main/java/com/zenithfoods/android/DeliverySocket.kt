package com.zenithfoods.android

import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.net.URI

class DeliverySocket(
    private val baseUrl: String,
) {
    private var socket: Socket? = null

    fun connect(token: String, onDeliveryInfo: (distance: Double, charge: Int) -> Unit) {
        disconnect()
        val opts = IO.Options()
        opts.auth = mapOf("token" to token)
        val uri = URI.create(baseUrl)
        val s = IO.socket(uri, opts)
        s.on("delivery-info") { args ->
            if (args.isEmpty()) return@on
            val data = args[0] as? JSONObject ?: return@on
            val distance = data.optDouble("distance", 0.0)
            val charge = data.optInt("deliveryCharge", 0)
            onDeliveryInfo(distance, charge)
        }
        s.connect()
        socket = s
    }

    fun emitLocation(lat: Double, lng: Double) {
        val s = socket ?: return
        s.emit("location-update", JSONObject().put("lat", lat).put("lng", lng))
    }

    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
    }
}
