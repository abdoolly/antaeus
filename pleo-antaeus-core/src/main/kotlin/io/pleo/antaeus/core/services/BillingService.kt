package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.external.PaymentProvider
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import kotlin.concurrent.schedule
import com.github.shyiko.skedule.Schedule
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import kotlinx.coroutines.*
import java.time.temporal.ChronoUnit
import kotlin.system.measureTimeMillis

class BillingService(
        private val paymentProvider: PaymentProvider,
        private val invoiceService: InvoiceService,
        private val customerService: CustomerService,
        private val notificationService: NotificationService
) {
    /**
     * time zone id
     */
    private val ZONE_ID = "CET"
    var retryMap: MutableMap<Int, Int> = mutableMapOf()

    private fun billInvoices() = runBlocking {
        try {
            // get all the invoices that are pending
            val invoices = invoiceService.fetchByStatus(InvoiceStatus.PENDING)
            val jobs: MutableList<Job> = mutableListOf()

            // loop on the invoices and send them to the charge method
            invoices.forEach { invoice ->
                val job = launch { chargeInvoice(invoice) }
                jobs.add(job)
            }

            // wait for all jobs to finish
            jobs.joinAll()

            // cleaning the retry map after all the jobs have finished
            retryMap = mutableMapOf()
        } catch (err: Exception) {
            notificationService.notifyAdmin("Unexpected error happened while running the period invoice charging cycle ${err.message}")
        }
    }

    // do not forget to handle exceptions
    private suspend fun chargeInvoice(invoice: Invoice) {
        val (id, customerId, amount) = invoice
        try {
            val isCharged = paymentProvider.charge(invoice)

            // update invoice status to PAID
            if (isCharged) {
                println("invoice with ID $id is PAID successfully")
                invoiceService.update(invoice.copy(status = InvoiceStatus.PAID))
            }

            if (!isCharged) {
                notificationService.notifyAdmin("Customer with ID: $customerId has no enough balance to make invoice ${invoice.id}")
            }
        } catch (err: CustomerNotFoundException) {
            notificationService.notifyAdmin("Customer in invoice with ID: $id does not exist")
            notificationService.notifyCustomer(customerId, "Insufficient balance in your account for invoice ID: ${invoice.id} to be charged")
        } catch (err: CurrencyMismatchException) {
            val customer = customerService.fetch(customerId)
            notificationService.notifyAdmin("Invoice with ID: $id has mismatching currency invoice currency ${amount.currency}, customer currency ${customer.currency}")
        } catch (err: NetworkException) {
            retryChargeInvoice(invoice)
        } catch (err: Exception) {
            notificationService.notifyAdmin("Unexpected error happened while processing invoice ID : $id")
        }
    }

    private suspend fun retryChargeInvoice(invoice: Invoice) {
        val (id) = invoice
        val retries: Int = retryMap.getOrPut(id, { 0 })

        // only 3 retries allowed for each invoice
        if (retries < 3) {
            retryMap.put(id, retries + 1) // incrementing retries
            // waiting 30 seconds between each retry
            delay(30000)
            return chargeInvoice(invoice)
        }

        notificationService.notifyAdmin("There is a network problem in payment provider for invoice ID $id")
    }

    /**
     * initiate the main billing service schedule on this server
     * that should make an invoice billing cycle run every month
     */
    fun runBillingScheduler(date: Date? = null) {
        val scheduleTime = date ?: getNextTime()
        val timer = Timer()

        println("scheduleTime is : $scheduleTime")

        timer.schedule(time = scheduleTime) {
            billInvoices()
            val nextDate = getNextTime(ZonedDateTime.ofInstant(scheduleTime.toInstant(), ZoneId.of(ZONE_ID)))
            cleanTimer(timer)
            runBillingScheduler(nextDate)
        }
    }

    /**
     * gets the next time in which the scheduler should run at any time
     */
    private fun getNextTime(date: ZonedDateTime? = null): Date {
//        val scheduler = Schedule.parse("1 of month 00:00")
        val scheduler = Schedule.every(1, ChronoUnit.MINUTES)
        if (date != null)
            return zonedDateToDate(scheduler.next(date));

        val now = ZonedDateTime.now()
        return zonedDateToDate(scheduler.nextOrSame(now))
    }

    /**
     * util function which convert from zonedDateTime object to Date object
     */
    private fun zonedDateToDate(date: ZonedDateTime): Date {
        return Date.from(date.toInstant())
    }

    /**
     * cleans the timer after it's execution
     */
    private fun cleanTimer(timer: Timer) {
        timer.cancel()
        timer.purge()
    }
}
