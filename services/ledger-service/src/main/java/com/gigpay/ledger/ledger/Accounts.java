package com.gigpay.ledger.ledger;

/** Chart of accounts. */
public final class Accounts {

    public static final String BANK_FLOAT = "bank:float";
    public static final String IN_TRANSIT = "clearing:payouts-in-transit";
    public static final String FEE_INCOME = "income:payout-fees";
    public static final String CAPITAL = "equity:capital";

    private Accounts() {
    }

    /** What GigPay owes the worker: earned, not yet paid out. */
    public static String workerPayable(String workerId) {
        return "worker:" + workerId + ":payable";
    }

    /** What a platform owes GigPay for its workers' accrued earnings, until it settles. */
    public static String platformReceivable(String platform) {
        return "platform:" + platform + ":receivable";
    }
}
