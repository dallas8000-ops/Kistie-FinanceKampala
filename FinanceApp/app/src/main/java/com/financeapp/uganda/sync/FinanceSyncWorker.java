package com.financeapp.uganda.sync;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.financeapp.uganda.database.AppDatabase;

public final class FinanceSyncWorker extends Worker {
    public FinanceSyncWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            SupabaseConfig config = SupabaseConfig.fromBuildConfig();
            if (!config.isConfigured()) return Result.success();
            AppDatabase database = AppDatabase.getInstance(getApplicationContext());
            SupabaseSessionStore sessions = new SupabaseSessionStore(getApplicationContext());
            SyncOutboxDrainer drainer = new SyncOutboxDrainer(database.financeDao(), sessions,
                    config, new SupabaseAuthClient(config), new FinanceSyncClient(config));
            SyncOutboxDrainer.Outcome outcome = drainer.drain();
            if (outcome == SyncOutboxDrainer.Outcome.RETRY) return Result.retry();
            if (outcome == SyncOutboxDrainer.Outcome.NEEDS_SIGN_IN
                    || outcome == SyncOutboxDrainer.Outcome.CONFLICT
                    || outcome == SyncOutboxDrainer.Outcome.REJECTED) {
                return Result.success();
            }
            return Result.success();
        } catch (RuntimeException e) {
            return Result.retry();
        }
    }
}
