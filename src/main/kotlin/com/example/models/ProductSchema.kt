package com.example.models

import kotlinx.serialization.Serializable
import java.sql.Connection
import java.sql.Statement
import kotlinx.coroutines.*

@Serializable
data class Product(
    val title: String,
    val price: Double,
    val description: String,
    val categoryId: Int,
    val image: String
)

@Serializable
data class ProductResponse(
    val id: Int,
    val title: String,
    val description: String,
    val price: Double,
    val image: String,
    val categoryId: Int,
    val sellNumber?: Int
)

class ProductService(private val connection: Connection) {
    companion object {
        private const val CREATE_TABLE_PRODUCTS = """
            CREATE TABLE IF NOT EXISTS PRODUCTS (
                ID SERIAL PRIMARY KEY,
                TITLE VARCHAR(255) NOT NULL,
                PRICE DECIMAL(10,2) NOT NULL,
                DESCRIPTION TEXT,
                CATEGORY_ID INT REFERENCES CATEGORIES(ID),
                IMAGE VARCHAR(255),
                SELL_NUMBER INT DEFAULT 0
            );
        """

        private const val INSERT_PRODUCT = """
            INSERT INTO products (title, price, description, category_id, image) 
            VALUES (?, ?, ?, ?, ?) RETURNING id
        """

        private const val SELECT_PRODUCT_BY_ID = """
            SELECT * FROM products WHERE id = ?
        """

        private const val SELECT_PRODUCTS_BY_CATEGORY = """
            SELECT * FROM products 
            WHERE category_id = ?
        """

        private const val UPDATE_PRODUCT = """
            UPDATE products 
            SET title = ?, price = ?, description = ?, category_id = ?, image = ?
            WHERE id = ?
            RETURNING *
        """

        private const val DELETE_PRODUCT = """
            DELETE FROM products 
            WHERE id = ?
            RETURNING *
        """

        private const val SELECT_ALL_PRODUCTS = """
            SELECT * FROM products
        """

        private const val SELECT_BEST_SELLERS = """
            SELECT * FROM products 
            ORDER BY sell_number DESC 
            LIMIT 10
        """

        private const val UPDATE_PRODUCT_SELL_NUMBER = """
            UPDATE products 
            SET sell_number = sell_number + ? 
            WHERE id = ?
        """
    }

    init {
        // connection.createStatement().use { statement ->
            // statement.execute(CREATE_TABLE_PRODUCTS)
            
        // }
    }

    suspend fun addProducts(products: List<Product>): List<ProductResponse> = withContext(Dispatchers.IO) {
        try {
            val responses = mutableListOf<ProductResponse>()
            
            for (product in products) {
                connection.prepareStatement(INSERT_PRODUCT).use { statement ->
                    statement.setString(1, product.title)
                    statement.setDouble(2, product.price)
                    statement.setString(3, product.description)
                    statement.setInt(4, product.categoryId)
                    statement.setString(5, product.image)
                    
                    val resultSet = statement.executeQuery()
                    if (resultSet.next()) {
                        val id = resultSet.getInt("id")
                        responses.add(
                            ProductResponse(
                                id = id,
                                title = product.title,
                                description = product.description,
                                price = product.price,
                                image = product.image,
                                categoryId = product.categoryId,
                                sellNumber = 0
                            )
                        )
                    }
                }
            }
            return@withContext responses
        } catch (e: Exception) {
            throw Exception("Failed to add products: ${e.message}")
        }
    }

    suspend fun getProducts(): List<ProductResponse> = withContext(Dispatchers.IO) {
        try {
            connection.prepareStatement(SELECT_ALL_PRODUCTS).use { statement ->
                val resultSet = statement.executeQuery()
                val products = mutableListOf<ProductResponse>()

                while (resultSet.next()) {
                    products.add(
                        ProductResponse(
                            id = resultSet.getInt("id").toInt(),
                            title = resultSet.getString("title"),
                            description = resultSet.getString("description"),
                            price = resultSet.getDouble("price"),
                            image = resultSet.getString("image"),
                            categoryId = resultSet.getInt("category_id"),
                            sellNumber = resultSet.getInt("sell_number")
                        )
                    )
                }
                return@withContext products
            }
        } catch (e: Exception) {
            throw Exception("Failed to get products: ${e.message}")
        }
    }

    suspend fun getProductById(id: Int): Product = withContext(Dispatchers.IO) {
        try {
            connection.prepareStatement(SELECT_PRODUCT_BY_ID).use { statement ->
                statement.setInt(1, id)
                val resultSet = statement.executeQuery()

                if (resultSet.next()) {
                    return@withContext Product(
                        title = resultSet.getString("title"),
                        price = resultSet.getDouble("price"),
                        description = resultSet.getString("description"),
                        categoryId = resultSet.getInt("category_id"),
                        image = resultSet.getString("image")
                    )
                } else {
                    throw ProductNotFoundException()
                }
            }
        } catch (e: Exception) {
            when (e) {
                is ProductNotFoundException -> throw e
                else -> throw Exception("Failed to get product: ${e.message}")
            }
        }
    }

    suspend fun getProductsByCategory(categoryId: Int): List<Product> = withContext(Dispatchers.IO) {
        try {
            connection.prepareStatement(SELECT_PRODUCTS_BY_CATEGORY).use { statement ->
                statement.setInt(1, categoryId)
                val resultSet = statement.executeQuery()
                val products = mutableListOf<Product>()

                while (resultSet.next()) {
                    products.add(
                        Product(
                            title = resultSet.getString("title"),
                            price = resultSet.getDouble("price"),
                            description = resultSet.getString("description"),
                            categoryId = resultSet.getInt("category_id"),
                            image = resultSet.getString("image")
                        )
                    )
                }
                return@withContext products
            }
        } catch (e: Exception) {
            throw Exception("Failed to get products: ${e.message}")
        }
    }

    suspend fun updateProduct(id: Int, product: Product): ProductResponse = withContext(Dispatchers.IO) {
        try {
            connection.prepareStatement(UPDATE_PRODUCT).use { statement ->
                statement.setString(1, product.title)
                statement.setDouble(2, product.price)
                statement.setString(3, product.description)
                statement.setInt(4, product.categoryId)
                statement.setString(5, product.image)
                statement.setInt(6, id)

                val resultSet = statement.executeQuery()
                if (resultSet.next()) {
                    return@withContext ProductResponse(
                        id = resultSet.getInt("id").toInt(),
                        title = resultSet.getString("title"),
                        description = resultSet.getString("description"),
                        price = resultSet.getDouble("price"),
                        image = resultSet.getString("image"),
                        categoryId = resultSet.getInt("category_id").toInt(),
                        sellNumber = resultSet.getInt("sell_number")
                    )
                } else {
                    throw ProductNotFoundException()
                }
            }
        } catch (e: Exception) {
            when (e) {
                is ProductNotFoundException -> throw e
                else -> throw Exception("Failed to update product: ${e.message}")
            }
        }
    }

    suspend fun deleteProduct(id: Int) = withContext(Dispatchers.IO) {
        try {
            connection.prepareStatement(DELETE_PRODUCT).use { statement ->
                statement.setInt(1, id)
                val resultSet = statement.executeQuery()

                if (!resultSet.next()) {
                    throw ProductNotFoundException()
                }
            }
        } catch (e: Exception) {
            when (e) {
                is ProductNotFoundException -> throw e
                else -> throw Exception("Failed to delete product: ${e.message}")
            }
        }
    }

    suspend fun getBestSellers(): List<ProductResponse> = withContext(Dispatchers.IO) {
        try {
            connection.prepareStatement(SELECT_BEST_SELLERS).use { statement ->
                val resultSet = statement.executeQuery()
                val products = mutableListOf<ProductResponse>()

                while (resultSet.next()) {
                    products.add(
                        ProductResponse(
                            id = resultSet.getInt("id"),
                            title = resultSet.getString("title"),
                            description = resultSet.getString("description"),
                            price = resultSet.getDouble("price"),
                            image = resultSet.getString("image"),
                            categoryId = resultSet.getInt("category_id"),
                            sellNumber = resultSet.getInt("sell_number")
                        )
                    )
                }
                return@withContext products
            }
        } catch (e: Exception) {
            throw Exception("Failed to get best sellers: ${e.message}")
        }
    }

    suspend fun updateProductSellNumber(productId: Int, quantity: Int) = withContext(Dispatchers.IO) {
        try {
            connection.prepareStatement(UPDATE_PRODUCT_SELL_NUMBER).use { statement ->
                statement.setInt(1, quantity)
                statement.setInt(2, productId)
                statement.executeUpdate()
            }
        } catch (e: Exception) {
            throw Exception("Failed to update product sell number: ${e.message}")
        }
    }
}

class ProductNotFoundException : Exception("Product not found") 