package com.financeapp.uganda;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ShipmentsActivityTest {
    @Test
    public void previewParserHandlesPartialDecimalInput() {
        assertEquals(0d, ShipmentsActivity.parsePreviewDouble("."), 0.001d);
        assertEquals(0d, ShipmentsActivity.parsePreviewDouble(""), 0.001d);
        assertEquals(12.5d, ShipmentsActivity.parsePreviewDouble("12.5"), 0.001d);
    }
}
