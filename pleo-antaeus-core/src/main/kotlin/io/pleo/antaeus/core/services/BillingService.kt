package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.quartz.Job
import org.quartz.JobExecutionContext


public class BillingService(private val paymentProvider: PaymentProvider,
                            private val invoiceService: InvoiceService,
                            private val dal: AntaeusDal) : Job {

    val logger = KotlinLogging.logger { } //Maybe will be replaced with elastic search?


    override fun execute(context: JobExecutionContext?) {
        GlobalScope.launch {
            charge()
        }
    }

    private suspend fun charge() = coroutineScope {
        print("This is a quartz job!");
        val invoices = invoiceService.fetchPendingInvoices()
        print(invoices)

    }


}