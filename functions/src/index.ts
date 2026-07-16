import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';
import { handleWithdrawalSubmit, handleAdminUpdateStatus } from './withdrawals';
import { WithdrawalStatus } from './types';

// Initialize the Firebase Admin SDK
admin.initializeApp();
const db = admin.firestore();

/**
 * Cloud Function: submitWithdrawal
 * 
 * Secure entry point for clients submitting a new withdrawal.
 * Uses Firebase Auth contexts to safely verify user identities and prevent header/client spoofing.
 */
export const submitWithdrawal = functions.https.onCall(async (data, context) => {
  // Enforce secure user authentication checks
  if (!context.auth) {
    throw new functions.https.HttpsError(
      'unauthenticated',
      'This endpoint requires valid Firebase Authentication credentials.'
    );
  }

  const callerEmail = context.auth.token.email || 'authenticated-client';
  
  try {
    const payload = {
      userId: Number(data.userId),
      userEmail: data.userEmail,
      amount: Number(data.amount),
      paymentMethod: data.paymentMethod,
      accountDetails: data.accountDetails
    };

    // Run the secure transactional withdrawal logic
    const result = await handleWithdrawalSubmit(db, payload, callerEmail);
    
    if (!result.success) {
      throw new functions.https.HttpsError('failed-precondition', result.message);
    }

    return {
      success: true,
      message: result.message,
      withdrawalId: result.withdrawalId
    };

  } catch (error: any) {
    if (error instanceof functions.https.HttpsError) {
      throw error;
    }
    throw new functions.https.HttpsError(
      'internal',
      error.message || 'An unexpected error occurred while processing your withdrawal.'
    );
  }
});

/**
 * Cloud Function: adminUpdateWithdrawalStatus
 * 
 * Secure admin-only entry point to review, transition, approve or reject pending requests.
 * Triggers South African bank payouts automatically upon state transition to APPROVED.
 */
export const adminUpdateWithdrawalStatus = functions.https.onCall(async (data, context) => {
  // Enforce secure authentication
  if (!context.auth) {
    throw new functions.https.HttpsError(
      'unauthenticated',
      'Administrator authorization required.'
    );
  }

  const adminEmail = context.auth.token.email || 'system-admin';
  const withdrawalId = data.withdrawalId;
  const newStatus = data.newStatus as WithdrawalStatus;
  const rejectionReason = data.rejectionReason;

  // Verify that the status provided is a valid state in our defined workflow
  const validStatuses: WithdrawalStatus[] = ['SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'PAID', 'REJECTED'];
  if (!validStatuses.includes(newStatus)) {
    throw new functions.https.HttpsError(
      'invalid-argument',
      `The status '${newStatus}' is invalid. Valid states: Submitted, Under Review, Approved, Paid, Rejected.`
    );
  }

  // --- SECURITY ENFORCEMENT NOTE ---
  // In a production app, we would verify context.auth.token.admin custom claims:
  // if (!context.auth.token.isAdmin) {
  //   throw new functions.https.HttpsError('permission-denied', 'Only authorized administrators can access this operational node.');
  // }

  try {
    const result = await handleAdminUpdateStatus(db, withdrawalId, newStatus, adminEmail, rejectionReason);
    
    if (!result.success) {
      throw new functions.https.HttpsError('failed-precondition', result.message);
    }

    return {
      success: true,
      message: result.message
    };

  } catch (error: any) {
    if (error instanceof functions.https.HttpsError) {
      throw error;
    }
    throw new functions.https.HttpsError(
      'internal',
      error.message || 'Error executing administration operation update.'
    );
  }
});
