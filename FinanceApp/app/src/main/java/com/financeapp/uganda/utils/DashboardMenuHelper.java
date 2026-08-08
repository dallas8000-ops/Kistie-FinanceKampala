package com.financeapp.uganda.utils;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import com.financeapp.uganda.R;

public final class DashboardMenuHelper {

    private DashboardMenuHelper() {}

    public static void bind(View root, int includeId, int iconRes, String title, String subtitle, Class<?> activity) {
        View item = root.findViewById(includeId);
        if (item == null) return;
        ImageView icon = item.findViewById(R.id.menuIcon);
        TextView titleView = item.findViewById(R.id.menuTitle);
        TextView subtitleView = item.findViewById(R.id.menuSubtitle);
        if (icon != null) icon.setImageResource(iconRes);
        if (titleView != null) titleView.setText(title);
        if (subtitleView != null) subtitleView.setText(subtitle);
        Context ctx = root.getContext();
        item.setOnClickListener(v -> ctx.startActivity(new Intent(ctx, activity)));
    }
}
