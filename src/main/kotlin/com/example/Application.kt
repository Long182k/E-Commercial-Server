package com.example

import com.example.routings.configureRouting
import io.ktor.server.application.*
import com.example.services.EmailService
import com.example.models.UserService
import com.example.services.CloudinaryService

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureSerialization()
    configureMonitoring()
    configureHTTP()
    
    val dbConnection = connectToPostgres(embedded = false)
    val cloudinaryService = CloudinaryService(
        cloudName = environment.config.property("cloudinary.cloudName").getString(),
        apiKey = environment.config.property("cloudinary.apiKey").getString(),
        apiSecret = environment.config.property("cloudinary.apiSecret").getString()
    )
    val emailService = EmailService(environment.config.property("mailersend.apiKey").getString())
    val userService = UserService(dbConnection, emailService)
    
    configureSecurity(userService)
    configureRouting(userService, cloudinaryService)
}
