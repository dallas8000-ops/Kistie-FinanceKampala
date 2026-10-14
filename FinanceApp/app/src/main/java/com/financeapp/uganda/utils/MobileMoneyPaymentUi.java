package com.financeapp.uganda.utils;

import android.content.Context;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

public final class MobileMoneyPaymentUi {
    private MobileMoneyPaymentUi() {}

    public static Runnable bind(Context context, Spinner spinner, EditText reference,
                                View referenceContainer, TextView receivingNumber, boolean incomingPayment) {
        Runnable refresh = () -> {
            Object selected = spinner.getSelectedItem();
            String method = selected == null ? "" : selected.toString();
            if (!PaymentMethods.isMobileMoney(method)) {
                reference.setVisibility(View.GONE);
                referenceContainer.setVisibility(View.GONE);
                receivingNumber.setVisibility(View.GONE);
                return;
            }

            reference.setVisibility(View.VISIBLE);
            referenceContainer.setVisibility(View.VISIBLE);
            reference.setHint(method + " transaction reference");
            if (!incomingPayment) {
                receivingNumber.setVisibility(View.GONE);
                return;
            }

            String number = PaymentMethods.MTN_MOMO.equals(method)
                    ? PrefsHelper.getMtnMobileMoneyNumber(context)
                    : PrefsHelper.getAirtelMoneyNumber(context);
            receivingNumber.setVisibility(View.VISIBLE);
            receivingNumber.setText(number.isEmpty()
                    ? "Set your " + method + " receiving number in Settings."
                    : "Customer sends to " + method + ": " + number);
        };
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                refresh.run();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {
                refresh.run();
            }
        });
        refresh.run();
        return refresh;
    }
}