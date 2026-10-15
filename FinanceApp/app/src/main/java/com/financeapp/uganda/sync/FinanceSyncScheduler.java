package com.financeapp.uganda.sync;

import android.content.Context;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;

public final class FinanceSyncScheduler {
    private static final String UNIQUE_WORK_NAME = "finance-owner-sync";

    private FinanceSyncScheduler() {}

    public static void schedule(Context context) {
        Context appContext = context.getApplicationContext();
        try {
            SupabaseConfig config = SupabaseConfig.fromBuildConfig();
            if (!config.isConfigured()
                    || new SupabaseSessionStore(appContext).getSession() == null) return;
            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build();
            OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(FinanceSyncWorker.class)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,
                            30, TimeUnit.SECONDS)
                    .build();
            WorkManager.getInstance(appContext).enqueueUniqueWork(
                    UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request);
        } catch (RuntimeException ignored) {
        }
    }
}
