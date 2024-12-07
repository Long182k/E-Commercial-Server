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
                val userId = call.parameters["userId"] ?: throw IllegalArgumentException("Invalid user ID")
                val rawBody = call.receiveText()
                val address = Json.decodeFromString<Address>(rawBody)

                try {
                    val orderId = orderService.placeOrder(userId, address)
                    call.respond(HttpStatusCode.OK, SuccessResponse("Place order successfully", mapOf("id" to orderId)))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(400, e.message ?: "Failed to place order"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(500, "An unexpected error occurred"))
            }
        }

        get("/{userId}") {
            try {
                val userId = call.parameters["userId"] ?: throw IllegalArgumentException("Invalid user ID")
                try {
                    val orders = orderService.getOrdersByUserId(userId)
                    call.respond(HttpStatusCode.OK, SuccessResponse("List order", orders))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(404, "User not found"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(500, "An unexpected error occurred"))
            }
        }
    }
} 