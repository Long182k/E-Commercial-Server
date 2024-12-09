# E-Commerce Backend

This is the backend server for an e-commerce application built with Kotlin and Ktor.

## Getting Started

### Prerequisites
- Java JDK 11 or higher
- Gradle
- PostgreSQL

### Running the Server

To start the development server with auto-reload:
bash
./gradlew run -t


The `-t` flag enables continuous build, which means the server will automatically restart when changes are detected.

### Swagger
- URL: http://localhost:8081/swagger

### Server Details
- Default port: 8081
- Base URL: http://localhost:8081

### API Endpoints

#### Products
- GET `/products` - Get all products
- GET `/products/{id}` - Get product by ID

#### Cart
- GET `/cart/{userId}` - Get user's cart
- POST `/cart/{userId}` - Add item to cart
- PUT `/cart/{userId}/cart/{cartId}` - Update cart item quantity
- DELETE `/cart/{userId}/cart/{cartId}` - Remove item from cart

#### Orders
- GET `/orders/{userId}` - Get user's orders
- POST `/orders/{userId}` - Place new order
- GET `/orders/{userId}/checkout` - Get checkout summary

## Development

The project uses:
- Kotlin
- Ktor Framework
- PostgreSQL
- Kotlinx Serialization
- Gradle
