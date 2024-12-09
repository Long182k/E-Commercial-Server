package com.example.routings

import com.example.models.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

fun Route.cartRouting(cartService: CartService) {
    route("/cart") {
        post("/{userId}") {
            try {
                val userId = call.parameters["userId"]?.toInt() 
                    ?: throw IllegalArgumentException("Invalid user ID")
                val cartItem = call.receive<CartItem>()
                val response = cartService.addToCart(userId, cartItem)
                call.respond(HttpStatusCode.OK, SuccessResponse("Item added to cart successfully", response))

            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(400, e.message ?: "Failed to add item to cart"))

            }
        }

        get("/{userId}") {
            try {
                val userId = call.parameters["userId"]?.toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid user ID")
                println("userId: $userId")
                val cartItems = cartService.getCartByUserId(userId)
                println("cartItems: $cartItems")

                call.respond(HttpStatusCode.OK, SuccessResponse("Cart items retrieved successfully", cartItems))

            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(500, "An unexpected error occurred"))

            }
        }

        put("/{userId}/{cartId}") {
            try {
                val userId = call.parameters["userId"]?.toInt()
                    ?: throw IllegalArgumentException("Invalid user ID")
                val cartId = call.parameters["cartId"]?.toInt()
                    ?: throw IllegalArgumentException("Invalid cart ID")
                val quantity = call.receive<Map<String, Int>>()["quantity"]
                    ?: throw IllegalArgumentException("Invalid quantity")
                
                val updatedItem = cartService.updateCartQuantity(userId, cartId, quantity)
                call.respond(HttpStatusCode.OK, SuccessResponse("Updated Item successfully", updatedItem))

            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(400, e.message ?: "Failed to update cart item"))

            }
        }

        delete("/{userId}/{cartId}") {
            try {
                val userId = call.parameters["userId"]?.toInt()
                    ?: throw IllegalArgumentException("Invalid user ID")
                val cartId = call.parameters["cartId"]?.toInt()
                    ?: throw IllegalArgumentException("Invalid cart ID")
                
                val deletedItem = cartService.deleteCartItem(userId, cartId)
                call.respond(HttpStatusCode.OK, SuccessResponse("Removed Item successfully", deletedItem))

            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(400, e.message ?: "Failed to delete cart item"))
            }
        }
    }
} 