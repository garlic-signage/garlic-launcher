package com.sagiadinos.garlic.launcher.helper;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.util.Log;

import com.sagiadinos.garlic.launcher.configuration.MainConfiguration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Build.VERSION.SDK_INT is 0 in local unit tests, so only the branches
 * below Android O (base storage permissions) are covered here.
 */
class AppPermissionsTest
{
    private static final int REQUEST_PERMISSIONS = 1;
    private static final String[] BASE_PERMISSIONS_LIST = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
    };

    MainConfiguration MainConfigurationMocked;
    Activity          ActivityMocked;
    ShellExecute      ShellExecuteMocked;

    @AfterEach
    void tearDown()
    {
        MainConfigurationMocked = null;
        ActivityMocked          = null;
        ShellExecuteMocked      = null;
    }

    @Test
    void onRequestPermissionsResultGranted()
    {
        ActivityMocked = mock(Activity.class);
        grantBasePermissions(true, true);

        AppPermissions.onRequestPermissionsResult(ActivityMocked, REQUEST_PERMISSIONS, BASE_PERMISSIONS_LIST, new int[]{0, 0});

        verify(ActivityMocked, times(1)).recreate();
    }

    @Test
    void onRequestPermissionsResultNotGranted()
    {
        ActivityMocked = mock(Activity.class);
        grantBasePermissions(true, false);

        AppPermissions.onRequestPermissionsResult(ActivityMocked, REQUEST_PERMISSIONS, BASE_PERMISSIONS_LIST, new int[]{0, -1});

        verify(ActivityMocked, never()).recreate();
    }

    @Test
    void onRequestPermissionsResultOtherRequestCode()
    {
        ActivityMocked = mock(Activity.class);
        grantBasePermissions(true, true);

        AppPermissions.onRequestPermissionsResult(ActivityMocked, 99, BASE_PERMISSIONS_LIST, new int[]{0, 0});

        verify(ActivityMocked, never()).recreate();
        verify(ActivityMocked, never()).checkSelfPermission(anyString());
    }

    @Test
    void onRequestPermissionsResultEmpty()
    {
        ActivityMocked = mock(Activity.class);
        grantBasePermissions(true, true);

        AppPermissions.onRequestPermissionsResult(ActivityMocked, REQUEST_PERMISSIONS, BASE_PERMISSIONS_LIST, new int[]{});

        verify(ActivityMocked, never()).recreate();
    }

    @Test
    void hasAllPermissionsWriteNotGranted()
    {
        ActivityMocked = mock(Activity.class);
        grantBasePermissions(false, true);

        assertFalse(AppPermissions.hasAllPermissions(ActivityMocked));
        verify(ActivityMocked, times(1)).checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        verify(ActivityMocked, never()).checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE);
    }

    @Test
    void hasAllPermissionsReadNotGranted()
    {
        ActivityMocked = mock(Activity.class);
        grantBasePermissions(true, false);

        assertFalse(AppPermissions.hasAllPermissions(ActivityMocked));
        verify(ActivityMocked, times(1)).checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        verify(ActivityMocked, times(1)).checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE);
    }

    @Test
    void hasAllPermissionsGranted()
    {
        ActivityMocked = mock(Activity.class);
        grantBasePermissions(true, true);

        assertTrue(AppPermissions.hasAllPermissions(ActivityMocked));
    }

    @Test
    void handleAllPermissionsAsRoot()
    {
        AppPermissions MyTestClass = createClass();
        grantBasePermissions(false, false);
        when(MainConfigurationMocked.isDeviceRooted()).thenReturn(true);
        when(ShellExecuteMocked.executeAsRoot(anyString())).thenReturn(true);

        MyTestClass.handleAllPermissions();

        verify(ShellExecuteMocked, times(1)).executeAsRoot("pm grant " + DeviceOwner.LAUNCHER_PACKAGE_NAME + " android.permission.WRITE_EXTERNAL_STORAGE");
        verify(ShellExecuteMocked, times(1)).executeAsRoot("pm grant " + DeviceOwner.LAUNCHER_PACKAGE_NAME + " android.permission.READ_EXTERNAL_STORAGE");
        verify(ActivityMocked, never()).requestPermissions(any(String[].class), any(Integer.class));
        assertEquals("", MyTestClass.getErrorText());
    }

    @Test
    void handleAllPermissionsAsRootFails()
    {
        AppPermissions MyTestClass = createClass();
        grantBasePermissions(true, false);
        when(MainConfigurationMocked.isDeviceRooted()).thenReturn(true);
        when(ShellExecuteMocked.executeAsRoot(anyString())).thenReturn(false);
        when(ShellExecuteMocked.getErrorText()).thenReturn("error");

        try (MockedStatic<Log> ignored = mockStatic(Log.class))
        {
            MyTestClass.handleAllPermissions();
        }

        verify(ShellExecuteMocked, times(1)).executeAsRoot(anyString());
        assertTrue(MyTestClass.getErrorText().contains("READ_EXTERNAL_STORAGE"));
        assertTrue(MyTestClass.getErrorText().contains("error"));
    }

    @Test
    void handleAllPermissionsAsNonRoot()
    {
        AppPermissions MyTestClass = createClass();
        grantBasePermissions(true, false);
        when(MainConfigurationMocked.isDeviceRooted()).thenReturn(false);

        MyTestClass.handleAllPermissions();

        verify(ShellExecuteMocked, never()).executeAsRoot(anyString());
        verify(ActivityMocked, times(1)).requestPermissions(BASE_PERMISSIONS_LIST, REQUEST_PERMISSIONS);
    }

    @Test
    void handleAllPermissionsAlreadyGranted()
    {
        AppPermissions MyTestClass = createClass();
        grantBasePermissions(true, true);

        MyTestClass.handleAllPermissions();

        verify(ShellExecuteMocked, never()).executeAsRoot(anyString());
        verify(ActivityMocked, never()).requestPermissions(any(String[].class), any(Integer.class));
    }

    @Test
    void grantPlayerPermissionsNotRooted()
    {
        AppPermissions MyTestClass = createClass();
        when(MainConfigurationMocked.isDeviceRooted()).thenReturn(false);

        assertFalse(MyTestClass.grantPlayerPermissions());
        verify(ShellExecuteMocked, never()).executeAsRoot(anyString());
    }

    @Test
    void grantPlayerPermissionsRooted()
    {
        AppPermissions MyTestClass = createClass();
        when(MainConfigurationMocked.isDeviceRooted()).thenReturn(true);
        when(ShellExecuteMocked.executeAsRoot(anyString())).thenReturn(true);

        assertTrue(MyTestClass.grantPlayerPermissions());
        verify(ShellExecuteMocked, times(1)).executeAsRoot("pm grant " + DeviceOwner.PLAYER_PACKAGE_NAME + " android.permission.READ_EXTERNAL_STORAGE");
        verify(ShellExecuteMocked, times(1)).executeAsRoot("pm grant " + DeviceOwner.PLAYER_PACKAGE_NAME + " android.permission.WRITE_EXTERNAL_STORAGE");
    }

    @Test
    void grantPlayerPermissionsRootedFails()
    {
        AppPermissions MyTestClass = createClass();
        when(MainConfigurationMocked.isDeviceRooted()).thenReturn(true);
        when(ShellExecuteMocked.executeAsRoot(anyString())).thenReturn(false);

        try (MockedStatic<Log> ignored = mockStatic(Log.class))
        {
            assertFalse(MyTestClass.grantPlayerPermissions());
        }
        verify(ShellExecuteMocked, times(1)).executeAsRoot(anyString());
    }

    void grantBasePermissions(boolean write, boolean read)
    {
        when(ActivityMocked.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE))
                .thenReturn(write ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED);
        when(ActivityMocked.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE))
                .thenReturn(read ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED);
    }

    AppPermissions createClass()
    {
        MainConfigurationMocked = mock(MainConfiguration.class);
        ActivityMocked          = mock(Activity.class);
        ShellExecuteMocked      = mock(ShellExecute.class);

        return new AppPermissions(ActivityMocked, MainConfigurationMocked, ShellExecuteMocked);
    }

}
