package io.pleo.antaeus.core.services

import io.mockk.*
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.*
import io.pleo.antaeus.models.Currency
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.Exception
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

class BillingServiceTest {

    val paymentProvider = mockk<PaymentProvider>()
    val invoiceService = mockk<InvoiceService>()
    val customerService = mockk<CustomerService>()
    val notificationService = mockk<NotificationService>()

    var billingService: BillingService = BillingService(
            paymentProvider,
            invoiceService,
            customerService,
            notificationService
    )

    @BeforeEach
    fun beforeEach() {
        clearAllMocks()
        clearStaticMockk()
    }

    @Test
    fun `should clean timer`() {
        val timer = mockk<Timer>()
        every { timer.cancel() } just Runs
        every { timer.purge() } returns 1

        billingService.cleanTimer(timer)
        verifyOrder {
            timer.cancel()
            timer.purge()
        }
    }

    @Test
    fun `should convert zonedDate to Date`() {
        val zonedDate = ZonedDateTime.now()
        val date = billingService.zonedDateToDate(zonedDate)
        assertEquals(Date.from(date.toInstant()), date)
    }

    @Test
    fun `getNextTime should get the 1st day of the next month given any date or not give any date at all`() {
        val zoned = ZonedDateTime.of(
                LocalDate.of(2020, 11, 19),
                LocalTime.of(10, 0, 0),
                ZoneId.of("CET")
        )
        mockkStatic(ZonedDateTime::class)
        every { ZonedDateTime.now(ZoneId.of("CET")) } returns zoned

        // without any params
        val date = billingService.getNextTime()
        assertEquals("Tue Dec 01 00:00:00 CET 2020", date.toString())

        // with params
        val nextDate = billingService.getNextTime(zoned.plusMonths(1))
        assertEquals("Fri Jan 01 00:00:00 CET 2021", nextDate.toString())
    }

    @Test
    fun `getNextTime should get the same date if today was the first day`() {
        val zoned = ZonedDateTime.of(
                LocalDate.of(2020, 11, 1),
                LocalTime.of(10, 0, 0),
                ZoneId.of("CET")
        )
        mockkStatic(ZonedDateTime::class)
        every { ZonedDateTime.now(ZoneId.of("CET")) } returns zoned

        val date = billingService.getNextTime()
        assertEquals("Sun Nov 01 10:00:00 CET 2020", date.toString())
    }

    @Test
    fun `retryChargeInvoice should retry invoice`() = runBlockingTest {
        val invoice = Invoice(
                id = 1,
                customerId = 1,
                amount = Money(BigDecimal.ONE, Currency.USD),
                status = InvoiceStatus.PENDING
        )

        val billMocked = mockk<BillingService>()
        coEvery { billMocked.retryChargeInvoice(invoice) } coAnswers { callOriginal() }
        coEvery { billMocked.chargeInvoice(invoice) } returns Unit
        every { billMocked.retryMap } returns mutableMapOf()

        billMocked.retryChargeInvoice(invoice)

        coVerify(exactly = 1) {
            billMocked.chargeInvoice(invoice)
        }
    }

    @Test
    fun `retryChargeInvoice should not retry invoice more than three times`() = runBlockingTest {
        val invoice = Invoice(
                id = 1,
                customerId = 1,
                amount = Money(BigDecimal.ONE, Currency.USD),
                status = InvoiceStatus.PENDING
        )
        val adminNotMsg = "There is a network problem in payment provider for invoice ID ${invoice.id}"

        every { notificationService.notifyAdmin(adminNotMsg) } just Runs
        val billSpy = spyk(billingService) {
            coEvery { chargeInvoice(invoice) } just Runs
            every { retryMap } answers { mutableMapOf(1 to 3) }
        }

        billSpy.retryChargeInvoice(invoice)

        verify {
            notificationService.notifyAdmin(adminNotMsg)
        }
    }

    @Test
    fun `chargeInvoice should charge invoice correctly`() = runBlocking {
        val invoice = Invoice(
                id = 1,
                customerId = 1,
                amount = Money(BigDecimal.ONE, Currency.USD),
                status = InvoiceStatus.PENDING
        )

        every { paymentProvider.charge(invoice) } returns true
        every { invoiceService.update(invoice.copy(status = InvoiceStatus.PAID)) } returns invoice.copy(status = InvoiceStatus.PAID)
        val billSpy = spyk(billingService)
        billSpy.chargeInvoice(invoice)

        verify {
            invoiceService.update(invoice.copy(status = InvoiceStatus.PAID))
        }
    }

    @Test
    fun `chargeInvoice should notify admin and customer in case customer did not have enough balance in his account`() = runBlocking {
        val invoice = Invoice(
                id = 1,
                customerId = 1,
                amount = Money(BigDecimal.ONE, Currency.USD),
                status = InvoiceStatus.PENDING
        )
        val adminMsg = "Customer with ID: ${invoice.customerId} has no enough balance to make invoice ${invoice.id}"
        val customerMsg = "Insufficient balance in your account for invoice ID: ${invoice.id} to be charged"

        every { paymentProvider.charge(invoice) } returns false
        every { notificationService.notifyAdmin(adminMsg) } just Runs
        every { notificationService.notifyCustomer(1, customerMsg) } just Runs
        val billSpy = spyk(billingService)
        billSpy.chargeInvoice(invoice)

        verifyOrder {
            notificationService.notifyAdmin(adminMsg)
            notificationService.notifyCustomer(1, customerMsg)
        }
    }

    @Test
    fun `chargeInvoice should notify admin on CustomerNotFoundException`() = runBlocking {
        val invoice = Invoice(
                id = 1,
                customerId = 1,
                amount = Money(BigDecimal.ONE, Currency.USD),
                status = InvoiceStatus.PENDING
        )
        val adminMsg = "Customer in invoice with ID: ${invoice.id} does not exist"

        every { paymentProvider.charge(invoice) } throws CustomerNotFoundException(1)
        every { notificationService.notifyAdmin(adminMsg) } just Runs
        val billSpy = spyk(billingService)
        billSpy.chargeInvoice(invoice)

        verify {
            notificationService.notifyAdmin(adminMsg)
        }
    }

    @Test
    fun `chargeInvoice should notify admin on CurrencyMismatchException`() = runBlocking {
        val invoice = Invoice(
                id = 1,
                customerId = 1,
                amount = Money(BigDecimal.ONE, Currency.USD),
                status = InvoiceStatus.PENDING
        )
        val adminMsg = "Invoice with ID: ${invoice.id} has mismatching currency invoice currency ${invoice.amount.currency}, customer currency ${Currency.EUR}"

        every { customerService.fetch(invoice.customerId) } returns Customer(1, Currency.EUR)
        every { notificationService.notifyAdmin(adminMsg) } just Runs
        every { paymentProvider.charge(invoice) } throws CurrencyMismatchException(invoice.id, invoice.customerId)

        val billSpy = spyk(billingService)
        billSpy.chargeInvoice(invoice)

        verify {
            customerService.fetch(invoice.customerId)
            notificationService.notifyAdmin(adminMsg)
        }
    }

    @Test
    fun `chargeInvoice should retry on NetworkException`() = runBlocking {
        val invoice = Invoice(
                id = 1,
                customerId = 1,
                amount = Money(BigDecimal.ONE, Currency.USD),
                status = InvoiceStatus.PENDING
        )

        every { paymentProvider.charge(invoice) } throws NetworkException()
        val billSpy = spyk(billingService) {
            coEvery { retryChargeInvoice(invoice) } returns Job()
        }
        billSpy.chargeInvoice(invoice)

        coVerify {
            billSpy.retryChargeInvoice(invoice)
        }
    }

    @Test
    fun `chargeInvoice should notify admin on any other unexpected Exception`() = runBlocking {
        val invoice = Invoice(
                id = 1,
                customerId = 1,
                amount = Money(BigDecimal.ONE, Currency.USD),
                status = InvoiceStatus.PENDING
        )
        val adminMsg = "Unexpected error happened while processing invoice ID : ${invoice.id}"

        every { paymentProvider.charge(invoice) } throws Exception()
        every { notificationService.notifyAdmin(adminMsg) } just Runs

        val billSpy = spyk(billingService)
        billSpy.chargeInvoice(invoice)

        verify {
            notificationService.notifyAdmin(adminMsg)
        }
    }
}