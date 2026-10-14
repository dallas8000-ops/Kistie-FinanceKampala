package com.financeapp.uganda.receipts;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.text.TextPaint;
import com.financeapp.uganda.models.ReceivablePayment;
import com.financeapp.uganda.models.Sale;
import com.financeapp.uganda.utils.CurrencyFormatter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class SaleReceiptGenerator {
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final int LINES_PER_PAGE = 36;

    private SaleReceiptGenerator() {}

    public static String buildReceiptText(Sale sale, List<ReceivablePayment> payments,
                                          String businessName, String businessAddress) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        Date saleDate = sale.getSaleDate();
        StringBuilder text = new StringBuilder();
        text.append(businessName).append('\n');
        if (businessAddress != null && !businessAddress.trim().isEmpty()) {
            text.append(businessAddress.trim()).append('\n');
        }
        text.append("RECEIPT #").append(sale.getId()).append('\n');
        text.append("Date: ").append(saleDate == null ? "Date unavailable" : dateFormat.format(saleDate)).append('\n');
        if (sale.getCustomerName() != null && !sale.getCustomerName().trim().isEmpty()) {
            text.append("Customer: ").append(sale.getCustomerName().trim()).append('\n');
        }
        if (sale.getCustomerPhone() != null && !sale.getCustomerPhone().trim().isEmpty()) {
            text.append("Phone: ").append(sale.getCustomerPhone().trim()).append('\n');
        }
        text.append("----------------------------------------\n");
        text.append(sale.getItemName()).append(" x").append(sale.getQuantitySold()).append('\n');
        text.append("Unit price: ").append(CurrencyFormatter.formatUGX(sale.getUnitPrice())).append('\n');
        text.append("Discount: ").append(CurrencyFormatter.formatUGX(sale.getDiscount())).append('\n');
        text.append("Total: ").append(CurrencyFormatter.formatUGX(sale.getTotalAmount())).append('\n');
        List<ReceivablePayment> history = payments != null ? payments : new ArrayList<>();
        double totalPaid = sale.getAmountPaid();
        for (ReceivablePayment payment : history) {
            if (!"REVERSED".equals(payment.getStatus())) totalPaid += payment.getAmount();
        }
        text.append("Paid: ").append(CurrencyFormatter.formatUGX(totalPaid)).append('\n');
        text.append("Balance due: ").append(CurrencyFormatter.formatUGX(
            Math.max(0, sale.getTotalAmount() - totalPaid))).append('\n');
        text.append("Payment method: ").append(sale.getPaymentMethod()).append('\n');
        if (sale.getTransactionId() != null && !sale.getTransactionId().trim().isEmpty()) {
            text.append("Transaction reference: ").append(sale.getTransactionId().trim()).append('\n');
        }
        if (!history.isEmpty()) {
            text.append("Payments received:").append('\n');
            for (ReceivablePayment payment : history) {
                text.append(payment.getPaidAt() == null ? "Date unavailable" : dateFormat.format(payment.getPaidAt()))
                        .append(" · ").append(CurrencyFormatter.formatUGX(payment.getAmount()))
                        .append(" · ").append(payment.getPaymentMethod());
                if (payment.getReference() != null && !payment.getReference().trim().isEmpty()) {
                    text.append(" · ").append(payment.getReference().trim());
                }
                if ("REVERSED".equals(payment.getStatus())) text.append(" · REVERSED");
                text.append('\n');
            }
        }
        return text.toString();
    }

    public static File createPdf(Context context, Sale sale, List<ReceivablePayment> payments,
                                 String businessName, String businessAddress) throws IOException {
        String receipt = buildReceiptText(sale, payments, businessName, businessAddress);
        String[] lines = receipt.split("\n");
        File directory = new File(context.getCacheDir(), "receipts");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Unable to create receipt cache directory.");
        }
        String safeName = "receipt-" + sale.getId() + ".pdf";
        File file = new File(directory, safeName);
        PdfDocument document = new PdfDocument();
        Paint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setTextSize(12f);
        paint.setColor(0xFF17251F);
        int pageCount = Math.max(1, (lines.length + LINES_PER_PAGE - 1) / LINES_PER_PAGE);
        try (FileOutputStream output = new FileOutputStream(file)) {
            for (int pageNumber = 0; pageNumber < pageCount; pageNumber++) {
                PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(
                        PAGE_WIDTH, PAGE_HEIGHT, pageNumber + 1).create();
                PdfDocument.Page page = document.startPage(info);
                Canvas canvas = page.getCanvas();
                int start = pageNumber * LINES_PER_PAGE;
                int end = Math.min(lines.length, start + LINES_PER_PAGE);
                float y = 52;
                for (int index = start; index < end; index++) {
                    if (index == 0) {
                        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                        paint.setTextSize(20f);
                    } else {
                        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                        paint.setTextSize(12f);
                    }
                    canvas.drawText(lines[index], 48, y, paint);
                    y += 20;
                }
                document.finishPage(page);
            }
            document.writeTo(output);
        } finally {
            document.close();
        }
        return file;
    }
}