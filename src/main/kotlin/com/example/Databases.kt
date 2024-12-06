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
