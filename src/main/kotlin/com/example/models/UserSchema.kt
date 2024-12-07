package com.example.models

import kotlinx.serialization.Serializable
import java.sql.Connection
import kotlinx.coroutines.*
import java.util.UUID

@Serializable
data class User(
    val username: String? = null,
    val email: String,
    val password: String,
    val name: String
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class SuccessResponse<T>(
    val msg: String = "Success",
    val data: T
)

@Serializable
data class ErrorResponse(
    val status: Int,
    val msg: String
)

@Serializable
data class UserResponse(
    val id: String,
    val username: String,
    val email: String,
    val name: String
)

class UserService(private val connection: Connection) {
    companion object {
        private const val CREATE_TABLE_USERS = """
    CREATE TABLE IF NOT EXISTS USERS (
        ID VARCHAR(36) PRIMARY KEY,   -- UUID as primary key
        USERNAME VARCHAR(255) UNIQUE,
        EMAIL VARCHAR(255) UNIQUE,
        PASSWORD VARCHAR(255),
        NAME VARCHAR(255)
    );
"""

        private const val INSERT_USER = """
    INSERT INTO users (id, username, email, password, name) 
    VALUES (?, ?, ?, ?, ?)
"""

        private const val SELECT_USER_BY_CREDENTIALS = """
            SELECT * FROM users 
            WHERE email = ? AND password = ?
        """
        private const val CHECK_USER_EXISTS = """
            SELECT COUNT(*) FROM users 
            WHERE username = ? OR email = ?
        """
        private const val SELECT_USER_BY_EMAIL = """
            SELECT COUNT(*) FROM users 
            WHERE email = ?
        """
        private const val SELECT_USER_BY_USERNAME = """
            SELECT COUNT(*) FROM users 
            WHERE username = ?
        """
    }

    init {
        try {
            connection.createStatement().use { statement ->
                statement.execute(CREATE_TABLE_USERS)
                println("Users table created or verified successfully")
            }
        } catch (e: Exception) {
            println("Database initialization error: ${e.message}")
            throw e
        }
    }

    suspend fun register(user: User): UserResponse = withContext(Dispatchers.IO) {
        try {
            // Validate input
            validateRegistrationInput(user)

            // Check if user exists
            checkUserExists(user)

            // Generate a UUID for the new user
            val userId = UUID.randomUUID().toString()
            val username = user.name  // Set username to the same value as name

            // Create new user
            connection.prepareStatement(INSERT_USER).use { statement ->
                statement.setString(1, userId)       // Set the UUID as ID
                statement.setString(2, username)    // Set username
                statement.setString(3, user.email)  // Set email
                statement.setString(4, user.password) // Set password
                statement.setString(5, user.name)   // Set name

                statement.executeUpdate()

                val newUser = UserResponse(
                    id = userId,     // Use the generated UUID
                    username = username,
                    email = user.email,
                    name = user.name
                )

                return@withContext newUser
            }
        } catch (e: Exception) {
            println("Registration error: ${e.message}")
            when (e) {
                is UserExistsException -> throw e
                else -> throw Exception("Registration failed: ${e.message}")
            }
        }
    }



    suspend fun login(credentials: LoginRequest): UserResponse = withContext(Dispatchers.IO) {
        try {
            // Validate input
            validateLoginInput(credentials)

            connection.prepareStatement(SELECT_USER_BY_CREDENTIALS).use { statement ->
                println("Executing login query for email: ${credentials.email}")

                statement.setString(1, credentials.email)
                statement.setString(2, credentials.password)
                val resultSet = statement.executeQuery()

                if (resultSet.next()) {
                    val user = UserResponse(
                        id = resultSet.getString("id"),
                        username = resultSet.getString("username"),
                        email = resultSet.getString("email"),
                        name = resultSet.getString("name")
                    )
                    println("User logged in successfully: $user")
                    return@withContext user
                } else {
                    println("Invalid credentials for email: ${credentials.email}")
                    throw InvalidCredentialsException()
                }
            }
        } catch (e: Exception) {
            println("Login error: ${e.message}")
            when (e) {
                is InvalidCredentialsException -> throw e
                else -> throw Exception("Login failed: ${e.message}")
            }
        }
    }

    private fun validateRegistrationInput(user: User) {
        if (user.email.isBlank() || user.password.isBlank() || user.name.isBlank()) {
            throw IllegalArgumentException("Email, password, and name are required")
        }
        if (!isValidEmail(user.email)) {
            throw IllegalArgumentException("Invalid email format")
        }
        if (user.password.length < 6) {
            throw IllegalArgumentException("Password must be at least 6 characters")
        }
    }

    private fun validateLoginInput(credentials: LoginRequest) {
        if (credentials.email.isBlank() || credentials.password.isBlank()) {
            throw IllegalArgumentException("Email and password are required")
        }
        if (!isValidEmail(credentials.email)) {
            throw IllegalArgumentException("Invalid email format")
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return email.matches(Regex("^[A-Za-z0-9+_.-]+@(.+)\$"))
    }

    private fun checkUserExists(user: User) {
        connection.prepareStatement(CHECK_USER_EXISTS).use { statement ->
            statement.setString(1, user.username)
            statement.setString(2, user.email)
            val resultSet = statement.executeQuery()

            if (resultSet.next() && resultSet.getInt(1) > 0) {
                // Check specifically what exists to provide better error message
                if (checkEmailExists(user.email)) {
                    throw UserExistsException("Email already registered")
                }
                if (user.username != null && checkUsernameExists(user.username)) {
                    throw UserExistsException("Username already taken")
                }
                throw UserExistsException()
            }
        }
    }

    private fun checkEmailExists(email: String): Boolean {
        connection.prepareStatement(SELECT_USER_BY_EMAIL).use { statement ->
            statement.setString(1, email)
            val resultSet = statement.executeQuery()
            return resultSet.next() && resultSet.getInt(1) > 0
        }
    }

    private fun checkUsernameExists(username: String): Boolean {
        connection.prepareStatement(SELECT_USER_BY_USERNAME).use { statement ->
            statement.setString(1, username)
            val resultSet = statement.executeQuery()
            return resultSet.next() && resultSet.getInt(1) > 0
        }
    }
}

class UserExistsException(message: String? = null) : Exception(message ?: "User already exists")
class InvalidCredentialsException : Exception("Invalid email or password")