package com.example.routings

import com.example.models.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

fun Route.productRouting(productService: ProductService) {
    route("/products") {
        post {
            try {
                val rawBody = call.receiveText()
                val products = Json.decodeFromString<List<Product>>(rawBody)

                try {
                    val createdProducts = productService.addProducts(products)
                    call.respond(HttpStatusCode.Created, SuccessResponse("Products added successfully", createdProducts))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(400, e.message ?: "Failed to add products"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(500, "An unexpected error occurred"))
            }
        }

        get {
            try {
                val products = productService.getProducts()
                if (products.isEmpty()) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(404, "No products found"))
                } else {
                    call.respond(HttpStatusCode.OK, SuccessResponse("Products retrieved successfully", products))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(500, "An unexpected error occurred"))
            }
        }

        get("{id}") {
            try {
                val id = call.parameters["id"]?.toInt() 
                    ?: throw IllegalArgumentException("Invalid ID")
                try {
                    val product = productService.getProductById(id)
                    call.respond(HttpStatusCode.OK, SuccessResponse("Product retrieved successfully", product))
                } catch (e: ProductNotFoundException) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(404, "Product not found"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(500, "An unexpected error occurred"))
            }
        }

        put("{id}") {
            try {
                val id = call.parameters["id"]?.toInt()
                    ?: throw IllegalArgumentException("Invalid ID")
                val rawBody = call.receiveText()
                val product = Json.decodeFromString<Product>(rawBody)

                try {
                    val updatedProduct = productService.updateProduct(id, product)
                    call.respond(HttpStatusCode.OK, SuccessResponse("Product updated successfully", updatedProduct))
                } catch (e: ProductNotFoundException) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(404, "Product not found"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(500, "An unexpected error occurred"))
            }
        }

        delete("{id}") {
            try {
                val id = call.parameters["id"]?.toInt()
                    ?: throw IllegalArgumentException("Invalid ID")
                try {
                    productService.deleteProduct(id)
                    call.respond(HttpStatusCode.OK, SuccessResponse<Unit>("Product deleted successfully", Unit))
                } catch (e: ProductNotFoundException) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(404, "Product not found"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(500, "An unexpected error occurred"))
            }
        }

        get("/category/{categoryId}") {
            try {
                val categoryId = call.parameters["categoryId"]?.toInt()
                    ?: throw IllegalArgumentException("Invalid category ID")
                val products = productService.getProductsByCategory(categoryId)
                call.respond(
                    HttpStatusCode.OK,
                    mapOf("msg" to "Products retrieved successfully", "data" to products)
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to (e.message ?: "Failed to get products"))
                )
            }
        }
    }
} 