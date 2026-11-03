package com.financeapp.uganda.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import com.financeapp.uganda.database.AppDatabase;
import com.financeapp.uganda.database.FinanceDao;
import com.financeapp.uganda.models.SyncOutboxEntry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class FinanceSyncOutboxTest {
    private AppDatabase database;
    private FinanceDao dao;

    @Before
    public void setUp() {
        Application application = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(application, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = database.financeDao();
    }

    @After
    public void tearDown() {
        database.close();
    }

    @Test
    public void retryBackoffAndTerminalTransitionsControlEligibility() {
        SyncOutboxEntry retryEntry = entry("retry-operation");
        long retryId = dao.insertSyncOutboxEntry(retryEntry);

        assertEquals(1, dao.scheduleSyncOperationRetry((int) retryId, 5000L, "503"));
        assertEquals(1, dao.getPendingSyncCount());
        assertTrue(dao.getPendingSyncEntries(4999L, 10).isEmpty());
        assertEquals(1, dao.getPendingSyncEntries(5000L, 10).size());
        assertEquals(1, dao.getSyncOutboxEntryById((int) retryId).getAttempts());

        assertEquals(1, dao.markSyncOperationAcknowledged((int) retryId));
        assertEquals(0, dao.getPendingSyncCount());
        assertTrue(dao.getPendingSyncEntries(Long.MAX_VALUE, 10).isEmpty());
        assertEquals(0, dao.scheduleSyncOperationRetry((int) retryId, 9000L, "late retry"));
        assertEquals("ACKNOWLEDGED", dao.getSyncOutboxEntryById((int) retryId).getStatus());
    }

    @Test
    public void authRequiredOperationCanBeRequeuedAfterSignIn() {
        long operationId = dao.insertSyncOutboxEntry(entry("auth-operation"));

        assertEquals(1, dao.markSyncOperationAuthRequired((int) operationId, "401"));
        assertEquals(0, dao.getPendingSyncCount());
        assertEquals(1, dao.resetAuthRequiredSyncOperations());
        assertEquals(1, dao.getPendingSyncCount());
        assertEquals(1, dao.getPendingSyncEntries(Long.MAX_VALUE, 10).size());
    }

    @Test
    public void conflictsAndRejectedOperationsRemainVisibleButAreNotRetried() {
        long conflictId = dao.insertSyncOutboxEntry(entry("conflict-operation"));
        long rejectedId = dao.insertSyncOutboxEntry(entry("rejected-operation"));

        assertEquals(1, dao.markSyncOperationConflict((int) conflictId, "stock changed"));
        assertEquals(1, dao.markSyncOperationRejected((int) rejectedId, "invalid payload"));
        assertEquals(0, dao.getPendingSyncCount());
        assertTrue(dao.getPendingSyncEntries(Long.MAX_VALUE, 10).isEmpty());
        assertEquals("CONFLICT", dao.getSyncOutboxEntryById((int) conflictId).getStatus());
        assertEquals("REJECTED", dao.getSyncOutboxEntryById((int) rejectedId).getStatus());
        assertEquals("stock changed", dao.getSyncOutboxEntryById((int) conflictId).getLastError());
        assertEquals(1, dao.getSyncCountByStatus("CONFLICT"));
        assertEquals(1, dao.getSyncCountByStatus("REJECTED"));
    }

    private SyncOutboxEntry entry(String operationId) {
        SyncOutboxEntry entry = new SyncOutboxEntry();
        entry.setOperationId(operationId);
        entry.setOperationType("SALE_POSTED");
        entry.setAggregateId("sale-1");
        entry.setPayloadJson("{}");
        entry.setCreatedAt(1L);
        return entry;
    }
}
