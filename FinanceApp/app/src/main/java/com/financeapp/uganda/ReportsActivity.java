package com.financeapp.uganda;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.financeapp.uganda.database.AppDatabase;
import com.financeapp.uganda.utils.CurrencyFormatter;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.utils.ColorTemplate;
import java.util.ArrayList;
import java.util.List;

public class ReportsActivity extends BaseToolbarActivity {

    private AppDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);
        setupToolbar("Reports & Analytics");

        db = AppDatabase.getInstance(this);
        loadReports();
    }

    private void loadReports() {
        double totalSales = db.financeDao().getTotalSales();
        double totalProfit = db.financeDao().getTotalProfit();
        double household = db.financeDao().getTotalByCategory("household");
        double businessOp = db.financeDao().getTotalByCategory("business_operational");
        double businessRent = db.financeDao().getTotalByCategory("business_rent");
        double businessStaff = db.financeDao().getTotalByCategory("business_staff");
        double businessMarketing = db.financeDao().getTotalByCategory("business_marketing");
        double totalExpenses = household + businessOp + businessRent + businessStaff + businessMarketing;
        double inventoryValue = db.financeDao().getTotalInventoryValue();
        int stockCount = db.financeDao().getTotalStockCount();
        int lowStock = db.financeDao().getLowStockItems().size();

        ((TextView) findViewById(R.id.tvReportSales)).setText(CurrencyFormatter.formatUGX(totalSales));
        ((TextView) findViewById(R.id.tvReportProfit)).setText(CurrencyFormatter.formatUGX(totalProfit));
        ((TextView) findViewById(R.id.tvReportExpenses)).setText(CurrencyFormatter.formatUGX(totalExpenses));
        ((TextView) findViewById(R.id.tvReportInventory)).setText(CurrencyFormatter.formatUGX(inventoryValue));
        ((TextView) findViewById(R.id.tvReportStock)).setText(stockCount + " items");
        ((TextView) findViewById(R.id.tvReportLowStock)).setText(lowStock + " items");

        setupPieChart(household, businessOp, businessRent, businessStaff, businessMarketing);
        setupBarChart(totalSales, totalProfit, totalExpenses, inventoryValue);
    }

    private void setupPieChart(double household, double businessOp, double businessRent, double businessStaff, double businessMarketing) {
        PieChart pieChart = findViewById(R.id.pieChart);
        List<PieEntry> entries = new ArrayList<>();
        if (household > 0) entries.add(new PieEntry((float) household, "Household"));
        if (businessOp > 0) entries.add(new PieEntry((float) businessOp, "Operations"));
        if (businessRent > 0) entries.add(new PieEntry((float) businessRent, "Rent/Utils"));
        if (businessStaff > 0) entries.add(new PieEntry((float) businessStaff, "Staff"));
        if (businessMarketing > 0) entries.add(new PieEntry((float) businessMarketing, "Marketing"));
        if (entries.isEmpty()) entries.add(new PieEntry(1f, "No data"));

        PieDataSet dataSet = new PieDataSet(entries, "Expense Breakdown");
        dataSet.setColors(ColorTemplate.MATERIAL_COLORS);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(12f);

        PieData data = new PieData(dataSet);
        pieChart.setData(data);
        Description desc = new Description();
        desc.setText("");
        pieChart.setDescription(desc);
        pieChart.invalidate();
    }

    private void setupBarChart(double sales, double profit, double expenses, double inventory) {
        BarChart barChart = findViewById(R.id.barChart);
        List<BarEntry> entries = new ArrayList<>();
        entries.add(new BarEntry(0, (float) sales));
        entries.add(new BarEntry(1, (float) profit));
        entries.add(new BarEntry(2, (float) expenses));
        entries.add(new BarEntry(3, (float) inventory));

        BarDataSet dataSet = new BarDataSet(entries, "Financial Overview (UGX)");
        dataSet.setColors(ColorTemplate.COLORFUL_COLORS);
        dataSet.setValueTextSize(10f);

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.6f);
        barChart.setData(data);
        Description desc = new Description();
        desc.setText("");
        barChart.setDescription(desc);
        barChart.getXAxis().setGranularity(1f);
        barChart.invalidate();
    }
}
