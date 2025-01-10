package com.example.services

import com.example.models.Address
import com.example.models.CartItemResponse
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

    suspend fun sendOrderConfirmationEmail(
        recipientEmail: String,
        recipientName: String,
        orderNumber: String,
        address: Address,
        items: List<CartItemResponse>,
        total: Double
    ) {
        val itemsHtml = items.joinToString("") { item ->
            val secureImageUrl = item.imageUrl.takeIf { it.isNotBlank() }
                ?.replace("http://", "https://")
                ?: "https://via.placeholder.com/60" // Fallback image

            """
            <tr>
                <td style="padding: 12px; border-bottom: 1px solid #eee;">
                    <div style="display: flex; align-items: center;">
                        <div style="min-width: 60px; margin-right: 12px;">
                            <img src="$secureImageUrl" 
                                 alt="${item.productName}"
                                 style="width: 60px; height: 60px; object-fit: cover; border-radius: 4px; border: 1px solid #eee;"
                                 onerror="this.onerror=null; this.src='https://via.placeholder.com/60';">
                        </div>
                        <div style="flex: 1;">
                            <span style="display: inline-block; word-break: break-word;">${item.productName}</span>
                        </div>
                    </div>
                </td>
                <td style="padding: 12px; border-bottom: 1px solid #eee; text-align: center; white-space: nowrap;">${item.quantity}</td>
                <td style="padding: 12px; border-bottom: 1px solid #eee; text-align: right; white-space: nowrap;">$${String.format("%.2f", item.price)}</td>
                <td style="padding: 12px; border-bottom: 1px solid #eee; text-align: right; white-space: nowrap;">$${String.format("%.2f", item.price * item.quantity)}</td>
            </tr>
            """
        }

        val htmlBody = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>JetECommerce - Order Confirmation</title>
            </head>
            <body style="margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #333; background-color: #f4f4f4;">
                <div style="max-width: 600px; margin: 0 auto; background-color: #ffffff; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1);">
                    <div style="text-align: center; margin-bottom: 20px;">
                        <img src="https://res.cloudinary.com/dcivdqyyj/image/upload/v1736505364/arqrqvoch2ihezsa5nxj.png"
                             alt="JetECommerce"
                             style="width: 200px; height: auto; margin-bottom: 10px;"
                             onerror="this.style.display='none'">
                    </div>
                    
                    <div style="text-align: center; padding: 20px 0; background-color: #f8f9fa; border-radius: 8px; margin-bottom: 20px;">
                        <h2 style="color: #2196F3; margin: 0; font-size: 24px;">Order Confirmation</h2>
                        <p style="color: #666; margin: 10px 0 0;">Order #$orderNumber</p>
                    </div>

                    <div style="margin-bottom: 20px;">
                        <p style="margin: 0; font-size: 16px;">Dear ${recipientName.capitalize()},</p>
                        <p style="margin: 10px 0 0; color: #666;">
                            Thank you for shopping with JetECommerce! We're pleased to confirm that we've received your order and it's being processed.
                        </p>
                    </div>

                    <div style="background-color: #f8f9fa; padding: 15px; border-radius: 8px; margin-bottom: 20px;">
                        <h2 style="color: #333; margin: 0 0 10px; font-size: 18px;">Shipping Address</h2>
                        <p style="margin: 0; line-height: 1.5;">
                            ${address.addressLine}<br>
                            ${address.city}, ${address.state} ${address.postalCode}<br>
                            ${address.country}
                        </p>
                    </div>

                    <table style="width: 100%; border-collapse: collapse; margin-bottom: 20px;">
                        <thead>
                            <tr style="background-color: #f8f9fa;">
                                <th style="padding: 12px; text-align: left; border-bottom: 2px solid #eee;">Product</th>
                                <th style="padding: 12px; text-align: center; border-bottom: 2px solid #eee; white-space: nowrap;">Quantity</th>
                                <th style="padding: 12px; text-align: right; border-bottom: 2px solid #eee; white-space: nowrap;">Price</th>
                                <th style="padding: 12px; text-align: right; border-bottom: 2px solid #eee; white-space: nowrap;">Subtotal</th>
                            </tr>
                        </thead>
                        <tbody>
                            $itemsHtml
                            <tr>
                                <td colspan="3" style="padding: 12px; text-align: right; font-weight: bold; border-top: 2px solid #eee;">Total:</td>
                                <td style="padding: 12px; text-align: right; font-weight: bold; border-top: 2px solid #eee;">$${String.format("%.2f", total)}</td>
                            </tr>
                        </tbody>
                    </table>

                    <div style="text-align: center; padding: 20px 0; border-top: 2px solid #eee;">
                        <p style="color: #666; margin: 0;">Thank you for shopping with JetECommerce, ${recipientName.capitalize()}!</p>
                        <p style="color: #666; margin: 10px 0 0; font-size: 12px;">
                            If you have any questions about your order, please contact our support team at support@jetecommerce.com
                        </p>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()

        try {
            val emailRequest = EmailRequestSimple(
                from = EmailRequestSimple.FromEmail(email = senderEmail),
                to = listOf(EmailRequestSimple.ToEmail(email = recipientEmail)),
                subject = "Order Confirmation #$orderNumber",
                text = createPlainTextVersion(recipientName, orderNumber, address, items, total),
                html = htmlBody
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

    private fun createPlainTextVersion(
        recipientName: String,
        orderNumber: String,
        address: Address,
        items: List<CartItemResponse>,
        total: Double
    ): String {
        val itemsList = items.joinToString("\n") { item ->
            "${item.productName} x ${item.quantity} = $${String.format("%.2f", item.price * item.quantity)}"
        }

        return """
            JetECommerce
            
            Dear ${recipientName.capitalize()},

            Thank you for shopping with JetECommerce! We're pleased to confirm that we've received your order and it's being processed.

            Order Confirmation #$orderNumber

            Shipping Address:
            ${address.addressLine}
            ${address.city}, ${address.state} ${address.postalCode}
            ${address.country}

            Items:
            $itemsList

            Total: $${String.format("%.2f", total)}

            Thank you for shopping with JetECommerce!
            If you have any questions about your order, please contact our support team at support@jetecommerce.com
        """.trimIndent()
    }
}

class EmailSendException(message: String) : Exception(message) 