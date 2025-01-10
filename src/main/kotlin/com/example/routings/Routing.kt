package com.example.routings

import com.example.connectToPostgres
import com.example.models.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import java.sql.Connection
import com.example.services.CloudinaryService
import com.example.services.EmailService

fun Application.configureRouting(
    userService: UserService,
    cloudinaryService: CloudinaryService,
    cartService: CartService,
    productService: ProductService,
    emailService: EmailService
) {
    val dbConnection: Connection = connectToPostgres(embedded = false)
    val categoryService = CategoryService(dbConnection)
    val orderService = OrderService(dbConnection, cartService, productService, emailService)

    routing {
        authRouting(userService, cloudinaryService)
        categoryRouting(categoryService)
        productRouting(productService)
        cartRouting(cartService)
        orderRouting(orderService)
    }
}
