package com.financeapp.uganda.utils;

import java.util.ArrayList;
import java.util.List;

public final class PaymentMethods {
    public static final String CASH = "Cash";
    public static final String MTN_MOMO = "MTN MoMo";
    public static final String AIRTEL_MONEY = "Airtel Money";
    public static final String BANK_TRANSFER = "Bank Transfer";
    public static final String CHEQUE = "Cheque";
    public static final String CREDIT = "Credit";

    private PaymentMethods() {}

    public static String[] options(boolean includeCredit, boolean includeCheque) {
        List<String> methods = new ArrayList<>();
        methods.add(CASH);
        methods.add(MTN_MOMO);
        methods.add(AIRTEL_MONEY);
        methods.add(BANK_TRANSFER);
        if (includeCheque) methods.add(CHEQUE);
        if (includeCredit) methods.add(CREDIT);
        return methods.toArray(new String[0]);
    }

    public static boolean isMobileMoney(String method) {
        return MTN_MOMO.equals(method) || AIRTEL_MONEY.equals(method) || "Mobile Money".equals(method);
    }

    public static boolean isRecordedMethod(String method) {
        return CASH.equals(method) || isMobileMoney(method)
                || BANK_TRANSFER.equals(method) || CHEQUE.equals(method);
    }
}