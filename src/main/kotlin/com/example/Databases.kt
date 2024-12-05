package com.example

import com.example.models.ErrorResponse
import com.example.models.InvalidCredentialsException
import com.example.models.LoginRequest
import com.example.models.SuccessResponse
import com.example.models.User
import com.example.models.UserExistsException
import com.example.models.UserService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.sql.DriverManager

fun Application.configureDatabases() {
    val dbConnection: Connection = connectToPostgres(embedded = false)
    val userService = UserService(dbConnection)

    routing {
        post("/auth/register") {
            log.info("Handling POST request to /auth/register")

            try {
                val rawBody = call.receiveText()
                log.info("Raw body: $rawBody")

                val user = Json.decodeFromString<User>(rawBody)
                log.info("Deserialized User: $user")

                // Validate user input
                if (user.email.isBlank() || user.password.isBlank() || user.name.isBlank()) {
                    log.warn("Validation failed: Missing required fields")
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(400, "Missing required fields"))
                    return@post
                }

                try {
                    val createdUser = userService.register(user)
                    call.respond(HttpStatusCode.Created, SuccessResponse("User registered successfully", createdUser))
                } catch (e: UserExistsException) {
                    log.warn("User registration failed: Email or username already exists")
                    call.respond(HttpStatusCode.Conflict, ErrorResponse(409, "Username or email already exists"))
                }
            } catch (e: Exception) {
                log.error("An error occurred while processing the registration request", e)
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(500, "An unexpected error occurred"))
            }
        }

        // In Database.kt, modify the login route:
        post("/auth/login") {
            try {
                val rawBody = call.receiveText()
                log.info("Raw body: $rawBody")

                val credentials = Json.decodeFromString<LoginRequest>(rawBody)
                log.info("Deserialized credentials: $credentials")

                // Test database connection
                userService.testConnection()

                // Validate the request body
                if (credentials.email.isBlank() || credentials.password.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(400, "Missing email or password"))
                    return@post
                }

                try {
                    val user = userService.login(credentials)
                    call.respond(HttpStatusCode.OK, SuccessResponse("Login successful", user))
                } catch (e: InvalidCredentialsException) {
                    log.warn("Invalid credentials for email: ${credentials.email}")
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse(401, "Invalid credentials"))
                } catch (e: Exception) {
                    log.error("Database error during login: ${e.message}", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(500, "Database error: ${e.message}")
                    )
                }
            } catch (e: Exception) {
                log.error("Login error: ${e.message}", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(500, "An unexpected error occurred: ${e.message}")
                )
            }
        }

    }
}

fun Application.connectToPostgres(embedded: Boolean): Connection {
    Class.forName("org.postgresql.Driver")
    return if (embedded) {
        log.info("Using embedded H2 database for testing")
        try {
            val connection = DriverManager.getConnection("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "root", "")
            log.info("Successfully connected to the embedded H2 database.")
            connection
        } catch (e: Exception) {
            log.error("Failed to connect to the embedded H2 database: ${e.message}", e)
            throw e
        }
    } else {
        val serverName = environment.config.property("postgres.serverName").getString()
        val portNumber = environment.config.property("postgres.portNumber").getString()
        val databaseName = environment.config.property("postgres.databaseName").getString()
        val user = environment.config.property("postgres.user").getString()
        val password = environment.config.property("postgres.password").getString()

        val url = "jdbc:postgresql://$serverName:$portNumber/$databaseName"
        log.info("Connecting to Postgres database at $url")

        try {
            val connection = DriverManager.getConnection(url, user, password)
            log.info("Successfully connected to the Postgres database.")
            connection
        } catch (e: Exception) {
            log.error("Failed to connect to the Postgres database: ${e.message}", e)
            throw e
        }
    }
}
