package com.financeapp.uganda.sync;

import com.financeapp.uganda.database.FinanceDao;
import com.financeapp.uganda.models.SyncOutboxEntry;
import java.util.List;

public final class SyncOutboxDrainer {
    private static final int BATCH_SIZE = 50;
    private static final long REFRESH_BEFORE_EXPIRY_MILLIS = 60_000L;
    private static final long INITIAL_RETRY_DELAY_MILLIS = 30_000L;
    private static final long MAX_RETRY_DELAY_MILLIS = 6 * 60 * 60 * 1000L;
    private final FinanceDao dao;
    private final SupabaseSessionStore sessions;
    private final SupabaseConfig config;
    private final SupabaseAuthClient authClient;
    private final FinanceSyncClient syncClient;

    public SyncOutboxDrainer(FinanceDao dao, SupabaseSessionStore sessions,
            SupabaseConfig config, SupabaseAuthClient authClient, FinanceSyncClient syncClient) {
        this.dao = dao;
        this.sessions = sessions;
        this.config = config;
        this.authClient = authClient;
        this.syncClient = syncClient;
    }

    public Outcome drain() {
        if (config == null || !config.isConfigured()) return Outcome.NOT_CONFIGURED;
        SupabaseSessionStore.Session session = sessions.getSession();
        if (session == null) {
            return dao.getPendingSyncCount() > 0 ? Outcome.NEEDS_SIGN_IN : Outcome.COMPLETE;
        }

        if (session.getExpiresAtMillis() <= System.currentTimeMillis() + REFRESH_BEFORE_EXPIRY_MILLIS) {
            SupabaseAuthClient.Result refresh = refreshSession(session);
            if (!refresh.isSuccess()) return refreshFailureOutcome(refresh);
            session = refresh.getSession();
        }
        return drainPending(session);
    }

    private Outcome drainPending(SupabaseSessionStore.Session session) {
        List<SyncOutboxEntry> pending = dao.getPendingSyncEntries(System.currentTimeMillis(), BATCH_SIZE);
        for (SyncOutboxEntry entry : pending) {
            FinanceSyncClient.Result result = syncClient.push(entry, session.getAccessToken());
            if (result.getStatus() == FinanceSyncClient.Status.AUTH_REQUIRED) {
                SupabaseAuthClient.Result refresh = refreshSession(session);
                if (!refresh.isSuccess()) {
                    return handleRefreshFailure(entry, refresh);
                }
                session = refresh.getSession();
                result = syncClient.push(entry, session.getAccessToken());
            }
            Outcome outcome = persistPushResult(entry, result);
            if (outcome != Outcome.COMPLETE) return outcome;
        }
        return Outcome.COMPLETE;
    }

    private SupabaseAuthClient.Result refreshSession(SupabaseSessionStore.Session session) {
        SupabaseAuthClient.Result result = authClient.refresh(session.getRefreshToken());
        if (result.isSuccess()) {
            SupabaseSessionStore.Session refreshed = result.getSession();
            sessions.saveSession(refreshed.getAccessToken(), refreshed.getRefreshToken(),
                    refreshed.getExpiresAtMillis());
        }
        return result;
    }

    private Outcome refreshFailureOutcome(SupabaseAuthClient.Result result) {
        return isAuthFailure(result.getHttpCode()) ? Outcome.NEEDS_SIGN_IN : Outcome.RETRY;
    }

    private Outcome handleRefreshFailure(SyncOutboxEntry entry, SupabaseAuthClient.Result result) {
        Outcome outcome = refreshFailureOutcome(result);
        if (outcome == Outcome.NEEDS_SIGN_IN) {
            dao.markSyncOperationAuthRequired(entry.getId(), safeHttpError(result.getHttpCode()));
        }
        return outcome;
    }

    private Outcome persistPushResult(SyncOutboxEntry entry, FinanceSyncClient.Result result) {
        String error = safeHttpError(result.getHttpCode());
        switch (result.getStatus()) {
            case ACKNOWLEDGED:
                dao.markSyncOperationAcknowledged(entry.getId());
                return Outcome.COMPLETE;
            case CONFLICT:
                dao.markSyncOperationConflict(entry.getId(), error);
                return Outcome.CONFLICT;
            case RETRY:
                long nextAttemptAt = System.currentTimeMillis() + retryDelay(entry.getAttempts() + 1);
                dao.scheduleSyncOperationRetry(entry.getId(), nextAttemptAt, error);
                return Outcome.RETRY;
            case AUTH_REQUIRED:
                dao.markSyncOperationAuthRequired(entry.getId(), error);
                return Outcome.NEEDS_SIGN_IN;
            case REJECTED:
                dao.markSyncOperationRejected(entry.getId(), error);
                return Outcome.REJECTED;
            case NOT_CONFIGURED:
                return Outcome.NOT_CONFIGURED;
            default:
                return Outcome.RETRY;
        }
    }

    private long retryDelay(int attempt) {
        int shift = Math.min(Math.max(attempt - 1, 0), 10);
        return Math.min(INITIAL_RETRY_DELAY_MILLIS << shift, MAX_RETRY_DELAY_MILLIS);
    }

    private boolean isAuthFailure(int status) {
        return status == 400 || status == 401 || status == 403;
    }

    private String safeHttpError(int status) {
        return status > 0 ? "HTTP " + status : "Network or response error";
    }

    public enum Outcome {
        COMPLETE,
        NOT_CONFIGURED,
        NEEDS_SIGN_IN,
        RETRY,
        CONFLICT,
        REJECTED
    }
}
