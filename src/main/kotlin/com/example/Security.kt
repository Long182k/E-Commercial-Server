package com.example

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import com.example.models.LoginRequest
import com.example.models.UserService

fun Application.configureSecurity() {
    val userService = UserService(connectToPostgres(embedded = false))  // Assume UserService is properly set up

    authentication {
        basic("auth-basic") {
            realm = "Ktor Server"
            validate { credentials ->
                try {
                    userService.login(LoginRequest(credentials.name, credentials.password))
                        ?.let { UserIdPrincipal(credentials.name) }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    routing {
        authenticate("auth-basic") {
            get("/protected/route") {
                val principal = call.principal<UserIdPrincipal>()
                call.respondText("Hello, ${principal?.name}")
            }
        }
    }
}
