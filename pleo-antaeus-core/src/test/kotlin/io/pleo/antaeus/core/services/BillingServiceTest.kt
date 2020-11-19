package io.pleo.antaeus.core.services

import io.mockk.*
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Invoice
import net.bytebuddy.dynamic.Nexus.clean
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import kotlin.random.Random

class BillingServiceTest {
    var billingService: BillingService? = null

    init {
        initializeBillingService()
    }

    private fun initializeBillingService() {
        val invoiceService = mockk<InvoiceService>()
        val customerService = mockk<CustomerService>()
        val paymentProvider: PaymentProvider = mockk<PaymentProvider>()
        val notificationService = mockk<NotificationService>()

        billingService = BillingService(
                paymentProvider,
                invoiceService,
                customerService,
                notificationService
        )
    }

    @Test
    fun `should clean timer`() {
        val timer = mockk<Timer>()
        every { timer.cancel() } just Runs
        every { timer.purge() } returns 1

        billingService?.cleanTimer(timer)
        verifyOrder {
            timer.cancel()
            timer.purge()
        }
    }

    @Test
    fun `should convert zonedDate to Date`() {
        val zonedDate = ZonedDateTime.now()
        val date = billingService?.zonedDateToDate(zonedDate)
        assertEquals(Date.from(date?.toInstant()), date)
    }

    @Test
    fun `getNextTime should get the 1st day of the next month given any date or not give any date at all`() {
        val zoned = ZonedDateTime.of(
                LocalDate.of(2020, 11, 19),
                LocalTime.of(10, 0, 0),
                ZoneId.of("CET")
        )
        mockkStatic(ZonedDateTime::class)
        every { ZonedDateTime.now() } returns zoned

        // without any params
        val date = billingService?.getNextTime()
        assertEquals("Tue Dec 01 01:00:00 EET 2020", date.toString())

        // with params
        val nextDate = billingService?.getNextTime(zoned.plusMonths(1))
        assertEquals("Fri Jan 01 01:00:00 EET 2021", nextDate.toString())
    }

    @Test
    fun `getNextTime should get the same date if today was the first day`() {
        val zoned = ZonedDateTime.of(
                LocalDate.of(2020, 11, 1),
                LocalTime.of(10, 0, 0),
                ZoneId.of("CET")
        )
        mockkStatic(ZonedDateTime::class)
        every { ZonedDateTime.now() } returns zoned

        val date = billingService?.getNextTime()
        assertEquals("Sun Nov 01 11:00:00 EET 2020", date.toString())
    }
}