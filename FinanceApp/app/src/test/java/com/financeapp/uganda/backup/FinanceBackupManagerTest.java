package com.financeapp.uganda.backup;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class FinanceBackupManagerTest {

    @Test
    public void encryptedArchiveRoundTripsWithoutExposingPlaintext() throws Exception {
        byte[] payload = "customer=Test Customer;balance=123456".getBytes(StandardCharsets.UTF_8);
        char[] passphrase = "local-test-passphrase".toCharArray();

        byte[] encrypted = FinanceBackupManager.encryptArchivePayload(payload, passphrase);

        assertFalse(new String(encrypted, StandardCharsets.UTF_8).contains("Test Customer"));
        assertArrayEquals(payload,
                FinanceBackupManager.decryptArchivePayload(encrypted, passphrase));
    }

    @Test
    public void wrongPassphraseIsRejected() throws Exception {
        byte[] encrypted = FinanceBackupManager.encryptArchivePayload(
                "private backup".getBytes(StandardCharsets.UTF_8), "correct horse battery".toCharArray());

        assertThrows(java.io.IOException.class,
                () -> FinanceBackupManager.decryptArchivePayload(encrypted, "wrong passphrase".toCharArray()));
    }

    @Test
    public void modifiedCiphertextIsRejected() throws Exception {
        byte[] encrypted = FinanceBackupManager.encryptArchivePayload(
                "private backup".getBytes(StandardCharsets.UTF_8), "passphrase".toCharArray());
        encrypted[encrypted.length - 1] ^= 1;

        assertThrows(java.io.IOException.class,
                () -> FinanceBackupManager.decryptArchivePayload(encrypted, "passphrase".toCharArray()));
    }
}
