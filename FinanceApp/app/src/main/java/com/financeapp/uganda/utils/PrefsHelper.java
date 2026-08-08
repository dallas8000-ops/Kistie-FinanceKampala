package com.financeapp.uganda.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class PrefsHelper {
    private static final String PREFS = "kistie_finance_prefs";
    private static final String KEY_OPENAI = "openai_api_key";

    public static void saveApiKey(Context ctx, String key) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_OPENAI, key.trim()).apply();
    }

    public static String getApiKey(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_OPENAI, "");
    }

    private static final String KEY_CAPITAL_SET = "capital_set";

    public static void setCapitalConfigured(Context ctx, boolean configured) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_CAPITAL_SET, configured).apply();
    }

    public static boolean isCapitalConfigured(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_CAPITAL_SET, false);
    }
}
