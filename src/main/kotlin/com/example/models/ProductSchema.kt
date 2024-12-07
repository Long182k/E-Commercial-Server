package com.example.models

import kotlinx.serialization.Serializable
import java.sql.Connection
import kotlinx.coroutines.*
import java.util.UUID

@Serializable
data class Product(
    val title: String,
    val description: String,
    val price: Double,
    val image: String,
    val categoryId: String
)

@Serializable
data class ProductResponse(
    val id: String,
    val title: String,
    val description: String,
    val price: Double,
    val image: String,
    val categoryId: String
)

class ProductService(private val connection: Connection) {
    companion object {
        private const val CREATE_TABLE_PRODUCTS = """
            CREATE TABLE IF NOT EXISTS PRODUCTS (
                ID VARCHAR(36) PRIMARY KEY,
                TITLE VARCHAR(255) NOT NULL,
                DESCRIPTION TEXT,
                PRICE DOUBLE PRECISION NOT NULL,
                IMAGE TEXT,
                CATEGORY_ID VARCHAR(36) REFERENCES CATEGORIES(ID)
            );
        """

        private const val INSERT_PRODUCT = """
            INSERT INTO products (id, title, description, price, image, category_id) 
            VALUES (?, ?, ?, ?, ?, ?)
        """

        private const val SELECT_ALL_PRODUCTS = "SELECT * FROM products"
        private const val SELECT_PRODUCT_BY_ID = "SELECT * FROM products WHERE id = ?"
        private const val UPDATE_PRODUCT = """
            UPDATE products 
            SET title = ?, description = ?, price = ?, image = ?, category_id = ?
            WHERE id = ?
        """
        private const val DELETE_PRODUCT = "DELETE FROM products WHERE id = ?"
    }

    init {
        try {
            // First verify that the Categories table exists
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT 1 FROM categories LIMIT 1")
            }
            
            // Then create the Products table
            connection.createStatement().use { statement ->
                statement.execute(CREATE_TABLE_PRODUCTS)
                println("Products table created or verified successfully")
            }
        } catch (e: Exception) {
            println("Database initialization error: ${e.message}")
            throw e
        }
    }

    suspend fun addProducts(products: List<Product>): List<ProductResponse> = withContext(Dispatchers.IO) {
        try {
            val responses = mutableListOf<ProductResponse>()
            
            connection.prepareStatement(INSERT_PRODUCT).use { statement ->
                for (product in products) {
                    val productId = UUID.randomUUID().toString()
                    
                    statement.setString(1, productId)
                    statement.setString(2, product.title)
                    statement.setString(3, product.description)
                    statement.setDouble(4, product.price)
                    statement.setString(5, product.image)
                    statement.setString(6, product.categoryId)
                    statement.addBatch()

                    responses.add(
                        ProductResponse(
                            id = productId,
                            title = product.title,
                            description = product.description,
                            price = product.price,
                            image = product.image,
                            categoryId = product.categoryId
                        )
                    )
                }
                statement.executeBatch()
                return@withContext responses
            }
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
                            id = resultSet.getString("id"),
                            title = resultSet.getString("title"),
                            description = resultSet.getString("description"),
                            price = resultSet.getDouble("price"),
                            image = resultSet.getString("image"),
                            categoryId = resultSet.getString("category_id")
                        )
                    )
                }
                return@withContext products
            }
        } catch (e: Exception) {
            throw Exception("Failed to get products: ${e.message}")
        }
    }

    suspend fun getProductById(id: String): ProductResponse = withContext(Dispatchers.IO) {
        try {
            connection.prepareStatement(SELECT_PRODUCT_BY_ID).use { statement ->
                statement.setString(1, id)
                val resultSet = statement.executeQuery()

                if (resultSet.next()) {
                    return@withContext ProductResponse(
                        id = resultSet.getString("id"),
                        title = resultSet.getString("title"),
                        description = resultSet.getString("description"),
                        price = resultSet.getDouble("price"),
                        image = resultSet.getString("image"),
                        categoryId = resultSet.getString("category_id")
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

    suspend fun updateProduct(id: String, product: Product): ProductResponse = withContext(Dispatchers.IO) {
        try {
            connection.prepareStatement(UPDATE_PRODUCT).use { statement ->
                statement.setString(1, product.title)
                statement.setString(2, product.description)
                statement.setDouble(3, product.price)
                statement.setString(4, product.image)
                statement.setString(5, product.categoryId)
                statement.setString(6, id)

                val rowsUpdated = statement.executeUpdate()
                if (rowsUpdated == 0) {
                    throw ProductNotFoundException()
                }

                return@withContext ProductResponse(
                    id = id,
                    title = product.title,
                    description = product.description,
                    price = product.price,
                    image = product.image,
                    categoryId = product.categoryId
                )
            }
        } catch (e: Exception) {
            when (e) {
                is ProductNotFoundException -> throw e
                else -> throw Exception("Failed to update product: ${e.message}")
            }
        }
    }

    suspend fun deleteProduct(id: String) = withContext(Dispatchers.IO) {
        try {
            connection.prepareStatement(DELETE_PRODUCT).use { statement ->
                statement.setString(1, id)
                val rowsDeleted = statement.executeUpdate()
                if (rowsDeleted == 0) {
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
}

class ProductNotFoundException : Exception("Product not found") 