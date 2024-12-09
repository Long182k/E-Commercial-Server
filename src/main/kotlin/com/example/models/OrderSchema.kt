package com.example.models

import kotlinx.serialization.Serializable
import java.sql.Connection
import kotlinx.coroutines.*
import java.util.UUID
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.sql.Statement

@Serializable
data class Address(
    val addressLine: String,
    val city: String,
    val state: String,
    val postalCode: String,
    val country: String
)

@Serializable
data class OrderItem(
    val orderId: Int,
    val productId: Int,
    val quantity: Int,
    val price: Double
)

@Serializable
data class Order(
    val userId: Int,
    val items: List<OrderItem>,
    val totalAmount: Double,
    val status: String,
    val address: Address,
    val orderDate: String? = null
)

@Serializable
data class OrderResponse(
    val id: Int,
    val items: List<OrderItem>,
    val orderDate: String? = null,
    val status: String,
    val totalAmount: Double,
    val userId: Int,
    val address: Address,
)

class OrderService(
    private val connection: Connection,
    private val cartService: CartService
) {
    companion object {
        private const val CREATE_TABLE_ORDERS = """
            CREATE TABLE IF NOT EXISTS ORDERS (
                ID SERIAL PRIMARY KEY,
                USER_ID INT REFERENCES USERS(ID),
                TOTAL_AMOUNT DECIMAL(10,2) NOT NULL,
                STATUS VARCHAR(50) NOT NULL,
                ORDER_DATE TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
        """

        private const val CREATE_TABLE_ORDER_ITEMS = """
            CREATE TABLE IF NOT EXISTS ORDER_ITEMS (
                ID SERIAL PRIMARY KEY,
                ORDER_ID INT REFERENCES ORDERS(ID),
                PRODUCT_ID INT REFERENCES PRODUCTS(ID),
                QUANTITY INT NOT NULL,
                PRICE DECIMAL(10,2) NOT NULL
            );
        """

        private const val CREATE_TABLE_ADDRESSES = """
            CREATE TABLE IF NOT EXISTS ADDRESSES (
                ID SERIAL PRIMARY KEY,
                ORDER_ID INT REFERENCES ORDERS(ID),
                ADDRESS_LINE VARCHAR(255) NOT NULL,
                CITY VARCHAR(255) NOT NULL,
                STATE VARCHAR(255) NOT NULL,
                POSTAL_CODE VARCHAR(20) NOT NULL,
                COUNTRY VARCHAR(255) NOT NULL
            );
        """

        private const val INSERT_ORDER = """
            INSERT INTO orders (user_id, total_amount, status, order_date) 
            VALUES (?, ?, ?, CURRENT_TIMESTAMP) 
            RETURNING id
        """

        private const val INSERT_ORDER_ITEM = """
            INSERT INTO order_items (order_id, product_id, quantity, price) 
            VALUES (?, ?, ?, ?)
        """

        private const val INSERT_ADDRESS = """
            INSERT INTO addresses (order_id, address_line, city, state, postal_code, country) 
            VALUES (?, ?, ?, ?, ?, ?) 
            RETURNING id
        """

        private const val SELECT_ORDERS_BY_USER = """
            SELECT 
                o.id, o.user_id, o.total_amount, o.status, o.order_date,
                a.id as address_id, a.address_line, a.city, a.state, a.postal_code, a.country,
                oi.product_id, oi.quantity, oi.price
            FROM orders o
            LEFT JOIN addresses a ON o.id = a.order_id
            LEFT JOIN order_items oi ON o.id = oi.order_id
            WHERE o.user_id = ?
            ORDER BY o.order_date DESC
        """
    }

    init {
        connection.createStatement().use { statement ->
            statement.execute(CREATE_TABLE_ORDERS)
            statement.execute(CREATE_TABLE_ORDER_ITEMS)
            statement.execute(CREATE_TABLE_ADDRESSES)
        }
    }

    suspend fun placeOrder(userId: Int, address: Address): Int = withContext(Dispatchers.IO) {
        try {
            // Get cart items
            val cartItems = cartService.getCartByUserId(userId)
            if (cartItems.isEmpty()) {
                throw Exception("Cart is empty")
            }

            // Calculate total
            val total = cartItems.sumOf { it.price * it.quantity }

            connection.prepareStatement(INSERT_ORDER).use { statement ->
                statement.setInt(1, userId)
                statement.setDouble(2, total)
                statement.setString(3, "PENDING")

                val resultSet = statement.executeQuery()
                resultSet.next()
                val orderId = resultSet.getInt("id")

                // Insert address
                createAddress(orderId, address)

                // Insert order items
                val orderItems = cartItems.map { cartItem ->
                    insertOrderItem(orderId, cartItem)
                }

                return@withContext orderId

            }
        } catch (e: Exception) {
            throw Exception("Failed to place order: ${e.message}")
        }
    }

    private suspend fun createAddress(orderId: Int, address: Address): Int = withContext(Dispatchers.IO) {
        try {
            connection.prepareStatement(INSERT_ADDRESS).use { statement ->
                statement.setInt(1, orderId)
                statement.setString(2, address.addressLine)
                statement.setString(3, address.city)
                statement.setString(4, address.state)
                statement.setString(5, address.postalCode)
                statement.setString(6, address.country)
                
                val resultSet = statement.executeQuery()
                if (resultSet.next()) {
                    return@withContext resultSet.getInt("id")
                } else {
                    throw Exception("Failed to create address")
                }
            }
        } catch (e: Exception) {
            throw Exception("Failed to create address: ${e.message}")
        }
    }

    private suspend fun insertOrderItem(orderId: Int, cartItem: CartItemResponse): OrderItem {
        connection.prepareStatement(INSERT_ORDER_ITEM).use { statement ->
            statement.setInt(1, orderId)
            statement.setInt(2, cartItem.productId)
            statement.setInt(3, cartItem.quantity)
            statement.setDouble(4, cartItem.price)
            statement.executeUpdate()

            return OrderItem(
                orderId = orderId,
                productId = cartItem.productId,
                quantity = cartItem.quantity,
                price = cartItem.price
            )
        }
    }

    suspend fun getOrdersByUserId(userId: Int): List<OrderResponse> = withContext(Dispatchers.IO) {
        try {
            connection.prepareStatement(SELECT_ORDERS_BY_USER).use { statement ->
                statement.setInt(1, userId)
                val resultSet = statement.executeQuery()
                val orders = mutableMapOf<Int, OrderResponse>()

                while (resultSet.next()) {
                    val orderId = resultSet.getInt("id")
                    
                    // Check if address exists by checking address_id
                    val addressId = resultSet.getInt("address_id")
                    val hasAddress = !resultSet.wasNull()

                    if (!orders.containsKey(orderId)) {
                        // Create address only if it exists
                        val address = if (hasAddress) {
                            Address(
                                addressLine = resultSet.getString("address_line"),
                                city = resultSet.getString("city"),
                                state = resultSet.getString("state"),
                                postalCode = resultSet.getString("postal_code"),
                                country = resultSet.getString("country")
                            )
                        } else {
                            // Provide a default address or null if your OrderResponse allows it
                            Address(
                                addressLine = "",
                                city = "",
                                state = "",
                                postalCode = "",
                                country = ""
                            )
                        }

                        orders[orderId] = OrderResponse(
                            id = orderId,
                            userId = resultSet.getInt("user_id"),
                            totalAmount = resultSet.getDouble("total_amount"),
                            status = resultSet.getString("status") ?: "UNKNOWN",
                            items = mutableListOf(),
                            address = address,
                            orderDate = resultSet.getString("order_date")
                        )
                    }
                    
                    // Add order item if it exists
                    val productId = resultSet.getInt("product_id")
                    if (!resultSet.wasNull()) {
                        val orderItem = OrderItem(
                            orderId = orderId,
                            productId = productId,
                            quantity = resultSet.getInt("quantity"),
                            price = resultSet.getDouble("price")
                        )
                        (orders[orderId]?.items as MutableList).add(orderItem)
                    }
                }

                return@withContext orders.values.toList()
            }
        } catch (e: Exception) {
            println("Error fetching orders: ${e.message}")
            e.printStackTrace()
            throw Exception("Failed to get orders: ${e.message}")
        }
    }
} 