package com.financeapp.uganda.receipts;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;
import androidx.core.content.FileProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.financeapp.uganda.models.Sale;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SaleReceiptPdfTest {

    @Test
    public void createsReadablePdfInRestrictedCachePath() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Sale sale = new Sale();
        sale.setId(9876);
        sale.setItemName("PDF smoke test");
        sale.setQuantitySold(1);
        sale.setUnitPrice(1200d);
        sale.setTotalAmount(1200d);
        sale.setAmountPaid(400d);
        sale.setPaymentMethod("Cash");

        File receipt = SaleReceiptGenerator.createPdf(context, sale, null,
                "Kistie Finance", "Kampala, Uganda");
        try {
            assertTrue(receipt.isFile());
            assertTrue(receipt.getParentFile().getCanonicalPath()
                    .endsWith(File.separator + "cache" + File.separator + "receipts"));
            byte[] signature = new byte[4];
            try (FileInputStream input = new FileInputStream(receipt)) {
                assertEquals(4, input.read(signature));
            }
            assertArrayEquals("%PDF".getBytes(StandardCharsets.US_ASCII), signature);

            Uri uri = FileProvider.getUriForFile(context,
                    context.getPackageName() + ".fileprovider", receipt);
            assertEquals("content", uri.getScheme());
            assertEquals(context.getPackageName() + ".fileprovider", uri.getAuthority());
        } finally {
            receipt.delete();
        }
    }
}