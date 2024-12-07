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
                val userId = call.parameters["userId"] ?: throw IllegalArgumentException("Invalid user ID")
                val rawBody = call.receiveText()
                val cartItem = Json.decodeFromString<CartItem>(rawBody)

                try {
                    val addedItem = cartService.addToCart(userId, cartItem)
                    call.respond(HttpStatusCode.OK, SuccessResponse("Item added to cart successfully", addedItem))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(400, e.message ?: "Failed to add item to cart"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(500, "An unexpected error occurred"))
            }
        }

        get("/{userId}") {
            try {
                val userId = call.parameters["userId"] ?: throw IllegalArgumentException("Invalid user ID")
                try {
                    val cartItems = cartService.getCartByUserId(userId)
                    call.respond(HttpStatusCode.OK, SuccessResponse("Cart items retrieved successfully", cartItems))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(404, "User not found"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(500, "An unexpected error occurred"))
            }
        }

        put("/{userId}/{id}") {
            try {
                val userId = call.parameters["userId"] ?: throw IllegalArgumentException("Invalid user ID")
                val cartId = call.parameters["id"] ?: throw IllegalArgumentException("Invalid cart ID")
                val rawBody = call.receiveText()
                val cartItem = Json.decodeFromString<CartItem>(rawBody)

                try {
                    val updatedItem = cartService.updateCartQuantity(userId, cartId, cartItem.quantity)
                    call.respond(HttpStatusCode.OK, SuccessResponse("Cart item quantity updated successfully", updatedItem))
                } catch (e: CartItemNotFoundException) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(404, "Cart item not found"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(500, "An unexpected error occurred"))
            }
        }

        delete("/{userId}/{id}") {
            try {
                val userId = call.parameters["userId"] ?: throw IllegalArgumentException("Invalid user ID")
                val cartId = call.parameters["id"] ?: throw IllegalArgumentException("Invalid cart ID")

                try {
                    val deletedItem = cartService.deleteCartItem(userId, cartId)
                    call.respond(HttpStatusCode.OK, SuccessResponse("Item removed from cart successfully", deletedItem))
                } catch (e: CartItemNotFoundException) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(404, "Cart item not found"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(500, "An unexpected error occurred"))
            }
        }
    }
} 