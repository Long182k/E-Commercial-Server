package com.example.services

import com.cloudinary.Cloudinary
import java.io.InputStream
import java.util.*
import java.io.File

class CloudinaryService(
    cloudName: String,
    apiKey: String,
    apiSecret: String
) {
    private val cloudinary = Cloudinary(mapOf(
        "cloud_name" to cloudName,
        "api_key" to apiKey,
        "api_secret" to apiSecret
    ))

    suspend fun uploadImage(inputStream: InputStream, fileName: String): String {
        // Create a temporary file
        val tempFile = File.createTempFile("upload_", fileName)
        tempFile.deleteOnExit()
        
        try {
            // Copy input stream to temp file
            tempFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }

            // Upload the temp file
            val result = cloudinary.uploader().upload(tempFile, mapOf(
                "public_id" to "avatars/${UUID.randomUUID()}-$fileName",
                "folder" to "ecommerce"
            ))
            
            return result["url"] as String
        } finally {
            // Clean up
            tempFile.delete()
        }
    }
}