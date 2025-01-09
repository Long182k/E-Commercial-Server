package com.example.models

import kotlinx.serialization.Serializable

@Serializable
data class EmailRequest(
    val from: Recipient,
    val to: List<Recipient>,
    val subject: String,
    val html: String,
    val text: String? = null,
    val variables: List<Variable>? = null
) {
    @Serializable
    data class Recipient(
        val email: String,
        val name: String
    )

    @Serializable
    data class Variable(
        val email: String,
        val substitutions: List<Substitution>
    ) {
        @Serializable
        data class Substitution(
            val variable: String,
            val value: String
        )
    }
} 