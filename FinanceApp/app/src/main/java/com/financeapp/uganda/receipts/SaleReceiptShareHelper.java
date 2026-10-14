package com.financeapp.uganda.receipts;

import android.content.Context;
import android.content.Intent;
import androidx.core.content.FileProvider;
import com.financeapp.uganda.database.AppDatabase;
import com.financeapp.uganda.models.ReceivablePayment;
import com.financeapp.uganda.models.Sale;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public final class SaleReceiptShareHelper {
    private SaleReceiptShareHelper() {}

    public static File createAndShare(Context context, Sale sale) throws IOException {
        AppDatabase database = AppDatabase.getInstance(context);
        List<ReceivablePayment> payments = sale.getReceivableId() > 0
                ? database.financeDao().getReceivablePayments(sale.getReceivableId())
                : Collections.emptyList();
        File receipt = SaleReceiptGenerator.createPdf(context, sale, payments,
                "Kistie Finance", "Kampala, Uganda");
        android.net.Uri uri = FileProvider.getUriForFile(context,
                context.getPackageName() + ".fileprovider", receipt);
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("application/pdf");
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(Intent.createChooser(shareIntent, "Share sale receipt"));
        return receipt;
    }
}