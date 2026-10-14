package com.financeapp.uganda.utils;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.financeapp.uganda.BuildConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class PrefsHelperTest {
    private static final String PREFS = "kistie_finance_prefs";
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit();
    }

    @After
    public void tearDown() {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test
    public void localBuildKeyIsUsedWhenNoDeviceKeyHasBeenSaved() {
        assertEquals(BuildConfig.OPENAI_API_KEY, PrefsHelper.getApiKey(context));
    }

    @Test
    public void savedDeviceKeyTakesPrecedenceOverLocalBuildKey() {
        PrefsHelper.saveApiKey(context, "saved-device-key");

        assertEquals("saved-device-key", PrefsHelper.getApiKey(context));
    }

    @Test
    public void mtnAndAirtelReceivingNumbersAreStoredSeparately() {
        PrefsHelper.saveMtnMobileMoneyNumber(context, "+256770111222");
        PrefsHelper.saveAirtelMoneyNumber(context, "+256750333444");

        assertEquals("+256770111222", PrefsHelper.getMtnMobileMoneyNumber(context));
        assertEquals("+256750333444", PrefsHelper.getAirtelMoneyNumber(context));
    }
}
