package com.example.data

import android.util.Log
import kotlinx.coroutines.delay

/**
 * South African Bank Names supported for Direct EFT / Instant EFT payouts.
 */
enum class SABankName(val displayName: String, val defaultBranchCode: String) {
    STANDARD_BANK("Standard Bank", "051001"),
    ABSA("ABSA", "632005"),
    FNB("First National Bank (FNB)", "250655"),
    CAPITEC("Capitec Bank", "470010"),
    NEDBANK("Nedbank", "198765"),
    TYMEBANK("TymeBank", "678910"),
    DISCOVERY_BANK("Discovery Bank", "254005")
}

data class BankAccountDetails(
    val bankName: SABankName,
    val accountNumber: String,
    val branchCode: String,
    val accountHolderName: String,
    val accountType: String // "Savings", "Current", "Transmission"
)

data class PayoutResult(
    val success: Boolean,
    val transactionId: String?,
    val status: String,
    val message: String,
    val zarAmount: Double,
    val usdAmount: Double,
    val providerReference: String?
)

/**
 * Service preparing the backend for future South African payment providers (e.g., Stitch, Ozow, PayFast).
 * Handles mock FX conversion from USD to ZAR and models actual secure payout endpoints.
 */
class SouthAfricanPaymentProviderService {
    private val TAG = "SAPaymentService"
    
    // Standard USD to ZAR conversion rate (e.g. $1 = R18.50)
    private val USD_TO_ZAR_RATE = 18.50

    /**
     * Converts rewards from USD to ZAR.
     */
    fun convertUsdToZar(usdAmount: Double): Double {
        return usdAmount * USD_TO_ZAR_RATE
    }

    /**
     * Simulates initiating a payout using Ozow or Stitch Instant EFT payout APIs.
     * In a production environment, this function will exchange credentials for a bearer token,
     * calculate request payload HMAC signatures, and execute a secure HTTP POST request.
     */
    suspend fun processInstantEFTPayout(
        account: BankAccountDetails,
        usdAmount: Double,
        reference: String
    ): PayoutResult {
        val zarAmount = convertUsdToZar(usdAmount)
        Log.i(TAG, "Initiating Instant EFT payout of R${String.format("%.2f", zarAmount)} ($$usdAmount) to ${account.accountHolderName} at ${account.bankName.displayName}")

        // Simulate API call processing latency
        delay(1500)

        // Generate a real-looking reference ID (Stitch/Ozow format)
        val transactionId = "TXN-ZA-" + System.currentTimeMillis().toString().takeLast(8) + "-" + (1000..9999).random()
        val providerRef = "REF-" + (100000..999999).random()

        return if (account.accountNumber.length in 8..13) {
            Log.i(TAG, "Payout succeeded. Reference: $providerRef, TxnId: $transactionId")
            PayoutResult(
                success = true,
                transactionId = transactionId,
                status = "PAID",
                message = "Instant EFT payment of R${String.format("%.2f", zarAmount)} processed successfully to ${account.bankName.displayName}.",
                zarAmount = zarAmount,
                usdAmount = usdAmount,
                providerReference = providerRef
            )
        } else {
            Log.w(TAG, "Payout failed: Invalid account number length.")
            PayoutResult(
                success = false,
                transactionId = null,
                status = "FAILED",
                message = "Failed to process payout: Invalid South African bank account number.",
                zarAmount = zarAmount,
                usdAmount = usdAmount,
                providerReference = null
            )
        }
    }
}
