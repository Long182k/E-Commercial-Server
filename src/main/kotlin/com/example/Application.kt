package com.example

import com.example.routings.configureRouting
import io.ktor.server.application.*
import com.example.services.EmailService
import com.example.models.UserService
import com.example.models.OrderService
import com.example.services.CloudinaryService
import com.example.models.CartService
import com.example.models.ProductService

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
    
    val emailService = EmailService(
        apiKey = environment.config.property("mailersend.apiKey").getString(),
        senderEmail = environment.config.property("mailersend.senderEmail").getString()
    )

    val productService = ProductService(dbConnection)
    val cartService = CartService(dbConnection, productService)
    val userService = UserService(dbConnection, emailService)
    val orderService = OrderService(dbConnection, cartService, productService, emailService)
    
    configureSecurity(userService)
    configureRouting(
        userService = userService,
        cloudinaryService = cloudinaryService,
        cartService = cartService,
        productService = productService,
        emailService = emailService
    )
}
