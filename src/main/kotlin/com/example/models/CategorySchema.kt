package com.example.models

import kotlinx.serialization.Serializable
import java.sql.Connection
import kotlinx.coroutines.*
import java.util.UUID

@Serializable
data class Category(
    val title: String,
    val image: String
)

@Serializable
data class CategoryResponse(
    val id: Int,
    val title: String,
    val image: String
)

class CategoryService(private val connection: Connection) {
    companion object {
        private const val CREATE_TABLE_CATEGORIES = """
            CREATE TABLE IF NOT EXISTS CATEGORIES (
                ID SERIAL PRIMARY KEY,
                TITLE VARCHAR(255) NOT NULL,
                IMAGE VARCHAR(255)
            );
        """

        private const val INSERT_CATEGORY = """
            INSERT INTO categories (title, image) 
            VALUES (?, ?) RETURNING id
        """

        private const val SELECT_ALL_CATEGORIES = """
            SELECT * FROM categories
        """

        private const val SELECT_CATEGORY_BY_ID = """
            SELECT * FROM categories WHERE id = ?
        """
        private const val UPDATE_CATEGORY = """
            UPDATE categories 
            SET title = ?, image = ?
            WHERE id = ?
        """
        private const val DELETE_CATEGORY = "DELETE FROM categories WHERE id = ?"
    }

    init {
        try {
            connection.createStatement().use { statement ->
                statement.execute(CREATE_TABLE_CATEGORIES)
                println("Categories table created or verified successfully")
            }
        } catch (e: Exception) {
            println("Database initialization error: ${e.message}")
            throw e
        }
    }

    suspend fun addCategories(categories: List<Category>): List<CategoryResponse> = withContext(Dispatchers.IO) {
        try {
            val responses = mutableListOf<CategoryResponse>()
            
            for (category in categories) {
                connection.prepareStatement(INSERT_CATEGORY).use { statement ->
                    statement.setString(1, category.title)
                    statement.setString(2, category.image)
                    
                    val resultSet = statement.executeQuery()
                    if (resultSet.next()) {
                        val id = resultSet.getInt("id")
                        responses.add(
                            CategoryResponse(
                                id = id,
                                title = category.title,
                                image = category.image
                            )
                        )
                    }
                }
            }
            return@withContext responses
        } catch (e: Exception) {
            throw Exception("Failed to add categories: ${e.message}")
        }
    }

    suspend fun getCategories(): List<CategoryResponse> = withContext(Dispatchers.IO) {
        try {
            connection.prepareStatement(SELECT_ALL_CATEGORIES).use { statement ->
                val resultSet = statement.executeQuery()
                val categories = mutableListOf<CategoryResponse>()

                while (resultSet.next()) {
                    categories.add(
                        CategoryResponse(
                            id = resultSet.getInt("id"),
                            title = resultSet.getString("title"),
                            image = resultSet.getString("image")
                        )
                    )
                }
                return@withContext categories
            }
        } catch (e: Exception) {
            throw Exception("Failed to get categories: ${e.message}")
        }
    }

    suspend fun getCategoryById(id: Int): CategoryResponse = withContext(Dispatchers.IO) {
        try {
            connection.prepareStatement(SELECT_CATEGORY_BY_ID).use { statement ->
                statement.setInt(1, id)
                val resultSet = statement.executeQuery()

                if (resultSet.next()) {
                    return@withContext CategoryResponse(
                        id = resultSet.getInt("id"),
                        title = resultSet.getString("title"),
                        image = resultSet.getString("image")
                    )
                } else {
                    throw CategoryNotFoundException()
                }
            }
        } catch (e: Exception) {
            when (e) {
                is CategoryNotFoundException -> throw e
                else -> throw Exception("Failed to get category: ${e.message}")
            }
        }
    }

    suspend fun updateCategory(id: Int, category: Category): CategoryResponse = withContext(Dispatchers.IO) {
        try {
            connection.prepareStatement(UPDATE_CATEGORY).use { statement ->
                statement.setString(1, category.title)
                statement.setString(2, category.image)
                statement.setInt(3, id)

                val rowsUpdated = statement.executeUpdate()
                if (rowsUpdated == 0) {
                    throw CategoryNotFoundException()
                }

                return@withContext CategoryResponse(
                    id = id.toInt(),
                    title = category.title,
                    image = category.image
                )
            }
        } catch (e: Exception) {
            when (e) {
                is CategoryNotFoundException -> throw e
                else -> throw Exception("Failed to update category: ${e.message}")
            }
        }
    }

    suspend fun deleteCategory(id: Int) = withContext(Dispatchers.IO) {
        try {
            connection.prepareStatement(DELETE_CATEGORY).use { statement ->
                statement.setInt(1, id)
                val rowsDeleted = statement.executeUpdate()
                if (rowsDeleted == 0) {
                    throw CategoryNotFoundException()
                }
            }
        } catch (e: Exception) {
            when (e) {
                is CategoryNotFoundException -> throw e
                else -> throw Exception("Failed to delete category: ${e.message}")
            }
        }
    }
}

class CategoryNotFoundException : Exception("Category not found") 