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

        private const val CHECK_EXISTING_CART_ITEM = """
            SELECT id, quantity FROM cart 
            WHERE user_id = ? AND product_id = ?
        """

        private const val UPDATE_EXISTING_CART_ITEM = """
            UPDATE cart SET quantity = quantity + ? 
            WHERE user_id = ? AND product_id = ? 
            RETURNING id
        """
    }

    init {
        connection.createStatement().use { statement ->
            statement.execute(CREATE_TABLE_CART)
        }
    }

    suspend fun addToCart(userId: Int, cartItem: CartItem): List<CartItemResponse> = withContext(Dispatchers.IO) {
        try {
            val product = productService.getProductById(cartItem.productId)
            
            // Check if the product already exists in the user's cart
            connection.prepareStatement(CHECK_EXISTING_CART_ITEM).use { checkStatement ->
                checkStatement.setInt(1, userId)
                checkStatement.setInt(2, cartItem.productId)
                val checkResult = checkStatement.executeQuery()
                
                val cartId = if (checkResult.next()) {
                    // Product exists in cart, update quantity
                    connection.prepareStatement(UPDATE_EXISTING_CART_ITEM).use { updateStatement ->
                        updateStatement.setInt(1, cartItem.quantity)
                        updateStatement.setInt(2, userId)
                        updateStatement.setInt(3, cartItem.productId)
                        val resultSet = updateStatement.executeQuery()
                        resultSet.next()
                        resultSet.getInt("id")
                    }
                } else {
                    // Product doesn't exist in cart, insert new item
                    connection.prepareStatement(INSERT_CART_ITEM).use { insertStatement ->
                        insertStatement.setInt(1, userId)
                        insertStatement.setInt(2, cartItem.productId)
                        insertStatement.setInt(3, cartItem.quantity)
                        val resultSet = insertStatement.executeQuery()
                        resultSet.next()
                        resultSet.getInt("id")
                    }
                }

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
            connection.prepareStatement(SELECT_CART_BY_USER).use { statement ->
                statement.setInt(1, userId)
                val resultSet = statement.executeQuery()
                val cartItems = mutableListOf<CartItemResponse>()

                while (resultSet.next()) {
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
                    } catch (e: Exception) {
                        println("Error processing row: ${e.message}")
                    }
                }
                return@withContext cartItems
            }
        } catch (e: Exception) {
            throw Exception("Failed to get cart items: ${e.message}")
        }
    }

    suspend fun updateCartQuantity(userId: Int, cartId: Int, quantity: Int): List<CartItemResponse> = withContext(Dispatchers.IO) {
        try {
            connection.prepareStatement(UPDATE_CART_QUANTITY).use { statement ->
                statement.setInt(1, quantity)
                statement.setInt(2, cartId)
                statement.setInt(3, userId)
                
                val rowsUpdated = statement.executeUpdate()
                if (rowsUpdated == 0) throw CartItemNotFoundException()

                val updatedCartItem = getCartByUserId(userId).first { it.id == cartId }


                // Get updated cart item
                return@withContext listOf(updatedCartItem)
            }
        } catch (e: Exception) {
            when (e) {
                is CartItemNotFoundException -> throw e
                else -> throw Exception("Failed to update cart quantity: ${e.message}")
            }
        }
    }

    suspend fun deleteCartItem(userId: Int, cartId: Int): List<CartItemResponse> = withContext(Dispatchers.IO) {
        try {
            // Get cart item before deletion
            val cartItem = getCartByUserId(userId).firstOrNull { it.id == cartId }
                ?: throw CartItemNotFoundException()

            connection.prepareStatement(DELETE_CART_ITEM).use { statement ->
                statement.setInt(1, cartId)
                statement.setInt(2, userId)
                
                val rowsDeleted = statement.executeUpdate()
                if (rowsDeleted == 0) throw CartItemNotFoundException()

                return@withContext listOf(cartItem)
            }
        } catch (e: Exception) {
            when (e) {
                is CartItemNotFoundException -> throw e
                else -> throw Exception("Failed to delete cart item: ${e.message}")
            }
        }
    }

    suspend fun clearCart(userId: Int) {
        connection.prepareStatement("DELETE FROM cart WHERE user_id = ?").use { statement ->
            statement.setInt(1, userId)
            statement.executeUpdate()
        }
    }
}

class CartItemNotFoundException : Exception("Cart item not found")