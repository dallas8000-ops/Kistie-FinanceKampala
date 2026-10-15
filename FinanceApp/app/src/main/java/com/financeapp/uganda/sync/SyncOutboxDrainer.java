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
        if (session == null) return dao.getPendingSyncCount() > 0
                ? Outcome.NEEDS_SIGN_IN : Outcome.COMPLETE;

        if (session.getExpiresAtMillis() <= System.currentTimeMillis() + REFRESH_BEFORE_EXPIRY_MILLIS) {
            SupabaseAuthClient.Result refresh = authClient.refresh(session.getRefreshToken());
            if (!refresh.isSuccess()) {
                if (isAuthFailure(refresh.getHttpCode())) return Outcome.NEEDS_SIGN_IN;
                return Outcome.RETRY;
            }
            session = refresh.getSession();
            sessions.saveSession(session.getAccessToken(), session.getRefreshToken(),
                    session.getExpiresAtMillis());
        }

        List<SyncOutboxEntry> pending = dao.getPendingSyncEntries(System.currentTimeMillis(), BATCH_SIZE);
        for (SyncOutboxEntry entry : pending) {
            FinanceSyncClient.Result result = syncClient.push(entry, session.getAccessToken());
            if (result.getStatus() == FinanceSyncClient.Status.AUTH_REQUIRED) {
                SupabaseAuthClient.Result refresh = authClient.refresh(session.getRefreshToken());
                if (!refresh.isSuccess()) {
                    dao.markSyncOperationAuthRequired(entry.getId(), safeHttpError(result.getHttpCode()));
                    return Outcome.NEEDS_SIGN_IN;
                }
                session = refresh.getSession();
                sessions.saveSession(session.getAccessToken(), session.getRefreshToken(),
                        session.getExpiresAtMillis());
                result = syncClient.push(entry, session.getAccessToken());
            }
            switch (result.getStatus()) {
                case ACKNOWLEDGED:
                    dao.markSyncOperationAcknowledged(entry.getId());
                    break;
                case CONFLICT:
                    dao.markSyncOperationConflict(entry.getId(), safeHttpError(result.getHttpCode()));
                    return Outcome.CONFLICT;
                case RETRY:
                    long nextAttemptAt = System.currentTimeMillis() + retryDelay(entry.getAttempts() + 1);
                    dao.scheduleSyncOperationRetry(entry.getId(), nextAttemptAt,
                            safeHttpError(result.getHttpCode()));
                    return Outcome.RETRY;
                case AUTH_REQUIRED:
                    dao.markSyncOperationAuthRequired(entry.getId(), safeHttpError(result.getHttpCode()));
                    return Outcome.NEEDS_SIGN_IN;
                case REJECTED:
                    dao.markSyncOperationRejected(entry.getId(), safeHttpError(result.getHttpCode()));
                    return Outcome.REJECTED;
                case NOT_CONFIGURED:
                    return Outcome.NOT_CONFIGURED;
            }
        }
        return Outcome.COMPLETE;
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
