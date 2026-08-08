package com.financeapp.uganda.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.financeapp.uganda.AddSaleActivity;
import com.financeapp.uganda.BusinessActivity;
import com.financeapp.uganda.HouseholdActivity;
import com.financeapp.uganda.InventoryActivity;
import com.financeapp.uganda.R;
import com.financeapp.uganda.ShipmentsActivity;
import com.financeapp.uganda.utils.DashboardMenuHelper;

public class OperationsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_operations, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        DashboardMenuHelper.bind(view, R.id.btnHousehold, R.drawable.ic_home,
                "Household Expenses", "Personal & family budgeting", HouseholdActivity.class);
        DashboardMenuHelper.bind(view, R.id.btnBusiness, R.drawable.ic_store,
                "Business Operations", "Shop rent, staff, marketing", BusinessActivity.class);
        DashboardMenuHelper.bind(view, R.id.btnInventory, R.drawable.ic_inventory,
                "Inventory Control", "Stock, costs & margins", InventoryActivity.class);
        DashboardMenuHelper.bind(view, R.id.btnShipments, R.drawable.ic_ship,
                "Import Shipments", "Turkey & China tracking", ShipmentsActivity.class);
        DashboardMenuHelper.bind(view, R.id.btnAddSale, R.drawable.ic_sales,
                "Record New Sale", "Log sale and update stock", AddSaleActivity.class);
    }
}
