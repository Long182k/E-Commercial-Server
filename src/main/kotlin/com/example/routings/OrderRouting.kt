package com.example.routings

import com.example.models.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

fun Route.orderRouting(orderService: OrderService) {
    route("/orders") {
        post("/{userId}") {
            try {
                val userId = call.parameters["userId"]?.toInt()
                    ?: throw IllegalArgumentException("Invalid user ID")
                val address = call.receive<Address>()
                val order = orderService.placeOrder(userId, address)
                call.respond(
                    HttpStatusCode.Created,
                    mapOf("msg" to "Order placed successfully", "data" to order)
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to (e.message ?: "Failed to place order"))
                )
            }
        }

        get("/{userId}") {
            try {
                val userId = call.parameters["userId"]?.toInt()
                    ?: throw IllegalArgumentException("Invalid user ID")
                val orders = orderService.getOrdersByUserId(userId)
                call.respond(
                    HttpStatusCode.OK,
                    mapOf("msg" to "Orders retrieved successfully", "data" to orders)
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to (e.message ?: "Failed to get orders"))
                )
            }
        }
    }
} 