package com.example.routings

import com.example.models.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

fun Route.categoryRouting(categoryService: CategoryService) {
    route("/categories") {
        post {
            try {
                val rawBody = call.receiveText()
                val categories = Json.decodeFromString<List<Category>>(rawBody)

                try {
                    val createdCategories = categoryService.addCategories(categories)
                    call.respond(HttpStatusCode.Created, SuccessResponse("Categories added successfully", createdCategories))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(400, e.message ?: "Failed to add categories"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(500, "An unexpected error occurred"))
            }
        }

        get {
            try {
                val categories = categoryService.getCategories()
                if (categories.isEmpty()) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(404, "No categories found"))
                } else {
                    call.respond(HttpStatusCode.OK, SuccessResponse("Categories retrieved successfully", categories))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(500, "An unexpected error occurred"))
            }
        }

        get("{id}") {
            try {
                val id = call.parameters["id"]?.toIntOrNull() ?: throw IllegalArgumentException("Invalid ID")
                try {
                    val category = categoryService.getCategoryById(id)
                    call.respond(HttpStatusCode.OK, SuccessResponse("Category retrieved successfully", category))
                } catch (e: CategoryNotFoundException) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(404, "Category not found"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(500, "An unexpected error occurred"))
            }
        }

        put("{id}") {
            try {
                val id = call.parameters["id"]?.toIntOrNull() ?: throw IllegalArgumentException("Invalid ID")
                val rawBody = call.receiveText()
                val category = Json.decodeFromString<Category>(rawBody)

                try {
                    val updatedCategory = categoryService.updateCategory(id, category)
                    call.respond(HttpStatusCode.OK, SuccessResponse("Category updated successfully", updatedCategory))
                } catch (e: CategoryNotFoundException) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(404, "Category not found"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(500, "An unexpected error occurred"))
            }
        }

        delete("{id}") {
            try {
                val id = call.parameters["id"]?.toIntOrNull() ?: throw IllegalArgumentException("Invalid ID")
                try {
                    categoryService.deleteCategory(id)
                    call.respond(HttpStatusCode.OK, SuccessResponse<Unit>("Category deleted successfully", Unit))
                } catch (e: CategoryNotFoundException) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(404, "Category not found"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(500, "An unexpected error occurred"))
            }
        }
    }
} 