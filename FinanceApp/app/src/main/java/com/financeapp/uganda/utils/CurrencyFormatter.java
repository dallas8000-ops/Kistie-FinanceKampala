package com.financeapp.uganda.utils;

import java.text.NumberFormat;
import java.util.Locale;

public class CurrencyFormatter {

    public static double USD_TO_UGX = 3700;
    public static double TRY_TO_UGX = 120;
    public static double CNY_TO_UGX = 510;

    public static String formatUGX(double amount) {
        NumberFormat format = NumberFormat.getCurrencyInstance(new Locale("en", "UG"));
        format.setMaximumFractionDigits(0);
        return format.format(amount).replace("USh", "UGX ");
    }

    public static String formatUSD(double amount) {
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.US);
        return format.format(amount);
    }

    public static String formatNumber(double amount) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setMaximumFractionDigits(0);
        return format.format(amount);
    }

    public static double convertToUGX(double amount, String fromCurrency) {
        if (fromCurrency == null) return amount;
        switch (fromCurrency) {
            case "USD": return amount * USD_TO_UGX;
            case "TRY": return amount * TRY_TO_UGX;
            case "CNY": return amount * CNY_TO_UGX;
            default: return amount;
        }
    }
}
