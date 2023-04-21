package com.example.ecommerceadminapp

data class Product(
    val id: String,
    val name: String,
    val category: String,
    val price: Int,
    val offerPercentage: Float? = null,
    val description: String? = null,
    val colors: List<Int>? = null,
    val sizes: List<String>? = null,
    val quantity:String,
    val images: List<String>
)
