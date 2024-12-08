package com.example.routings

import com.example.models.*
import com.example.models.UserService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

fun Route.authRouting(userService: UserService) {
    route("/auth") {
        post("/register") {
            try {
                val rawBody = call.receiveText()
                val user = Json.decodeFromString<User>(rawBody)

                if (user.email.isBlank() || user.password.isBlank() || user.name.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(400, "Missing required fields"))
                    return@post
                }

                try {
                    val createdUser = userService.register(user)
                    call.respond(HttpStatusCode.Created, SuccessResponse<UserResponse>("User registered successfully", createdUser))
                } catch (e: UserExistsException) {
                    call.respond(HttpStatusCode.Conflict, ErrorResponse(409, "Username or email already exists"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(500, "An unexpected error occurred"))
            }
        }

        post("/login") {
            try {
                val rawBody = call.receiveText()
                val credentials = Json.decodeFromString<LoginRequest>(rawBody)

                if (credentials.email.isBlank() || credentials.password.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(400, "Missing email or password"))
                    return@post
                }

                try {
                    val user = userService.login(credentials)
                    call.respond(HttpStatusCode.OK, SuccessResponse("Login successful", user))
                } catch (e: InvalidCredentialsException) {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse(401, "Invalid credentials"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(500, "An unexpected error occurred"))
            }
        }
    }
}