package io.pleo.antaeus.core.services

class NotificationService {

    /**
     * a function that should notify a customer
     */
    fun notifyCustomer(customerId: Int, message: String) {
        println("err for customer $customerId: $message")
    }

    /**
     * a function that should notify an admin
     */
    fun notifyAdmin(message: String) {
        println(message)
    }
}