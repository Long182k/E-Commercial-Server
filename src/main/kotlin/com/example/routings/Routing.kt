package com.example.routings

import com.example.connectToPostgres
import com.example.models.UserService
import io.ktor.server.application.*
import io.ktor.server.routing.*
import java.sql.Connection

fun Application.configureRouting() {
    val dbConnection: Connection = connectToPostgres(embedded = false)
    val userService = UserService(dbConnection)

    routing {
        authRouting(userService) // Import and use AuthRouting
    }
}
