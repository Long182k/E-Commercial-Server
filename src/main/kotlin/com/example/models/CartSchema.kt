package com.example.models

import kotlinx.serialization.Serializable
import java.sql.Connection
import kotlinx.coroutines.*

@Serializable
data class CartItem(
    val productId: Int,
    val quantity: Int
)

@Serializable
data class CartItemResponse(
    val id: Int,
    val productId: Int,
    val userId: Int,
    val price: Double,
    val imageUrl: String,
    val quantity: Int,
    val productName: String
)

class CartService(private val connection: Connection, private val productService: ProductService) {
    companion object {
        private const val CREATE_TABLE_CART = """
            CREATE TABLE IF NOT EXISTS CART (
                ID SERIAL PRIMARY KEY,
                USER_ID INT REFERENCES USERS(ID),
                PRODUCT_ID INT REFERENCES PRODUCTS(ID),
                QUANTITY INT NOT NULL,
                CREATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
        """

        private const val INSERT_CART_ITEM = """
            INSERT INTO cart (user_id, product_id, quantity) 
            VALUES (?, ?, ?) RETURNING id
        """

        private const val SELECT_CART_BY_USER = """
            SELECT c.*, p.title as product_name, p.price, p.image as image_url 
            FROM cart c 
            JOIN products p ON c.product_id = p.id 
            WHERE c.user_id = ?
        """

        private const val UPDATE_CART_QUANTITY = """
            UPDATE cart SET quantity = ? 
            WHERE id = ? AND user_id = ?
        """

        private const val DELETE_CART_ITEM = """
            DELETE FROM cart 
            WHERE id = ? AND user_id = ?
        """
    }

    init {
        connection.createStatement().use { statement ->
            statement.execute(CREATE_TABLE_CART)
            println("Cart table created or verified successfully")
        }
    }

    suspend fun addToCart(userId: Int, cartItem: CartItem):  List<CartItemResponse> = withContext(Dispatchers.IO) {
        try {
            val product = productService.getProductById(cartItem.productId)
            
            connection.prepareStatement(INSERT_CART_ITEM).use { statement ->
                statement.setInt(1, userId)
                statement.setInt(2, cartItem.productId)
                statement.setInt(3, cartItem.quantity)
                
                val resultSet = statement.executeQuery()
                resultSet.next()
                val cartId = resultSet.getInt("id")

                val cartItemResponse = CartItemResponse(
                    id = cartId,
                    productId = cartItem.productId,
                    userId = userId,
                    price = product.price,
                    imageUrl = product.image,
                    quantity = cartItem.quantity,
                    productName = product.title
                )

                return@withContext listOf(cartItemResponse)
            }
        } catch (e: Exception) {
            throw Exception("Failed to add item to cart: ${e.message}")
        }
    }

    suspend fun getCartByUserId(userId: Int): List<CartItemResponse> = withContext(Dispatchers.IO) {
        try {
            println("Fetching cart for userId: $userId")
            connection.prepareStatement(SELECT_CART_BY_USER).use { statement ->
                statement.setInt(1, userId)
                val resultSet = statement.executeQuery()
                val cartItems = mutableListOf<CartItemResponse>()

                while (resultSet.next()) {
                    println("Processing row...")
                    try {
                        val item = CartItemResponse(
                            id = resultSet.getInt("id"),
                            productId = resultSet.getInt("product_id"),
                            userId = resultSet.getInt("user_id"),
                            price = resultSet.getDouble("price"),
                            imageUrl = resultSet.getString("image_url"),
                            quantity = resultSet.getInt("quantity"),
                            productName = resultSet.getString("product_name")
                        )
                        cartItems.add(item)
                        println("Added item to cart: $item")
                    } catch (e: Exception) {
                        println("Error processing row: ${e.message}")
                    }
                }
                println("Final cart items count: ${cartItems.size}")
                return@withContext cartItems
            }
        } catch (e: Exception) {
            println("Error fetching cart: ${e.message}")
            throw Exception("Failed to get cart items: ${e.message}")
        }
    }

    suspend fun updateCartQuantity(userId: Int, cartId: Int, quantity: Int): CartItemResponse = withContext(Dispatchers.IO) {
        try {
            connection.prepareStatement(UPDATE_CART_QUANTITY).use { statement ->
                statement.setInt(1, quantity)
                statement.setInt(2, cartId)
                statement.setInt(3, userId)
                
                val rowsUpdated = statement.executeUpdate()
                if (rowsUpdated == 0) throw CartItemNotFoundException()

                // Get updated cart item
                return@withContext getCartByUserId(userId).first { it.id == cartId }
            }
        } catch (e: Exception) {
            when (e) {
                is CartItemNotFoundException -> throw e
                else -> throw Exception("Failed to update cart quantity: ${e.message}")
            }
        }
    }

    suspend fun deleteCartItem(userId: Int, cartId: Int): CartItemResponse = withContext(Dispatchers.IO) {
        try {
            // Get cart item before deletion
            val cartItem = getCartByUserId(userId).firstOrNull { it.id == cartId }
                ?: throw CartItemNotFoundException()

            connection.prepareStatement(DELETE_CART_ITEM).use { statement ->
                statement.setInt(1, cartId)
                statement.setInt(2, userId)
                
                val rowsDeleted = statement.executeUpdate()
                if (rowsDeleted == 0) throw CartItemNotFoundException()

                return@withContext cartItem
            }
        } catch (e: Exception) {
            when (e) {
                is CartItemNotFoundException -> throw e
                else -> throw Exception("Failed to delete cart item: ${e.message}")
            }
        }
    }
}

class CartItemNotFoundException : Exception("Cart item not found")