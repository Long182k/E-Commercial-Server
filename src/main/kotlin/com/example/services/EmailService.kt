package com.example.services

import com.example.models.EmailRequest
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.client.engine.cio.*
import kotlinx.serialization.Serializable

@Serializable
private data class EmailRequestSimple(
    val from: FromEmail,
    val to: List<ToEmail>,
    val subject: String,
    val text: String,
    val html: String
) {
    @Serializable
    data class FromEmail(val email: String)
    
    @Serializable
    data class ToEmail(val email: String)
}

class EmailService(
    private val apiKey: String,
    private val senderEmail: String
) {
    private val baseUrl = "https://api.mailersend.com/v1"
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }

    suspend fun sendPasswordResetEmail(toEmail: String, newPassword: String) {
        try {
            println("apiKey: $apiKey")
            println("senderEmail: $senderEmail")

            val emailRequest = EmailRequestSimple(
                from = EmailRequestSimple.FromEmail(
                    email = senderEmail
                ),
                to = listOf(
                    EmailRequestSimple.ToEmail(email = toEmail)
                ),
                subject = "Password Reset",
                text = """
                    Your password has been reset.
                    Your new password is: $newPassword
                    
                    Please login and change your password immediately for security reasons.
                """.trimIndent(),
                html = """
                    <p>Your password has been reset.</p>
                    <p>Your new password is: <strong>$newPassword</strong></p>
                    <p>Please login and change your password immediately for security reasons.</p>
                """.trimIndent()
            )

            val response = client.post("$baseUrl/email") {
                header("Authorization", "Bearer $apiKey")
                header("Content-Type", "application/json")
                header("X-Requested-With", "XMLHttpRequest")
                setBody(emailRequest)   
            }


            if (response.status != HttpStatusCode.Accepted) {
                throw EmailSendException("Failed to send email: ${response.status}")
            }

        } catch (e: Exception) {
            throw EmailSendException("Failed to send email: ${e.message}")
        }
    }
}

class EmailSendException(message: String) : Exception(message) 