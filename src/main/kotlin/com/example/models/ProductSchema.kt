package com.example.models

import kotlinx.serialization.Serializable
import java.sql.Connection
import kotlinx.coroutines.*
import java.util.UUID

@Serializable
data class Product(
    val id: Int? = null,
    val title: String,
    val price: Double,
    val description: String,
    val categoryId: Int,
    val image: String
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
                ID SERIAL PRIMARY KEY,
                TITLE VARCHAR(255) NOT NULL,
                PRICE DECIMAL(10,2) NOT NULL,
                DESCRIPTION TEXT,
                CATEGORY_ID INT REFERENCES CATEGORIES(ID),
                IMAGE VARCHAR(255)
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
            SELECT * FROM products WHERE category_id = ?
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
    }

    init {
        connection.createStatement().use { statement ->
            statement.execute(CREATE_TABLE_PRODUCTS)
            println("Products table created or verified successfully")
        }
    }

    suspend fun addProducts(products: List<Product>): List<ProductResponse> = withContext(Dispatchers.IO) {
        try {
            val responses = mutableListOf<ProductResponse>()
            
            connection.prepareStatement(INSERT_PRODUCT).use { statement ->
                for (product in products) {
                    val productId = UUID.randomUUID().toString()
                    
                    statement.setString(1, product.title)
                    statement.setDouble(2, product.price)
                    statement.setString(3, product.description)
                    statement.setInt(4, product.categoryId)
                    statement.setString(5, product.image)
                    statement.addBatch()

                    responses.add(
                        ProductResponse(
                            id = productId,
                            title = product.title,
                            description = product.description,
                            price = product.price,
                            image = product.image,
                            categoryId = product.categoryId.toString()
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
            connection.prepareStatement(SELECT_PRODUCT_BY_ID).use { statement ->
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

    suspend fun getProductById(id: Int): Product = withContext(Dispatchers.IO) {
        try {
            connection.prepareStatement(SELECT_PRODUCT_BY_ID).use { statement ->
                statement.setInt(1, id)
                val resultSet = statement.executeQuery()

                if (resultSet.next()) {
                    return@withContext Product(
                        id = resultSet.getInt("id"),
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
                            id = resultSet.getInt("id"),
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
                        id = resultSet.getInt("id").toString(),
                        title = resultSet.getString("title"),
                        description = resultSet.getString("description"),
                        price = resultSet.getDouble("price"),
                        image = resultSet.getString("image"),
                        categoryId = resultSet.getInt("category_id").toString()
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
}

class ProductNotFoundException : Exception("Product not found") 