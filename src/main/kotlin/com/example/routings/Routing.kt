package com.example.routings

import com.example.connectToPostgres
import com.example.models.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import java.sql.Connection
import com.example.services.CloudinaryService

fun Application.configureRouting(
    userService: UserService,
    cloudinaryService: CloudinaryService
) {
    val dbConnection: Connection = connectToPostgres(embedded = false)
    val categoryService = CategoryService(dbConnection)
    val productService = ProductService(dbConnection)
    val cartService = CartService(dbConnection, productService)
    val orderService = OrderService(dbConnection, cartService, productService)

    routing {
        authRouting(userService, cloudinaryService)
        categoryRouting(categoryService)
        productRouting(productService)
        cartRouting(cartService)
        orderRouting(orderService)
    }
}
