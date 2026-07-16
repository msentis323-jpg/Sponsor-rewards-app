/**
 * Secure Backend Types for Sponsor Rewards
 */

export type WithdrawalStatus = 'SUBMITTED' | 'UNDER_REVIEW' | 'APPROVED' | 'PAID' | 'REJECTED';

export interface UserProfile {
  id: number;
  username: string;
  email: string;
  walletBalance: number;
  isBanned?: boolean;
  fraudScore?: number; // Calculated dynamically or from historical scans
  lastWithdrawalTime?: number;
}

export interface WithdrawalRequest {
  id: string; // Document ID / Transaction ID
  userId: number;
  username: string;
  userEmail: string;
  amount: number;
  paymentMethod: string;
  accountDetails: string;
  status: WithdrawalStatus;
  requestedAt: number;
  updatedAt: number;
  reviewedBy?: string;
  rejectionReason?: string;
  payoutTxnId?: string; // South African Payment Provider reference (e.g. Stitch/Ozow)
  zarPayoutAmount?: number; // Equal value in ZAR if South African EFT is used
}

export interface AuditLog {
  id: string;
  action: string; // e.g. "WITHDRAWAL_SUBMITTED", "WITHDRAWAL_REVIEW_STARTED", "WITHDRAWAL_APPROVED", "WITHDRAWAL_PAID", "WITHDRAWAL_REJECTED", "BALANCE_ADJUSTED"
  userId: number;
  userEmail: string;
  amount?: number;
  withdrawalId?: string;
  performedBy: string; // "USER", "SYSTEM", or "ADMIN_EMAIL"
  timestamp: number;
  details: string;
  ipAddress?: string;
}

export interface FraudCheckResult {
  passed: boolean;
  reason?: string;
  fraudScore: number;
}
