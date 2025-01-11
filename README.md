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

### Important Notice

⚠️ **Email Registration Requirements**:

- Please use a real email address when registering
- Email verification is required for order placement
- Invalid emails will cause order placement to fail
- Email service is powered by MailerSend

### API Documentation

- Swagger UI: http://localhost:8081/swagger
- Default port: 8081
- Base URL: http://localhost:8081

### API Endpoints

#### Authentication

- POST `/auth/register` - Register new user
- POST `/auth/login` - User login
- POST `/auth/change-password` - Change password
- POST `/auth/forgot-password` - Request password reset
- PUT `/auth/edit-profile` - Update user profile

#### Products

- GET `/products` - Get all products
- POST `/products` - Create multiple products
- GET `/products/{id}` - Get product by ID
- PUT `/products/{id}` - Update product
- DELETE `/products/{id}` - Delete product
- GET `/products/category/{categoryId}` - Get products by category
- GET `/products/best-sellers` - Get best selling products

#### Cart

- GET `/cart/{userId}` - Get user's cart
- POST `/cart/{userId}` - Add item to cart
- PUT `/cart/{userId}/{cartId}` - Update cart item quantity
- DELETE `/cart/{userId}/{cartId}` - Remove item from cart

#### Orders

- GET `/orders/{userId}` - Get user's orders
- POST `/orders/{userId}` - Place new order
- GET `/orders/checkout/{userId}` - Get checkout summary

## Development

The project uses:

- Kotlin
- Ktor Framework
- PostgreSQL hosting (powered by Supabase)
- Kotlinx Serialization
- Gradle
- MailerSend (Email Service)
- Cloudinary (Media Management)
