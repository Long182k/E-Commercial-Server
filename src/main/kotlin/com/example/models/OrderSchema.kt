package com.example.models

import kotlinx.serialization.Serializable
import java.sql.Connection
import kotlinx.coroutines.*
import java.util.UUID
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
    val id: String,
    val orderId: String,
    val productId: String,
    val quantity: Int,
    val price: Double,
    val productName: String
)

@Serializable
data class Order(
    val id: String = UUID.randomUUID().toString(),
    val items: List<OrderItem>,
    val orderDate: String,
    val status: String,
    val totalAmount: Double,
    val userId: String,
    val address: Address
)

class OrderService(
    private val connection: Connection,
    private val cartService: CartService
) {
    companion object {
        private const val CREATE_TABLE_ORDERS = """
            CREATE TABLE IF NOT EXISTS ORDERS (
                ID VARCHAR(36) PRIMARY KEY,
                USER_ID VARCHAR(36) REFERENCES USERS(ID),
                ORDER_DATE TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                STATUS VARCHAR(50),
                TOTAL_AMOUNT DOUBLE PRECISION,
                ADDRESS_LINE TEXT,
                CITY VARCHAR(255),
                STATE VARCHAR(255),
                POSTAL_CODE VARCHAR(20),
                COUNTRY VARCHAR(255)
            );
        """

        private const val CREATE_TABLE_ORDER_ITEMS = """
            CREATE TABLE IF NOT EXISTS ORDER_ITEMS (
                ID VARCHAR(36) PRIMARY KEY,
                ORDER_ID VARCHAR(36) REFERENCES ORDERS(ID),
                PRODUCT_ID VARCHAR(36) REFERENCES PRODUCTS(ID),
                QUANTITY INT,
                PRICE DOUBLE PRECISION,
                PRODUCT_NAME VARCHAR(255)
            );
        """

        private const val INSERT_ORDER = """
            INSERT INTO orders (id, user_id, status, total_amount, address_line, city, state, postal_code, country) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """

        private const val INSERT_ORDER_ITEM = """
            INSERT INTO order_items (id, order_id, product_id, quantity, price, product_name) 
            VALUES (?, ?, ?, ?, ?, ?)
        """

        private const val SELECT_ORDERS_BY_USER = """
            SELECT o.*, oi.* 
            FROM orders o 
            LEFT JOIN order_items oi ON o.id = oi.order_id 
            WHERE o.user_id = ?
            ORDER BY o.order_date DESC
        """
    }

    init {
        connection.createStatement().use { statement ->
            statement.execute(CREATE_TABLE_ORDERS)
            statement.execute(CREATE_TABLE_ORDER_ITEMS)
            println("Orders tables created or verified successfully")
        }
    }

    suspend fun placeOrder(userId: String, address: Address): String = withContext(Dispatchers.IO) {
        try {
            val cartItems = cartService.getCartByUserId(userId)
            if (cartItems.isEmpty()) throw Exception("Cart is empty")

            val orderId = UUID.randomUUID().toString()
            val totalAmount = cartItems.sumOf { it.price * it.quantity }

            connection.autoCommit = false
            try {
                // Insert order
                connection.prepareStatement(INSERT_ORDER).use { statement ->
                    statement.setString(1, orderId)
                    statement.setString(2, userId)
                    statement.setString(3, "PENDING")
                    statement.setDouble(4, totalAmount)
                    statement.setString(5, address.addressLine)
                    statement.setString(6, address.city)
                    statement.setString(7, address.state)
                    statement.setString(8, address.postalCode)
                    statement.setString(9, address.country)
                    statement.executeUpdate()
                }

                // Insert order items
                connection.prepareStatement(INSERT_ORDER_ITEM).use { statement ->
                    for (item in cartItems) {
                        statement.setString(1, UUID.randomUUID().toString())
                        statement.setString(2, orderId)
                        statement.setString(3, item.productId)
                        statement.setInt(4, item.quantity)
                        statement.setDouble(5, item.price)
                        statement.setString(6, item.productName)
                        statement.executeUpdate()
                    }
                }

                // Clear cart items
                cartItems.forEach { cartService.deleteCartItem(userId, it.id) }

                connection.commit()
                return@withContext orderId
            } catch (e: Exception) {
                connection.rollback()
                throw e
            } finally {
                connection.autoCommit = true
            }
        } catch (e: Exception) {
            throw Exception("Failed to place order: ${e.message}")
        }
    }

    suspend fun getOrdersByUserId(userId: String): List<Order> = withContext(Dispatchers.IO) {
        try {
            connection.prepareStatement(SELECT_ORDERS_BY_USER).use { statement ->
                statement.setString(1, userId)
                val resultSet = statement.executeQuery()
                
                val orders = mutableMapOf<String, MutableList<OrderItem>>()
                val orderDetails = mutableMapOf<String, Triple<String, Double, Address>>()

                while (resultSet.next()) {
                    val orderId = resultSet.getString("id")
                    
                    if (!orderDetails.containsKey(orderId)) {
                        orderDetails[orderId] = Triple(
                            resultSet.getTimestamp("order_date").toString(),
                            resultSet.getDouble("total_amount"),
                            Address(
                                addressLine = resultSet.getString("address_line"),
                                city = resultSet.getString("city"),
                                state = resultSet.getString("state"),
                                postalCode = resultSet.getString("postal_code"),
                                country = resultSet.getString("country")
                            )
                        )
                    }

                    val orderItem = OrderItem(
                        id = resultSet.getString("id"),
                        orderId = orderId,
                        productId = resultSet.getString("product_id"),
                        quantity = resultSet.getInt("quantity"),
                        price = resultSet.getDouble("price"),
                        productName = resultSet.getString("product_name")
                    )

                    orders.getOrPut(orderId) { mutableListOf() }.add(orderItem)
                }

                return@withContext orders.map { (orderId, items) ->
                    val (orderDate, totalAmount, address) = orderDetails[orderId]!!
                    Order(
                        id = orderId,
                        items = items,
                        orderDate = orderDate,
                        status = "PENDING",
                        totalAmount = totalAmount,
                        userId = userId,
                        address = address
                    )
                }
            }
        } catch (e: Exception) {
            throw Exception("Failed to get orders: ${e.message}")
        }
    }
} 