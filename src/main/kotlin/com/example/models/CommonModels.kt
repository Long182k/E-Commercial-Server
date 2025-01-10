package com.example.models

import kotlinx.serialization.Serializable

@Serializable
data class Address(
    val addressLine: String,
    val city: String,
    val state: String,
    val postalCode: String,
    val country: String
)

@Serializable
data class CartItemResponse(
    val id: Int,
    val productId: Int,
    val userId: Int,
    val productName: String,
    val quantity: Int,
    val price: Double,
    val imageUrl: String
) 