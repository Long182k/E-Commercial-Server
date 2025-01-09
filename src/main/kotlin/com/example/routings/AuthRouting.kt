package com.example.routings

import com.example.models.*
import com.example.models.UserService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import com.example.services.EmailSendException
import io.ktor.http.content.*
import java.io.File
import com.example.services.CloudinaryService

fun Route.authRouting(
    userService: UserService,
    cloudinaryService: CloudinaryService
) {
    route("/auth") {
        post("/register") {
            try {
                val rawBody = call.receiveText()
                println("rawBody: ${rawBody}")
                val user = Json.decodeFromString<User>(rawBody)
                println("user: ${user}")

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

        post("/change-password") {
            try {
                val rawBody = call.receiveText()
                val request = Json.decodeFromString<ChangePasswordRequest>(rawBody)

                try {
                    userService.changePassword(request)
                    call.respond(HttpStatusCode.OK, SuccessResponse("Password changed successfully", true))
                } catch (e: InvalidCredentialsException) {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse(401, e.message ?:"Current password is incorrect"))
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(400, e.message ?: "Invalid input"))
                } catch (e: UserNotFoundException) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(404, e.message ?: "User not found"))
                }
            } catch (e: Exception) { 
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(500, e.message ?: "An unexpected error occurred"))
            }
        }

        post("/forgot-password") {
            try {
                val rawBody = call.receiveText()
                val request = Json.decodeFromString<ForgotPasswordRequest>(rawBody)

                if (request.email.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(400, "Email is required"))
                    return@post
                }

                try {
                    userService.forgotPassword(request.email)
                    call.respond(HttpStatusCode.OK, SuccessResponse("Password reset email sent", true))
                } catch (e: UserNotFoundException) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(404, e.message ?: "User not found"))
                } catch (e: EmailSendException) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse(500, e.message ?: "Failed to send reset email"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(500, e.message ?: "An unexpected error occurred"))
            }
        }

        put("/edit-profile") {
            try {
                val multipart = call.receiveMultipart()
                var name = ""
                var avatarUrl: String? = null
                var email = ""

                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> {
                            when (part.name) {
                                "name" -> name = part.value
                                "email" -> email = part.value
                            }
                        }
                        is PartData.FileItem -> {
                            if (part.name == "avatar") {
                                try {
                                    val fileName = part.originalFileName ?: "avatar"
                                    val inputStream = part.streamProvider()
                                    avatarUrl = cloudinaryService.uploadImage(inputStream, fileName)
                                    inputStream.close()
                                } catch (e: Exception) {
                                    println("Error uploading file: ${e.message}")
                                    throw e
                                }
                            }
                        }
                        else -> {}
                    }
                    part.dispose()
                }

                if (email.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(400, "Email is required"))
                    return@put
                }
                println("email: $email")
                println("name: $name")
                println("avatarUrl: $avatarUrl")

                val request = EditProfileRequest(name, avatarUrl)
                val updatedProfile = userService.updateProfile(email, request)
                call.respond(HttpStatusCode.OK, SuccessResponse("Profile updated successfully", updatedProfile))

            } catch (e: Exception) {
                println("Error in profile update: ${e.message}")
                when (e) {
                    is IllegalArgumentException -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(400, e.message ?: "Invalid input"))
                    is UserNotFoundException -> call.respond(HttpStatusCode.NotFound, ErrorResponse(404, e.message ?: "User not found"))
                    else -> call.respond(HttpStatusCode.InternalServerError, ErrorResponse(500, "Failed to update profile: ${e.message}"))
                }
            }
        }
    }
}