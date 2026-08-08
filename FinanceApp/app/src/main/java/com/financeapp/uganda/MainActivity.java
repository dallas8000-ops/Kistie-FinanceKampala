package com.financeapp.uganda;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.financeapp.uganda.accounting.AccountingService;
import com.financeapp.uganda.database.AppDatabase;
import com.financeapp.uganda.fragments.AccountingFragment;
import com.financeapp.uganda.fragments.HomeFragment;
import com.financeapp.uganda.fragments.MoreFragment;
import com.financeapp.uganda.fragments.OperationsFragment;
import com.financeapp.uganda.fragments.ReportsFragment;
import com.financeapp.uganda.utils.PrefsHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_nav);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment fragment;
            String title;
            int id = item.getItemId();
            if (id == R.id.nav_accounting) {
                fragment = new AccountingFragment();
                title = "Accounting";
            } else if (id == R.id.nav_operations) {
                fragment = new OperationsFragment();
                title = "Business";
            } else if (id == R.id.nav_reports) {
                fragment = new ReportsFragment();
                title = "Reports";
            } else if (id == R.id.nav_more) {
                fragment = new MoreFragment();
                title = "More";
            } else {
                fragment = new HomeFragment();
                title = "Kistie Finance";
            }
            loadFragment(fragment, title);
            return true;
        });

        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.nav_home);
            loadFragment(new HomeFragment(), "Kistie Finance");
            promptCapitalSetupIfNeeded();
        }
    }

    private void loadFragment(Fragment fragment, String title) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
        }
    }

    private void promptCapitalSetupIfNeeded() {
        AccountingService accounting = new AccountingService(AppDatabase.getInstance(this).financeDao());
        if (!accounting.hasInitialCapital() && !PrefsHelper.isCapitalConfigured(this)) {
            startActivity(new Intent(this, CapitalSetupActivity.class));
        }
    }
}
