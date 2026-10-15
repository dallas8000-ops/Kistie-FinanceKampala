package com.financeapp.uganda.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.financeapp.uganda.models.SyncOutboxEntry;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class FinanceSyncClientTest {
    @Test
    public void postsOwnerAuthenticatedIdempotentOperationToRpc() throws Exception {
        AtomicReference<Request> captured = new AtomicReference<>();
        OkHttpClient fakeClient = fakeClient(200, captured,
            "{\"accepted\":true,\"duplicate\":false}");
        FinanceSyncClient client = new FinanceSyncClient(
                new SupabaseConfig("https://owner-project.supabase.co", "public-anon-key"), fakeClient);
        SyncOutboxEntry entry = new SyncOutboxEntry();
        entry.setOperationId("550e8400-e29b-41d4-a716-446655440000");
        entry.setOperationType("SALE_POSTED");
        entry.setAggregateId("sale-42");
        entry.setPayloadVersion(1);
        entry.setPayloadJson("{\"saleId\":42,\"amount\":1200}");

        FinanceSyncClient.Result result = client.push(entry, "user-access-token");

        assertEquals(FinanceSyncClient.Status.ACKNOWLEDGED, result.getStatus());
        Request request = captured.get();
        assertEquals("/rest/v1/rpc/apply_finance_operation", request.url().encodedPath());
        assertEquals("public-anon-key", request.header("apikey"));
        assertEquals("Bearer user-access-token", request.header("Authorization"));
        Buffer body = new Buffer();
        request.body().writeTo(body);
        String payload = body.readUtf8();
        assertTrue(payload.contains("550e8400-e29b-41d4-a716-446655440000"));
        assertTrue(payload.contains("SALE_POSTED"));
        assertTrue(payload.contains("saleId"));
    }

    @Test
    public void missingConfigurationStopsBeforeNetwork() {
        FinanceSyncClient client = new FinanceSyncClient(new SupabaseConfig("", ""), fakeClient(200, new AtomicReference<>()));
        assertEquals(FinanceSyncClient.Status.NOT_CONFIGURED,
                client.push(new SyncOutboxEntry(), "token").getStatus());
    }

    @Test
    public void unauthorizedResponseRequiresSignIn() {
        FinanceSyncClient client = new FinanceSyncClient(
                new SupabaseConfig("https://owner-project.supabase.co", "public-anon-key"),
                fakeClient(401, new AtomicReference<>()));
        SyncOutboxEntry entry = new SyncOutboxEntry();
        entry.setOperationId("operation-unauthorized");
        entry.setOperationType("SALE_POSTED");
        entry.setAggregateId("sale-unauthorized");
        entry.setPayloadJson("{}");
        assertEquals(FinanceSyncClient.Status.AUTH_REQUIRED,
            client.push(entry, "expired-token").getStatus());
    }

    @Test
    public void conflictAndTransientServerErrorsAreNotAcknowledged() {
        assertEquals(FinanceSyncClient.Status.CONFLICT, pushWithStatus(409));
        assertEquals(FinanceSyncClient.Status.RETRY, pushWithStatus(503));
        assertEquals(FinanceSyncClient.Status.REJECTED, pushWithStatus(422));
    }

        @Test
        public void successfulHttpResponseMustContainAcceptedRpcResult() {
        FinanceSyncClient client = new FinanceSyncClient(
            new SupabaseConfig("https://owner-project.supabase.co", "public-anon-key"),
            fakeClient(200, new AtomicReference<>(), "{}"));
        SyncOutboxEntry entry = new SyncOutboxEntry();
        entry.setOperationId("550e8400-e29b-41d4-a716-446655440001");
        entry.setOperationType("SALE_POSTED");
        entry.setAggregateId("sale-status-test");
        entry.setPayloadJson("{}");

        assertEquals(FinanceSyncClient.Status.RETRY,
            client.push(entry, "owner-access-token").getStatus());
        }

    private FinanceSyncClient.Status pushWithStatus(int responseCode) {
        FinanceSyncClient client = new FinanceSyncClient(
                new SupabaseConfig("https://owner-project.supabase.co", "public-anon-key"),
                fakeClient(responseCode, new AtomicReference<>()));
        SyncOutboxEntry entry = new SyncOutboxEntry();
        entry.setOperationId("550e8400-e29b-41d4-a716-44665544000" + responseCode);
        entry.setOperationType("SALE_POSTED");
        entry.setAggregateId("sale-status-test");
        entry.setPayloadJson("{}");
        return client.push(entry, "owner-access-token").getStatus();
    }

    private OkHttpClient fakeClient(int responseCode, AtomicReference<Request> captured) {
        return fakeClient(responseCode, captured, "{}");
        }

        private OkHttpClient fakeClient(int responseCode, AtomicReference<Request> captured,
            String responseBody) {
        return new OkHttpClient.Builder().addInterceptor((Interceptor.Chain chain) -> {
            Request request = chain.request();
            captured.set(request);
            return new Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(responseCode)
                    .message("test response")
                    .body(ResponseBody.create(responseBody, MediaType.get("application/json")))
                    .build();
        }).build();
    }
}
