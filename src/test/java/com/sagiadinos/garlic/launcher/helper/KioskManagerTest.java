package com.sagiadinos.garlic.launcher.helper;

import com.sagiadinos.garlic.launcher.BuildConfig;
import com.sagiadinos.garlic.launcher.MainActivity;
import com.sagiadinos.garlic.launcher.configuration.MainConfiguration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KioskManagerTest
{
    DeviceOwner          DeviceOwnerMocked;
    HomeLauncherManager  HomeLauncherManagerMocked;
    MainActivity         MainActivityMocked;
    MainConfiguration    MainConfigurationMocked;

    @AfterEach
    void tearDown()
    {
        DeviceOwnerMocked         = null;
        HomeLauncherManagerMocked = null;
        MainActivityMocked        = null;
        MainConfigurationMocked   = null;
    }

    @Test
    void pinLockTaskPermitted()
    {
        KioskManager MyTestClass = createClass();
        when(DeviceOwnerMocked.isLockTaskPermitted()).thenReturn(true);

        MyTestClass.pin();

        // pin() is deactivated in debug builds
        verify(MainActivityMocked, times(BuildConfig.DEBUG ? 0 : 1)).startLockTask();
    }

    @Test
    void pinLockTaskNotPermitted()
    {
        KioskManager MyTestClass = createClass();
        when(DeviceOwnerMocked.isLockTaskPermitted()).thenReturn(false);

        MyTestClass.pin();
        verify(MainActivityMocked, never()).startLockTask();
    }

    @Test
    void unpin()
    {
        KioskManager MyTestClass = createClass();

        MyTestClass.unpin();
        verify(MainActivityMocked, times(1)).stopLockTask();
    }

    @Test
    void toggleServiceModeWithTrue()
    {
        KioskManager MyTestClass = createClass();

        MyTestClass.toggleServiceMode(true);
        verify(MainConfigurationMocked, times(1)).setStrictKioskMode(false);
    }

    @Test
    void toggleServiceModeWithFalse()
    {
        KioskManager MyTestClass = createClass();

        MyTestClass.toggleServiceMode(false);
        verify(MainConfigurationMocked, times(1)).setStrictKioskMode(true);
    }

    @Test
    void isStrictKioskModeActive()
    {
        KioskManager MyTestClass = createClass();
        when(MainConfigurationMocked.isStrictKioskModeActive()).thenReturn(true);

        assertTrue(MyTestClass.isStrictKioskModeActive());
        verify(MainConfigurationMocked, times(1)).isStrictKioskModeActive();
    }

    @Test
    void isHomeActivityTrue()
    {
        KioskManager MyTestClass = createClass();
        when(HomeLauncherManagerMocked.isHomeActivity()).thenReturn(true);

        assertTrue(MyTestClass.isHomeActivity());
        verify(HomeLauncherManagerMocked, times(1)).isHomeActivity();
    }

    @Test
    void isHomeActivityFalse()
    {
        KioskManager MyTestClass = createClass();
        when(HomeLauncherManagerMocked.isHomeActivity()).thenReturn(false);

        assertFalse(MyTestClass.isHomeActivity());
        verify(HomeLauncherManagerMocked, times(1)).isHomeActivity();
    }

    @Test
    void becomeHomeActivitySuccess()
    {
        KioskManager MyTestClass = createClass();
        when(DeviceOwnerMocked.isAdminActive()).thenReturn(true);
        when(DeviceOwnerMocked.isDeviceOwner()).thenReturn(true);

        MyTestClass.becomeHomeActivity();
        verify(HomeLauncherManagerMocked, times(1)).becomeHomeActivity(DeviceOwnerMocked);
    }

    @Test
    void becomeHomeActivityFailsAdminActive()
    {
        KioskManager MyTestClass = createClass();
        when(DeviceOwnerMocked.isAdminActive()).thenReturn(false);
        when(DeviceOwnerMocked.isDeviceOwner()).thenReturn(true);

        MyTestClass.becomeHomeActivity();
        verify(HomeLauncherManagerMocked, never()).becomeHomeActivity(DeviceOwnerMocked);
    }

    @Test
    void becomeHomeActivityFailsDeviceOwner()
    {
        KioskManager MyTestClass = createClass();
        when(DeviceOwnerMocked.isAdminActive()).thenReturn(true);
        when(DeviceOwnerMocked.isDeviceOwner()).thenReturn(false);

        MyTestClass.becomeHomeActivity();
        verify(HomeLauncherManagerMocked, never()).becomeHomeActivity(DeviceOwnerMocked);
    }

    KioskManager createClass()
    {
        DeviceOwnerMocked         = mock(DeviceOwner.class);
        HomeLauncherManagerMocked = mock(HomeLauncherManager.class);
        MainActivityMocked        = mock(MainActivity.class);
        MainConfigurationMocked   = mock(MainConfiguration.class);
        return new KioskManager(DeviceOwnerMocked, HomeLauncherManagerMocked, MainActivityMocked, MainConfigurationMocked);
    }

}
