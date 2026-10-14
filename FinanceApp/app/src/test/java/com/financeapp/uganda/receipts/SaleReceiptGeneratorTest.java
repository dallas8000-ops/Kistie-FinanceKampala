package com.financeapp.uganda.receipts;

import static org.junit.Assert.assertTrue;

import com.financeapp.uganda.models.ReceivablePayment;
import com.financeapp.uganda.models.Sale;
import java.util.Arrays;
import java.util.Date;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class SaleReceiptGeneratorTest {

    @Test
    public void receiptIncludesSaleDepositBalanceAndLaterPayment() {
        Sale sale = new Sale();
        sale.setId(42);
        sale.setItemName("Cotton dress");
        sale.setQuantitySold(2);
        sale.setUnitPrice(50000d);
        sale.setDiscount(5000d);
        sale.setTotalAmount(95000d);
        sale.setAmountPaid(30000d);
        sale.setTransactionId("MTN-REF-42");
        sale.setSaleDate(new Date(0));
        sale.setPaymentMethod("Cash");
        sale.setCustomerName("Amina Namusoke");
        sale.setCustomerPhone("0770000000");

        ReceivablePayment payment = new ReceivablePayment();
        payment.setAmount(10000d);
        payment.setPaymentMethod("Mobile Money");
        payment.setPaidAt(new Date(86400000));
        payment.setReference("MM-123");

        String receipt = SaleReceiptGenerator.buildReceiptText(sale,
                Arrays.asList(payment), "Kistie Finance", "Kampala, Uganda");

        assertTrue(receipt.contains("RECEIPT #42"));
        assertTrue(receipt.contains("Cotton dress x2"));
        assertTrue(receipt.contains("Total: UGX 95,000"));
        assertTrue(receipt.contains("Paid: UGX 40,000"));
        assertTrue(receipt.contains("Balance due: UGX 55,000"));
        assertTrue(receipt.contains("Mobile Money"));
        assertTrue(receipt.contains("Transaction reference: MTN-REF-42"));
        assertTrue(receipt.contains("MM-123"));
        assertTrue(receipt.contains("Amina Namusoke"));
    }

}