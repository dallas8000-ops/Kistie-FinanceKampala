package com.financeapp.uganda.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class SupabaseAuthClientTest {
    private MockWebServer server;
    private SupabaseAuthClient authClient;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        authClient = new SupabaseAuthClient(
            new SupabaseConfig("https://owner-project.supabase.co", "public-anon-key"),
            new OkHttpClient(), server.url("/").toString());
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    public void signInParsesTokensAndUsesPasswordGrantEndpoint() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(
                "{\"access_token\":\"access-1\",\"refresh_token\":\"refresh-1\","
                        + "\"expires_in\":3600}"));

        SupabaseAuthClient.Result result = authClient.signIn("owner@example.com", "password");
        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);

        assertTrue(result.isSuccess());
        assertNotNull(result.getSession());
        assertEquals("access-1", result.getSession().getAccessToken());
        assertEquals("refresh-1", result.getSession().getRefreshToken());
        assertTrue(result.getSession().getExpiresAtMillis() > System.currentTimeMillis());
        assertEquals("/auth/v1/token?grant_type=password", request.getPath());
        assertEquals("public-anon-key", request.getHeader("apikey"));
    }

    @Test
    public void refreshRotatesRefreshTokenAndAuthErrorsDoNotExposeBody() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(
                "{\"access_token\":\"access-2\",\"refresh_token\":\"refresh-2\","
                        + "\"expires_in\":1800}"));
        server.enqueue(new MockResponse().setResponseCode(400)
                .setBody("{\"error_description\":\"private detail\"}"));

        SupabaseAuthClient.Result refreshed = authClient.refresh("refresh-1");
        SupabaseAuthClient.Result rejected = authClient.signIn("owner@example.com", "bad-password");

        assertEquals("refresh-2", refreshed.getSession().getRefreshToken());
        assertFalseResultHasNoSession(rejected);
        assertEquals(400, rejected.getHttpCode());
        assertNotNull(rejected.getErrorMessage());
        assertTrue(!rejected.getErrorMessage().contains("private detail"));
    }

    private void assertFalseResultHasNoSession(SupabaseAuthClient.Result result) {
        assertTrue(!result.isSuccess());
        assertNull(result.getSession());
    }
}
