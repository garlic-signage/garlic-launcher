package com.sagiadinos.garlic.launcher.helper;

import android.util.DisplayMetrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * The admin UI opens after 10 clicks in the right or bottom 10% of the screen.
 */
class ScreenTest
{
    Screen MyTestClass;

    @BeforeEach
    void setUp()
    {
        DisplayMetrics MetricsMocked = mock(DisplayMetrics.class);
        MetricsMocked.widthPixels  = 1000;
        MetricsMocked.heightPixels = 500;
        MyTestClass = new Screen(MetricsMocked);
    }

    @Test
    void clicksOutsideAreaNeverPermit()
    {
        for (int i = 0; i < 20; i++)
        {
            assertFalse(MyTestClass.isEventInPermitUIArea(899, 449));
        }
    }

    @Test
    void tenthClickInRightAreaPermits()
    {
        assertTenthClickPermits(900, 0);
    }

    @Test
    void tenthClickInBottomAreaPermits()
    {
        assertTenthClickPermits(0, 450);
    }

    @Test
    void counterResetsAfterPermit()
    {
        assertTenthClickPermits(950, 480);
        assertTenthClickPermits(950, 480);
    }

    @Test
    void clicksOutsideDoNotResetCounter()
    {
        for (int i = 0; i < 5; i++)
        {
            assertFalse(MyTestClass.isEventInPermitUIArea(950, 480));
        }
        assertFalse(MyTestClass.isEventInPermitUIArea(10, 10));
        for (int i = 0; i < 4; i++)
        {
            assertFalse(MyTestClass.isEventInPermitUIArea(950, 480));
        }
        assertTrue(MyTestClass.isEventInPermitUIArea(950, 480));
    }

    private void assertTenthClickPermits(int x, int y)
    {
        for (int i = 0; i < 9; i++)
        {
            assertFalse(MyTestClass.isEventInPermitUIArea(x, y));
        }
        assertTrue(MyTestClass.isEventInPermitUIArea(x, y));
    }
}
