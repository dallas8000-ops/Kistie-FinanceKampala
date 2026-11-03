package com.financeapp.uganda.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.SharedPreferences;
import androidx.test.core.app.ApplicationProvider;
import java.util.Map;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class SupabaseSessionStoreTest {
    private Application application;
    private SecretKey testKey;

    @Before
    public void setUp() throws Exception {
        application = ApplicationProvider.getApplicationContext();
        application.getSharedPreferences("supabase_session", 0).edit().clear().commit();
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(256);
        testKey = keyGenerator.generateKey();
    }

    @Test
    public void sessionIsEncryptedAtRestAndCanBeCleared() {
        SupabaseSessionStore store = new SupabaseSessionStore(application, testKey);
        store.saveSession("access-secret", "refresh-secret", 123456L);

        SharedPreferences preferences = application.getSharedPreferences("supabase_session", 0);
        Map<String, ?> values = preferences.getAll();
        assertNotNull(values.get("session_ciphertext"));
        assertTrue(values.get("session_ciphertext") instanceof String);
        assertTrue(!values.get("session_ciphertext").toString().contains("access-secret"));
        assertTrue(!values.get("session_ciphertext").toString().contains("refresh-secret"));

        SupabaseSessionStore.Session session = store.getSession();
        assertEquals("access-secret", session.getAccessToken());
        assertEquals("refresh-secret", session.getRefreshToken());
        assertEquals(123456L, session.getExpiresAtMillis());

        store.clear();
        assertEquals(null, store.getSession());
    }
}
