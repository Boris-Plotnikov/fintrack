export type Role = "OWNER" | "MANAGER" | "EMPLOYEE";
export type OperationKind = "INCOME" | "EXPENSE" | "OWNER_DEPOSIT" | "OWNER_WITHDRAWAL" | "TRANSFER_OUT" | "TRANSFER_IN" | "CORRECTION";

export interface Me {
  authenticated: boolean;
  demoMode: boolean;
  id?: number;
  username?: string;
  fullName?: string;
  role?: Role;
  office?: string;
  officeId?: number;
}

export interface Account {
  id: number;
  name: string;
  type: "BANK" | "CASH";
  currency: string;
  office: string;
  owner: string;
  manager: string;
  balance: number;
  balanceEur: number;
  tolerance: number;
  active: boolean;
}

export interface Category {
  id: number;
  name: string;
  kind: "INCOME" | "EXPENSE";
}

export interface Operation {
  id: number;
  kind: OperationKind;
  accountId: number;
  account: string;
  office: string;
  category?: string;
  amount: number;
  effectiveAmount: number;
  currency: string;
  rateToEur: number;
  effectiveAmountEur: number;
  description: string;
  counterparty?: string;
  operationAt: string;
  createdBy: string;
  createdAt: string;
  source: string;
  transferId?: number;
  status: "ACTIVE" | "CORRECTED" | "CANCELLED";
  corrections: { id: number; delta: number; reason: string; by: string; at: string }[];
}

export interface Draft {
  kind: OperationKind;
  amount?: number;
  currency?: string;
  accountId?: number;
  accountName?: string;
  categoryId?: number;
  categoryName?: string;
  counterparty?: string;
  description: string;
  warnings: string[];
}

export interface Transfer {
  id: number;
  fromAccountId: number;
  fromAccount: string;
  fromCurrency: string;
  toAccountId: number;
  toAccount: string;
  toCurrency: string;
  toManager: string;
  toManagerId: number;
  amount: number;
  amountReceived: number;
  status: "PENDING" | "CONFIRMED" | "REJECTED";
  comment?: string;
  createdBy: string;
  createdAt: string;
  decidedBy?: string;
  decidedAt?: string;
}

export interface Anomaly {
  id: number;
  type: string;
  severity: "LOW" | "MEDIUM" | "HIGH";
  message: string;
  operationId?: number;
  status: "NEW" | "REVIEWED" | "RESOLVED";
  createdAt: string;
  resolvedBy?: { fullName: string };
  resolutionNote?: string;
}

export interface AuditEntry {
  id: number;
  at: string;
  username: string;
  action: string;
  entity: string;
  entityId?: number;
  details: string;
}

export interface Line {
  name: string;
  amountEur: number;
}

export interface Monthly {
  month: string;
  income: number;
  expense: number;
  profit: number;
  turnover: number;
  incomeByCategory: Line[];
  expenseByCategory: Line[];
  profitByOffice: Line[];
  daily: { date: string; income: number; expense: number }[];
  operations: number;
}

export interface Dashboard {
  currentMonth: Monthly;
  previousMonth: Monthly;
  totalBalanceEur: number;
  inTransitEur: number;
  pendingTransfers: number;
  openAnomalies: number;
  accounts: Account[];
  activity: AuditEntry[];
}

export interface Page<T> {
  content: T[];
  page?: { totalElements: number; totalPages: number };
  totalElements?: number;
  totalPages?: number;
}
