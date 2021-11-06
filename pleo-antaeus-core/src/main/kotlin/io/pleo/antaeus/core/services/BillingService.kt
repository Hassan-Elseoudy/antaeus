package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.InvoiceStatus
import kotlinx.coroutines.*
import mu.KotlinLogging
import org.quartz.Job
import org.quartz.JobExecutionContext


public class BillingService(private val paymentProvider: PaymentProvider,
                            private val invoiceService: InvoiceService,
                            private val dal: AntaeusDal) : Job {

    private val logger = KotlinLogging.logger { } //Maybe will be replaced with elastic search?


    @OptIn(DelicateCoroutinesApi::class)
    override fun execute(context: JobExecutionContext?) {

        GlobalScope.launch {
            charge()
        }
    }

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->

        when (throwable) {
            is CustomerNotFoundException -> {
                logger.error(throwable) { "Customer does not exist $throwable" } // If customer doesn't exist, shouldn't we fix it?
            }

            is NetworkException -> {
                logger.error(throwable) { "Network error, with $throwable" } // Maybe we can have a retry here?
            }

            is CurrencyMismatchException -> {
                logger.error(throwable) { "Currency is not matching $throwable" } // Maybe we can go and update the Fix the invoice itself by sending an email?
            }

            else -> {
                logger.error(throwable) { "Uh, Oh, Something happened! $throwable" } // Should we have a generalized version?
            }
        }
        return@CoroutineExceptionHandler
    }


    private suspend fun charge() = coroutineScope {
        invoiceService.fetchPendingInvoices()
                .map { invoice ->
                    async(coroutineExceptionHandler) {
                        Pair(paymentProvider.charge(invoice), invoice)
                    }
                }
                .awaitAll() // Waiting for results
                .groupBy { it.first } // Group them into true, false based on charged!
                .forEach { entry ->  // Now the real job.
                    when (entry.key ){
                        true -> entry.value.forEach { dal.updateStatus(it. second.id, InvoiceStatus.PAID) }
                        false -> entry.value.forEach { dal.updateStatus(it.second.id, InvoiceStatus.NOT_CHARGED) }
                    }
                }
    }


}