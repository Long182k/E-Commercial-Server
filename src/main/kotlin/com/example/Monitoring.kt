package com.example

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.sql.Connection
import java.sql.DriverManager
import org.slf4j.event.*

fun Application.configureMonitoring() {
    install(CallLogging) {
        level = Level.TRACE
        format { call ->
            "HTTP ${call.request.httpMethod.value} ${call.request.path()}"
        }
    }

}
