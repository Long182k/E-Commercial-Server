package com.example.models

import kotlinx.serialization.Serializable
import java.sql.Connection
import kotlinx.coroutines.*
import java.util.UUID
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Serializable
data class Address(
    val id: Int? = null,
    val street: String,
    val city: String,
    val state: String,
    val zipCode: String,
    val country: String
)

@Serializable
data class OrderItem(
    val id: Int? = null,
    val orderId: Int? = null,
    val productId: Int,
    val quantity: Int,
    val price: Double
)

@Serializable
data class Order(
    val id: Int? = null,
    val userId: Int,
    val items: List<OrderItem>,
    val total: Double,
    val status: String,
    val address: Address,
    val createdAt: String? = null
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
                TOTAL DECIMAL(10,2) NOT NULL,
                STATUS VARCHAR(50) NOT NULL,
                CREATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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
                STREET VARCHAR(255) NOT NULL,
                CITY VARCHAR(255) NOT NULL,
                STATE VARCHAR(255) NOT NULL,
                ZIP_CODE VARCHAR(20) NOT NULL,
                COUNTRY VARCHAR(255) NOT NULL
            );
        """

        private const val INSERT_ORDER = """
            INSERT INTO orders (user_id, total, status) 
            VALUES (?, ?, ?) RETURNING id
        """

        private const val INSERT_ORDER_ITEM = """
            INSERT INTO order_items (order_id, product_id, quantity, price) 
            VALUES (?, ?, ?, ?)
        """

        private const val INSERT_ADDRESS = """
            INSERT INTO addresses (order_id, street, city, state, zip_code, country) 
            VALUES (?, ?, ?, ?, ?, ?)
        """

        private const val SELECT_ORDERS_BY_USER = """
            SELECT o.*, a.*, oi.*
            FROM orders o
            LEFT JOIN addresses a ON o.id = a.order_id
            LEFT JOIN order_items oi ON o.id = oi.order_id
            WHERE o.user_id = ?
            ORDER BY o.created_at DESC
        """
    }

    init {
        connection.createStatement().use { statement ->
            statement.execute(CREATE_TABLE_ORDERS)
            statement.execute(CREATE_TABLE_ORDER_ITEMS)
            statement.execute(CREATE_TABLE_ADDRESSES)
            println("Orders tables created or verified successfully")
        }
    }

    suspend fun placeOrder(userId: Int, address: Address): Order = withContext(Dispatchers.IO) {
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
                insertAddress(orderId, address)

                // Insert order items
                val orderItems = cartItems.map { cartItem ->
                    insertOrderItem(orderId, cartItem)
                }

                return@withContext Order(
                    id = orderId,
                    userId = userId,
                    items = orderItems,
                    total = total,
                    status = "PENDING",
                    address = address,
                    createdAt = resultSet.getString("created_at")
                )
            }
        } catch (e: Exception) {
            throw Exception("Failed to place order: ${e.message}")
        }
    }

    private suspend fun insertAddress(orderId: Int, address: Address) {
        connection.prepareStatement(INSERT_ADDRESS).use { statement ->
            statement.setInt(1, orderId)
            statement.setString(2, address.street)
            statement.setString(3, address.city)
            statement.setString(4, address.state)
            statement.setString(5, address.zipCode)
            statement.setString(6, address.country)
            statement.executeUpdate()
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

    suspend fun getOrdersByUserId(userId: Int): List<Order> = withContext(Dispatchers.IO) {
        try {
            connection.prepareStatement(SELECT_ORDERS_BY_USER).use { statement ->
                statement.setInt(1, userId)
                val resultSet = statement.executeQuery()
                
                val orders = mutableMapOf<Int, Order>()
                
                while (resultSet.next()) {
                    val orderId = resultSet.getInt("id")
                    
                    if (!orders.containsKey(orderId)) {
                        orders[orderId] = Order(
                            id = orderId,
                            userId = resultSet.getInt("user_id"),
                            total = resultSet.getDouble("total"),
                            status = resultSet.getString("status"),
                            items = mutableListOf(),
                            address = Address(
                                street = resultSet.getString("street"),
                                city = resultSet.getString("city"),
                                state = resultSet.getString("state"),
                                zipCode = resultSet.getString("zip_code"),
                                country = resultSet.getString("country")
                            ),
                            createdAt = resultSet.getString("created_at")
                        )
                    }
                    
                    // Add order item
                    val orderItemId = resultSet.getInt("id")
                    if (orderItemId > 0) {
                        val orderItem = OrderItem(
                            id = orderItemId,
                            orderId = orderId,
                            productId = resultSet.getInt("product_id"),
                            quantity = resultSet.getInt("quantity"),
                            price = resultSet.getDouble("price")
                        )
                        (orders[orderId]?.items as MutableList).add(orderItem)
                    }
                }
                
                return@withContext orders.values.toList()
            }
        } catch (e: Exception) {
            throw Exception("Failed to get orders: ${e.message}")
        }
    }
} 