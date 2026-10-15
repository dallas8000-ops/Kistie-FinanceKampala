package com.financeapp.uganda;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ManifestSecurityTest {
    @Test
    public void disablesPlatformBackupAndCleartextTraffic() throws Exception {
        Application application = ApplicationProvider.getApplicationContext();
        ApplicationInfo applicationInfo = application.getPackageManager()
                .getApplicationInfo(application.getPackageName(), 0);

        assertEquals(0, applicationInfo.flags & ApplicationInfo.FLAG_ALLOW_BACKUP);
        assertFalse((applicationInfo.flags & ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC) != 0);
    }
}
