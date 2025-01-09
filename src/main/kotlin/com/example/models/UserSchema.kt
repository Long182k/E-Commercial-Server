package com.example.models

import kotlinx.serialization.Serializable
import java.sql.Connection
import kotlinx.coroutines.*
import java.util.UUID
import com.example.services.EmailService

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
data class ChangePasswordRequest(
    val email: String,
    val oldPassword: String,
    val newPassword: String
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
    val id: Int,
    val username: String,
    val email: String,
    val name: String
)

@Serializable
data class ForgotPasswordRequest(
    val email: String
)

@Serializable
data class EditProfileRequest(
    val name: String,
    val avatarUrl: String? = null
)

@Serializable
data class UserProfileResponse(
    val id: Int,
    val username: String,
    val email: String,
    val name: String,
    val avatarUrl: String?
)

class UserService(
    private val connection: Connection,
    private val emailService: EmailService
) {
    companion object {
        private const val CREATE_TABLE_USERS = """
    CREATE TABLE IF NOT EXISTS USERS (
        ID SERIAL PRIMARY KEY,
        USERNAME VARCHAR(255) UNIQUE,
        EMAIL VARCHAR(255) UNIQUE,
        PASSWORD VARCHAR(255),
        NAME VARCHAR(255),
        AVATAR_URL TEXT
    );
"""

        private const val INSERT_USER = """
    INSERT INTO users (username, email, password, name) 
    VALUES (?, ?, ?, ?) RETURNING id
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
        private const val UPDATE_PASSWORD = """
            UPDATE users SET password = ? 
            WHERE email = ? AND password = ?
        """
        private const val UPDATE_PASSWORD_BY_EMAIL = """
            UPDATE users SET password = ? 
            WHERE email = ?
        """
        private const val UPDATE_USER_PROFILE = """
            UPDATE users 
            SET name = ?, avatar_url = COALESCE(?, avatar_url)
            WHERE email = ?
            RETURNING id, username, email, name, avatar_url
        """
    }

    init {
        try {
            connection.createStatement().use { statement ->
                statement.execute(CREATE_TABLE_USERS)
            }
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun register(user: User): UserResponse = withContext(Dispatchers.IO) {
        try {
            // Validate input
            validateRegistrationInput(user)

            // Check if user exists
            checkUserExists(user)

            connection.prepareStatement(INSERT_USER).use { statement ->
                statement.setString(1, user.name)    
                statement.setString(2, user.email)   
                statement.setString(3, user.password) 
                statement.setString(4, user.name)    

                val resultSet = statement.executeQuery()
                resultSet.next()
                val userId = resultSet.getInt("id")

                return@withContext UserResponse(
                    id = userId,
                    username = user.name,
                    email = user.email,
                    name = user.name
                )
            }
        } catch (e: Exception) {
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

                statement.setString(1, credentials.email)
                statement.setString(2, credentials.password)
                val resultSet = statement.executeQuery()

                if (resultSet.next()) {
                    val user = UserResponse(
                        id = resultSet.getInt("id"),
                        username = resultSet.getString("username"),
                        email = resultSet.getString("email"),
                        name = resultSet.getString("name")
                    )
                    return@withContext user
                } else {
                    throw InvalidCredentialsException()
                }
            }
        } catch (e: Exception) {
            when (e) {
                is InvalidCredentialsException -> throw e
                else -> throw Exception("Login failed: ${e.message}")
            }
        }
    }

    suspend fun changePassword(request: ChangePasswordRequest): Boolean = withContext(Dispatchers.IO) {
        try {
            // Check if user exists first
            if (!checkEmailExists(request.email)) {
                throw UserNotFoundException("User not found")
            }

            // Validate input
            if (request.email.isBlank() || request.oldPassword.isBlank() || request.newPassword.isBlank()) {
                throw IllegalArgumentException("All fields are required")
            }

            if (request.newPassword.length < 6) {
                throw IllegalArgumentException("New password must be at least 6 characters")
            }


            connection.prepareStatement(UPDATE_PASSWORD).use { statement ->
                statement.setString(1, request.newPassword)
                statement.setString(2, request.email)
                statement.setString(3, request.oldPassword)
                
                val updatedRows = statement.executeUpdate()
                if (updatedRows == 0) {
                    throw InvalidCredentialsException()
                }
                return@withContext true
            }
        } catch (e: Exception) {
            when (e) {
                is InvalidCredentialsException -> throw e
                is IllegalArgumentException -> throw e
                is UserNotFoundException -> throw e
                else -> throw Exception("Password change failed: ${e.message}")
            }
        }
    }

    suspend fun forgotPassword(email: String) = withContext(Dispatchers.IO) {
        try {
            println("Forgot password request received for email: $email")
            // Validate email
            if (email.isBlank() || !isValidEmail(email)) {
                throw IllegalArgumentException("Invalid email format")
            }

            // Check if user exists
            if (!checkEmailExists(email)) {
                throw UserNotFoundException("User not found")
            }

            // Generate new password
            val newPassword = generateRandomPassword()
            println("Generated new password: $newPassword")
            // Update password in database
            connection.prepareStatement(UPDATE_PASSWORD_BY_EMAIL).use { statement ->
                statement.setString(1, newPassword)
                statement.setString(2, email)
                
                val updatedRows = statement.executeUpdate()
                if (updatedRows == 0) {
                    println("Failed to update password")
                    throw Exception("Failed to update password")
                }

                // Send email with new password
                emailService.sendPasswordResetEmail(email, newPassword)
                println("Email sent successfully")
                return@withContext true
            }
        } catch (e: Exception) {
            when (e) {
                is IllegalArgumentException -> throw e
                is UserNotFoundException -> throw e
                else -> throw Exception("Password reset failed: ${e.message}")
            }
        }
    }

    suspend fun updateProfile(email: String, request: EditProfileRequest): UserProfileResponse = withContext(Dispatchers.IO) {
        try {
            println("Updating profile for email: $email")
            println("Request: $request")
            if (request.name.isBlank()) {
                throw IllegalArgumentException("Name cannot be empty")
            }

            connection.prepareStatement(UPDATE_USER_PROFILE).use { statement ->
                statement.setString(1, request.name)
                statement.setString(2, request.avatarUrl)
                statement.setString(3, email)

                val resultSet = statement.executeQuery()
                if (resultSet.next()) {
                    return@withContext UserProfileResponse(
                        id = resultSet.getInt("id"),
                        username = resultSet.getString("username"),
                        email = resultSet.getString("email"),
                        name = resultSet.getString("name"),
                        avatarUrl = resultSet.getString("avatar_url")
                    )
                } else {
                    throw UserNotFoundException("User not found")
                }
            }
        } catch (e: Exception) {
            when (e) {
                is IllegalArgumentException -> throw e
                is UserNotFoundException -> throw e
                else -> throw Exception("Profile update failed: ${e.message}")
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

    private fun generateRandomPassword(): String {
        val chars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        return (1..8)
            .map { chars.random() }
            .joinToString("")
    }
}

class UserExistsException(message: String? = null) : Exception(message ?: "User already exists")
class InvalidCredentialsException : Exception("Invalid email or password")
class UserNotFoundException(message: String) : Exception(message)