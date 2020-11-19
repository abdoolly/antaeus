package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.external.PaymentProvider
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import kotlin.concurrent.schedule
import com.github.shyiko.skedule.Schedule
import java.time.temporal.ChronoUnit

class BillingService(
        private val paymentProvider: PaymentProvider,
        private val invoiceService: InvoiceService,
        private val notificationService: NotificationService
) {
    /**
     * time zone id
     */
    private val ZONE_ID = "CET"

    fun billInvoices() {
        println("billing invoices")
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
    fun getNextTime(date: ZonedDateTime? = null): Date {
        val scheduler = Schedule.parse("1 of month 00:00")
//        val scheduler = Schedule.every(1, ChronoUnit.MINUTES)
        if (date != null)
            return zonedDateToDate(scheduler.next(date));

        val now = ZonedDateTime.now()
        return zonedDateToDate(scheduler.nextOrSame(now))
    }

    /**
     * util function which convert from zonedDateTime object to Date object
     */
    fun zonedDateToDate(date: ZonedDateTime): Date {
        return Date.from(date.toInstant())
    }

    /**
     * cleans the timer after it's execution
     */
    fun cleanTimer(timer: Timer) {
        timer.cancel()
        timer.purge()
    }
}
