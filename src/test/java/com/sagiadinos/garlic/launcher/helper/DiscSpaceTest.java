package com.sagiadinos.garlic.launcher.helper;

import android.os.StatFs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiscSpaceTest
{
    @Test
    void freePercentIsDeterminedOnConstruction()
    {
        StatFs StatFsMocked = mock(StatFs.class);
        when(StatFsMocked.getTotalBytes()).thenReturn(1000L);
        when(StatFsMocked.getFreeBytes()).thenReturn(250L);

        assertEquals(25, new DiscSpace(StatFsMocked, "/cache").getFreePercent());
    }

    @Test
    void freePercentIsRoundedDown()
    {
        StatFs StatFsMocked = mock(StatFs.class);
        when(StatFsMocked.getTotalBytes()).thenReturn(3L);
        when(StatFsMocked.getFreeBytes()).thenReturn(2L);

        assertEquals(66, new DiscSpace(StatFsMocked, "/cache").getFreePercent());
    }

    @Test
    void emptyDiscReportsZeroPercent()
    {
        StatFs StatFsMocked = mock(StatFs.class);
        when(StatFsMocked.getTotalBytes()).thenReturn(0L);
        when(StatFsMocked.getFreeBytes()).thenReturn(0L);

        assertEquals(0, new DiscSpace(StatFsMocked, "/cache").getFreePercent());
    }

    @Test
    void refreshRestatsPathAndRecalculates()
    {
        StatFs StatFsMocked = mock(StatFs.class);
        when(StatFsMocked.getTotalBytes()).thenReturn(1000L);
        when(StatFsMocked.getFreeBytes()).thenReturn(500L, 100L);

        DiscSpace MyTestClass = new DiscSpace(StatFsMocked, "/cache");
        assertEquals(50, MyTestClass.getFreePercent());

        MyTestClass.refresh();

        verify(StatFsMocked, times(1)).restat("/cache");
        assertEquals(10, MyTestClass.getFreePercent());
    }
}
