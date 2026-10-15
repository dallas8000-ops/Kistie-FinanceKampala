package com.financeapp.uganda.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import com.financeapp.uganda.database.AppDatabase;
import com.financeapp.uganda.database.FinanceDao;
import com.financeapp.uganda.models.SyncOutboxEntry;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class SyncOutboxDrainerTest {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private AppDatabase database;
    private FinanceDao dao;
    private SupabaseSessionStore sessions;
    private SecretKey testKey;
    private SupabaseConfig config;

    @Before
    public void setUp() throws Exception {
        Application application = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(application, AppDatabase.class)
                .allowMainThreadQueries().build();
        dao = database.financeDao();
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(256);
        testKey = keyGenerator.generateKey();
        sessions = new SupabaseSessionStore(application, testKey);
        sessions.clear();
        config = new SupabaseConfig("https://owner-project.supabase.co", "public-anon-key");
    }

    @After
    public void tearDown() {
        sessions.clear();
        database.close();
    }

    @Test
    public void successfulPushAcknowledgesOperation() {
        long id = dao.insertSyncOutboxEntry(entry("ack-operation"));
        sessions.saveSession("access", "refresh", System.currentTimeMillis() + 3600000L);

        SyncOutboxDrainer.Outcome outcome = drainer(200).drain();

        assertEquals(SyncOutboxDrainer.Outcome.COMPLETE, outcome);
        assertEquals("ACKNOWLEDGED", dao.getSyncOutboxEntryById((int) id).getStatus());
        assertEquals(0, dao.getPendingSyncCount());
    }

    @Test
    public void conflictIsPersistedAndBlocksLaterOperations() {
        long conflictId = dao.insertSyncOutboxEntry(entry("conflict-operation"));
        long laterId = dao.insertSyncOutboxEntry(entry("later-operation"));
        sessions.saveSession("access", "refresh", System.currentTimeMillis() + 3600000L);

        assertEquals(SyncOutboxDrainer.Outcome.CONFLICT, drainer(409).drain());

        assertEquals("CONFLICT", dao.getSyncOutboxEntryById((int) conflictId).getStatus());
        SyncOutboxEntry retry = dao.getSyncOutboxEntryById((int) laterId);
        assertEquals("PENDING", retry.getStatus());
        assertEquals(0, retry.getAttempts());
    }

    @Test
    public void transientFailureSchedulesExponentialRetry() {
        long retryId = dao.insertSyncOutboxEntry(entry("retry-operation"));
        sessions.saveSession("access", "refresh", System.currentTimeMillis() + 3600000L);

        assertEquals(SyncOutboxDrainer.Outcome.RETRY, drainer(503).drain());

        SyncOutboxEntry retry = dao.getSyncOutboxEntryById((int) retryId);
        assertEquals("PENDING", retry.getStatus());
        assertEquals(1, retry.getAttempts());
        assertTrue(retry.getNextAttemptAt() > System.currentTimeMillis());
    }

    @Test
    public void missingSessionLeavesLocalOperationsPending() {
        dao.insertSyncOutboxEntry(entry("offline-operation"));

        assertEquals(SyncOutboxDrainer.Outcome.NEEDS_SIGN_IN, drainer(200).drain());
        assertEquals(1, dao.getPendingSyncCount());
    }

    private SyncOutboxDrainer drainer(int statusCode) {
        return drainer(() -> statusCode);
    }

    private SyncOutboxDrainer drainer(StatusSupplier statusSupplier) {
        OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain -> respond(chain,
                statusSupplier.get())).build();
        return new SyncOutboxDrainer(dao, sessions, config,
                new SupabaseAuthClient(config, http, "https://auth.test"),
                new FinanceSyncClient(config, http));
    }

    private Response respond(Interceptor.Chain chain, int statusCode) throws IOException {
        String body = statusCode == 200 ? "{\"accepted\":true}" : "{}";
        return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(statusCode).message("test")
            .body(ResponseBody.create(body, JSON)).build();
    }

    private SyncOutboxEntry entry(String operationId) {
        SyncOutboxEntry entry = new SyncOutboxEntry();
        entry.setOperationId(operationId);
        entry.setOperationType("SALE_POSTED");
        entry.setAggregateId("sale-1");
        entry.setPayloadJson("{}");
        entry.setCreatedAt(System.currentTimeMillis());
        return entry;
    }

    private interface StatusSupplier {
        int get();
    }
}
