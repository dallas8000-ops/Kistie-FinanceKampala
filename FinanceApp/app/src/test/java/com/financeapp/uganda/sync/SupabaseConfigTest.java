package com.financeapp.uganda.sync;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SupabaseConfigTest {

    @Test
    public void syncRemainsDisabledWithoutProjectUrlAndPublicKey() {
        assertFalse(new SupabaseConfig("", "").isConfigured());
        assertFalse(new SupabaseConfig("https://sample.supabase.co", "").isConfigured());
        assertFalse(new SupabaseConfig("", "public-anon-key").isConfigured());
    }

    @Test
    public void onlyHttpsProjectAndPublicClientKeysAreAccepted() {
        assertTrue(new SupabaseConfig("https://sample.supabase.co", "public-anon-key").isConfigured());
        assertFalse(new SupabaseConfig("http://sample.supabase.co", "public-anon-key").isConfigured());
        assertFalse(new SupabaseConfig("https://sample.supabase.co", "service_role-secret").isConfigured());
    }
}
