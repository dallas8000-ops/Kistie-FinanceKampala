package com.financeapp.uganda.utils;

import android.content.Context;
import android.content.SharedPreferences;
import com.financeapp.uganda.BuildConfig;

public class PrefsHelper {
    private static final String PREFS = "kistie_finance_prefs";
    private static final String KEY_OPENAI = "openai_api_key";
    private static final String KEY_MTN_NUMBER = "mtn_mobile_money_number";
    private static final String KEY_AIRTEL_NUMBER = "airtel_money_number";

    public static void saveApiKey(Context ctx, String key) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_OPENAI, key.trim()).apply();
    }

    public static String getApiKey(Context ctx) {
        String deviceKey = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_OPENAI, "");
        return deviceKey == null || deviceKey.trim().isEmpty() ? BuildConfig.OPENAI_API_KEY : deviceKey;
    }

    public static void saveMtnMobileMoneyNumber(Context ctx, String number) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_MTN_NUMBER, number == null ? "" : number.trim()).apply();
    }

    public static String getMtnMobileMoneyNumber(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MTN_NUMBER, "");
    }

    public static void saveAirtelMoneyNumber(Context ctx, String number) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_AIRTEL_NUMBER, number == null ? "" : number.trim()).apply();
    }

    public static String getAirtelMoneyNumber(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_AIRTEL_NUMBER, "");
    }

    private static final String KEY_CAPITAL_SET = "capital_set";

    public static void setCapitalConfigured(Context ctx, boolean configured) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_CAPITAL_SET, configured).apply();
    }

    public static boolean isCapitalConfigured(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_CAPITAL_SET, false);
    }
}
