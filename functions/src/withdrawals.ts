import * as admin from 'firebase-admin';
import { 
  UserProfile, 
  WithdrawalRequest, 
  WithdrawalStatus, 
  AuditLog, 
  FraudCheckResult 
} from './types';

// Constants
const DUP_PREVENTION_WINDOW_MS = 5 * 60 * 1000; // 5 minutes prevention window
const MAX_DAILY_WITHDRAWAL_COUNT = 3; // Daily withdrawal limit per user
const RISK_MAX_WITHDRAWAL_AMOUNT = 150.0; // Withdrawals larger than $150.0 require manual compliance hold
const USD_TO_ZAR_RATE = 18.50; // Mock real-time ZAR / USD FX rate

/**
 * Helper to log security audit entries to Firestore.
 */
async function writeAuditLog(
  db: admin.firestore.Firestore,
  action: string,
  userId: number,
  userEmail: string,
  amount: number | undefined,
  withdrawalId: string | undefined,
  performedBy: string,
  details: string
): Promise<void> {
  const auditRef = db.collection('audit_logs').doc();
  const log: AuditLog = {
    id: auditRef.id,
    action,
    userId,
    userEmail,
    amount,
    withdrawalId,
    performedBy,
    timestamp: Date.now(),
    details
  };
  await auditRef.set(log);
  console.log(`[AUDIT] Action: ${action} | User: ${userEmail} | Details: ${details}`);
}

/**
 * Runs a dynamic Fraud and Risk Assessment check on the user profile and request details.
 */
function evaluateFraudRisk(
  user: UserProfile, 
  amount: number, 
  recentRequestsCount: number
): FraudCheckResult {
  // 1. Check if user is already flagged/banned
  if (user.isBanned) {
    return { passed: false, reason: 'User account has been flagged or banned for safety violations.', fraudScore: 100 };
  }

  // 2. Evaluate historical risk score
  const score = user.fraudScore || 0;
  if (score >= 80) {
    return { passed: false, reason: 'Account security rating is too low. Please contact support.', fraudScore: score };
  }

  // 3. Limit the speed/frequency of withdrawal requests (Velocity Limits)
  if (recentRequestsCount >= MAX_DAILY_WITHDRAWAL_COUNT) {
    return { passed: false, reason: `Daily payout velocity limit reached. Maximum is ${MAX_DAILY_WITHDRAWAL_COUNT} withdrawals per 24 hours.`, fraudScore: 75 };
  }

  // 4. Large single withdrawal warning threshold
  if (amount > RISK_MAX_WITHDRAWAL_AMOUNT) {
    return { 
      passed: true, 
      reason: 'Large withdrawal amount flagged for manual administrator compliance check.', 
      fraudScore: 50 
    };
  }

  return { passed: true, fraudScore: 10 };
}

/**
 * Main handler to process a incoming withdrawal request securely.
 * Utilizes atomic Firestore transactions to avoid race conditions (double withdrawals)
 * and verifies balances, duplicate clicks, and fraud flags.
 */
export async function handleWithdrawalSubmit(
  db: admin.firestore.Firestore,
  data: {
    userId: number;
    userEmail: string;
    amount: number;
    paymentMethod: string;
    accountDetails: string;
  },
  callerEmail: string
): Promise<{ success: boolean; message: string; withdrawalId?: string }> {
  const { userId, userEmail, amount, paymentMethod, accountDetails } = data;

  // Basic validation
  if (!userId || !userEmail || !amount || amount <= 0 || !paymentMethod || !accountDetails) {
    return { success: false, message: 'Invalid or incomplete request parameters.' };
  }

  const userQuery = db.collection('users').where('email', '==', userEmail).limit(1);

  // 1. Run a check to prevent identical double-clicks within the prevention window
  const duplicateQuery = await db.collection('withdrawals')
    .where('userId', '==', userId)
    .where('amount', '==', amount)
    .orderBy('requestedAt', 'desc')
    .limit(1)
    .get();

  if (!duplicateQuery.empty) {
    const lastRequest = duplicateQuery.docs[0].data() as WithdrawalRequest;
    const timeDiff = Date.now() - lastRequest.requestedAt;
    if (timeDiff < DUP_PREVENTION_WINDOW_MS && (lastRequest.status !== 'REJECTED')) {
      return { 
        success: false, 
        message: `Duplicate request detected. Please wait at least ${DUP_PREVENTION_WINDOW_MS / 60000} minutes between identical withdrawal amounts.` 
      };
    }
  }

  // 2. Fetch the counts for velocity check (withdrawals requested in the last 24 hours)
  const oneDayAgo = Date.now() - (24 * 60 * 60 * 1000);
  const recentRequestsQuery = await db.collection('withdrawals')
    .where('userId', '==', userId)
    .where('requestedAt', '>=', oneDayAgo)
    .get();
  
  const recentCount = recentRequestsQuery.size;

  // 3. Initiate an atomic Transaction to check balance, adjust wallet and submit document
  try {
    const result = await db.runTransaction(async (transaction) => {
      const userDocs = await transaction.get(userQuery);
      if (userDocs.empty) {
        throw new Error('User profile not found in database.');
      }

      const userDoc = userDocs.docs[0];
      const userProfile = userDoc.data() as UserProfile;

      // Ensure the balance is sufficient
      if (userProfile.walletBalance < amount) {
        throw new Error(`Insufficient wallet balance. Current: $${userProfile.walletBalance.toFixed(2)}, Requested: $${amount.toFixed(2)}`);
      }

      // Fraud analysis
      const fraudCheck = evaluateFraudRisk(userProfile, amount, recentCount);
      if (!fraudCheck.passed) {
        throw new Error(`Transaction declined by security subsystem: ${fraudCheck.reason}`);
      }

      // Deduct the balance securely
      const newBalance = userProfile.walletBalance - amount;
      transaction.update(userDoc.ref, { 
        walletBalance: newBalance,
        lastWithdrawalTime: Date.now()
      });

      // Prepare withdrawal request document
      const withdrawalRef = db.collection('withdrawals').doc();
      const newWithdrawal: WithdrawalRequest = {
        id: withdrawalRef.id,
        userId,
        username: userProfile.username,
        userEmail: userProfile.email,
        amount,
        paymentMethod,
        accountDetails,
        status: fraudCheck.fraudScore >= 50 ? 'UNDER_REVIEW' : 'SUBMITTED', // Auto flag suspicious to review
        requestedAt: Date.now(),
        updatedAt: Date.now()
      };

      transaction.set(withdrawalRef, newWithdrawal);

      return {
        id: withdrawalRef.id,
        status: newWithdrawal.status,
        newBalance
      };
    });

    // 4. Record successful audit logs outside of transaction
    await writeAuditLog(
      db,
      'WITHDRAWAL_SUBMITTED',
      userId,
      userEmail,
      amount,
      result.id,
      callerEmail,
      `Successfully processed withdrawal submission. Status: ${result.status}. Wallet adjusted to $${result.newBalance.toFixed(2)}.`
    );

    return { 
      success: true, 
      message: result.status === 'UNDER_REVIEW' 
        ? 'Withdrawal request submitted but flagged for compliance review.' 
        : 'Withdrawal request submitted successfully.', 
      withdrawalId: result.id 
    };

  } catch (err: any) {
    console.error(`[TXN ERROR] Submission failed: ${err.message}`);
    
    // Log failure attempt for security monitoring
    await writeAuditLog(
      db,
      'WITHDRAWAL_FAILED_SUBMISSION',
      userId,
      userEmail,
      amount,
      undefined,
      callerEmail,
      `Failed submission attempt: ${err.message}`
    );

    return { success: false, message: err.message || 'Internal processing error occurred.' };
  }
}

/**
 * South African Payment Provider EFT payouts simulation logic.
 * Prepares backend infrastructure for Ozow, Stitch, PayFast, or Peach Payments direct settlement integrations.
 */
async function triggerSouthAfricanEFT(
  withdrawal: WithdrawalRequest
): Promise<{ success: boolean; payoutReference: string; details: string }> {
  const zarAmount = withdrawal.amount * USD_TO_ZAR_RATE;
  console.log(`[EFT PAYOUT] Exchanging FX: Converting $${withdrawal.amount} to R${zarAmount.toFixed(2)} at ZAR/USD ${USD_TO_ZAR_RATE}`);
  
  /**
   * --- STITCH / OZOW PRODUCTION CODE INTEGRATION NOTE ---
   *
   * In production, this integration will perform the following steps:
   * 1. Exchange client credentials for an access token:
   *    const tokenResponse = await axios.post('https://api.stitch.money/connect/token', {
   *      grant_type: 'client_credentials',
   *      client_id: process.env.STITCH_CLIENT_ID,
   *      audience: 'https://api.stitch.money/payments',
   *      client_assertion_type: 'urn:ietf:params:oauth:client-assertion-type:jwt-bearer',
   *      client_assertion: generateSignedClientAssertionJwt()
   *    });
   *    const accessToken = tokenResponse.data.access_token;
   *
   * 2. Construct and sign payment initiate request payload with private keys:
   *    const payoutPayload = {
   *      amount: { currency: "ZAR", value: Math.round(zarAmount * 100) }, // in cents
   *      destination: {
   *        bankAccount: {
   *          bankId: getBankStitchId(withdrawal.accountDetails.bank),
   *          accountNumber: withdrawal.accountDetails.accountNumber,
   *          branchCode: withdrawal.accountDetails.branchCode,
   *          holderName: withdrawal.username
   *        }
   *      },
   *      reference: `REWARD-${withdrawal.id}`
   *    };
   *
   * 3. Submit secure HTTP request with HMAC verification headers:
   *    const response = await axios.post('https://api.stitch.money/payments/payouts', payoutPayload, {
   *      headers: { Authorization: `Bearer ${accessToken}` }
   *    });
   */

  // Simulating payment network routing processing delay...
  await new Promise(resolve => setTimeout(resolve, 800));

  // Simulating 95% success rate for validation routing
  const validAccountPattern = /^[0-9]+$/;
  const isAccountValid = validAccountPattern.test(withdrawal.accountDetails.trim());

  if (isAccountValid) {
    const reference = 'PAY-ZA-' + Math.random().toString(36).substr(2, 9).toUpperCase();
    return {
      success: true,
      payoutReference: reference,
      details: `Successfully settled Instant EFT payout of R${zarAmount.toFixed(2)} to South African bank. Ref: ${reference}`
    };
  } else {
    return {
      success: false,
      payoutReference: '',
      details: 'Failed to route Instant EFT: South African banking account pattern contains non-numeric characters.'
    };
  }
}

/**
 * Handles status updates from Administrators including State workflow transitions and refund processing.
 */
export async function handleAdminUpdateStatus(
  db: admin.firestore.Firestore,
  withdrawalId: string,
  newStatus: WithdrawalStatus,
  adminUserEmail: string,
  rejectionReason?: string
): Promise<{ success: boolean; message: string }> {
  if (!withdrawalId || !newStatus) {
    return { success: false, message: 'Missing withdrawal ID or targeted status.' };
  }

  const withdrawalRef = db.collection('withdrawals').document(withdrawalId);

  try {
    const result = await db.runTransaction(async (transaction) => {
      const withdrawalDoc = await transaction.get(withdrawalRef);
      if (!withdrawalDoc.exists) {
        throw new Error('Withdrawal request record not found.');
      }

      const withdrawal = withdrawalDoc.data() as WithdrawalRequest;
      const currentStatus = withdrawal.status;

      // Prevent transitions from terminal states
      if (currentStatus === 'PAID' || currentStatus === 'REJECTED') {
        throw new Error(`Cannot modify transaction already settled in terminal status: ${currentStatus}`);
      }

      if (currentStatus === newStatus) {
        throw new Error(`Transaction is already marked as ${newStatus}`);
      }

      // 1. Process REJECTED status transition (Refund User Balance atomically)
      if (newStatus === 'REJECTED') {
        const userQuery = db.collection('users').where('email', '==', withdrawal.userEmail).limit(1);
        const userDocs = await transaction.get(userQuery);
        
        if (userDocs.empty) {
          throw new Error('Associated user profile not found. Cannot securely execute refund.');
        }

        const userDoc = userDocs.docs[0];
        const userProfile = userDoc.data() as UserProfile;
        const refundedBalance = userProfile.walletBalance + withdrawal.amount;

        // Refund user
        transaction.update(userDoc.ref, { walletBalance: refundedBalance });
        // Set withdrawal to REJECTED
        transaction.update(withdrawalRef, {
          status: 'REJECTED',
          rejectionReason: rejectionReason || 'Declined by security compliance operations.',
          updatedAt: Date.now(),
          reviewedBy: adminUserEmail
        });

        return { 
          action: 'REFUND_EXECUTED', 
          message: `Withdrawal rejected and successfully refunded $${withdrawal.amount.toFixed(2)} to ${withdrawal.userEmail}.` 
        };
      }

      // 2. Process PAID status transition directly (or via APPROVED)
      if (newStatus === 'PAID') {
        transaction.update(withdrawalRef, {
          status: 'PAID',
          updatedAt: Date.now(),
          reviewedBy: adminUserEmail
        });
        return { 
          action: 'SETTLEMENT_COMPLETE', 
          message: `Withdrawal successfully marked as Paid/Settled by Administrator: ${adminUserEmail}.` 
        };
      }

      // 3. Process APPROVED status transition (Initiate South African EFT API calling pipeline)
      if (newStatus === 'APPROVED') {
        // Trigger payment settlement
        transaction.update(withdrawalRef, {
          status: 'APPROVED',
          updatedAt: Date.now(),
          reviewedBy: adminUserEmail
        });
        return { 
          action: 'APPROVED_PENDING_SETTLEMENT', 
          message: 'Withdrawal approved. Status set to Approved; initiating secure payout routing.' 
        };
      }

      // 4. Process UNDER_REVIEW transition
      if (newStatus === 'UNDER_REVIEW') {
        transaction.update(withdrawalRef, {
          status: 'UNDER_REVIEW',
          updatedAt: Date.now(),
          reviewedBy: adminUserEmail
        });
        return { 
          action: 'UNDER_REVIEW_MARK', 
          message: 'Withdrawal marked under compliance review successfully.' 
        };
      }

      // Catch-all
      transaction.update(withdrawalRef, {
        status: newStatus,
        updatedAt: Date.now()
      });
      return { 
        action: 'STATUS_MODIFIED', 
        message: `Withdrawal transitioned to ${newStatus}.` 
      };
    });

    // Handle background automated action if approved
    if (newStatus === 'APPROVED') {
      const withdrawalDoc = await withdrawalRef.get();
      const currentWithdrawal = withdrawalDoc.data() as WithdrawalRequest;
      
      // Execute the South African EFT provider transaction integration asynchronously
      const eftResult = await triggerSouthAfricanEFT(currentWithdrawal);
      if (eftResult.success) {
        // Automatically settle to PAID once EFT succeeds
        await withdrawalRef.update({
          status: 'PAID',
          payoutTxnId: eftResult.payoutReference,
          zarPayoutAmount: currentWithdrawal.amount * USD_TO_ZAR_RATE,
          updatedAt: Date.now()
        });

        await writeAuditLog(
          db,
          'WITHDRAWAL_AUTO_PAID_EFT',
          currentWithdrawal.userId,
          currentWithdrawal.userEmail,
          currentWithdrawal.amount,
          withdrawalId,
          'SYSTEM_PAYMENT_ROUTE',
          `Automated EFT settled via Stitch/Ozow. Reference: ${eftResult.payoutReference}. Equivalent payout value: R${(currentWithdrawal.amount * USD_TO_ZAR_RATE).toFixed(2)}`
        );
      } else {
        // If settlement routing fails, set status back to UNDER_REVIEW so Admin can investigate banking details
        await withdrawalRef.update({
          status: 'UNDER_REVIEW',
          rejectionReason: `EFT Settlement routing failure: ${eftResult.details}`,
          updatedAt: Date.now()
        });

        await writeAuditLog(
          db,
          'WITHDRAWAL_SETTLEMENT_FAILED',
          currentWithdrawal.userId,
          currentWithdrawal.userEmail,
          currentWithdrawal.amount,
          withdrawalId,
          'SYSTEM_PAYMENT_ROUTE',
          `Payment router failed payout settlement: ${eftResult.details}`
        );
      }
    } else {
      // Record audits for non-APPROVED transitions
      const updatedDoc = await withdrawalRef.get();
      const currentWithdrawal = updatedDoc.data() as WithdrawalRequest;
      await writeAuditLog(
        db,
        `WITHDRAWAL_STATE_CHANGE_${newStatus}`,
        currentWithdrawal.userId,
        currentWithdrawal.userEmail,
        currentWithdrawal.amount,
        withdrawalId,
        adminUserEmail,
        result.message
      );
    }

    return { success: true, message: result.message };

  } catch (err: any) {
    console.error(`[TXN ERROR] State modification failed: ${err.message}`);
    return { success: false, message: err.message || 'Error occurred during status update transaction.' };
  }
}
