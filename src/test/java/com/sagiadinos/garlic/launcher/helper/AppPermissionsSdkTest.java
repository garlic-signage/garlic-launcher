package com.sagiadinos.garlic.launcher.helper;

import android.Manifest;
import android.app.Activity;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;

import com.sagiadinos.garlic.launcher.configuration.MainConfiguration;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowEnvironment;
import org.robolectric.shadows.ShadowSettings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

/**
 * The permissions from Android 8 (install packages) and Android 11 (all files access).
 * AppPermissionsTest covers the base permissions.
 */
@RunWith(RobolectricTestRunner.class)
public class AppPermissionsSdkTest
{
    private static final String LAUNCHER = "com.sagiadinos.garlic.launcher";

    Activity ActivityMocked;
    PackageManager PackageManagerMocked;
    MainConfiguration MainConfigurationMocked;
    ShellExecute ShellExecuteMocked;

    @Before
    public void setUp()
    {
        ActivityMocked          = mock(Activity.class);
        PackageManagerMocked    = mock(PackageManager.class);
        MainConfigurationMocked = mock(MainConfiguration.class);
        ShellExecuteMocked      = mock(ShellExecute.class);
        when(ActivityMocked.getPackageManager()).thenReturn(PackageManagerMocked);
        when(ActivityMocked.getPackageName()).thenReturn(LAUNCHER);
        when(ActivityMocked.checkSelfPermission(anyString())).thenReturn(PackageManager.PERMISSION_GRANTED);
        // Environment.isExternalStorageManager() needs an external storage dir
        ShadowEnvironment.addExternalDir("external");
    }

    // ------------------------------------------------ hasAllPermissions

    @Test
    @Config(sdk = 25)
    public void android7NeedsOnlyBasePermissions()
    {
        // canRequestPackageInstalls() does not exist before Android 8
        assertTrue(AppPermissions.hasAllPermissions(ActivityMocked));
    }

    @Test
    @Config(sdk = 26)
    public void android8NeedsInstallPermission()
    {
        when(PackageManagerMocked.canRequestPackageInstalls()).thenReturn(false);
        assertFalse(AppPermissions.hasAllPermissions(ActivityMocked));

        when(PackageManagerMocked.canRequestPackageInstalls()).thenReturn(true);
        assertTrue(AppPermissions.hasAllPermissions(ActivityMocked));
    }

    @Test
    @Config(sdk = {30, 35})
    public void android11NeedsAllFilesAccess()
    {
        when(PackageManagerMocked.canRequestPackageInstalls()).thenReturn(true);
        setAllFilesAccess(false);
        assertFalse(AppPermissions.hasAllPermissions(ActivityMocked));

        setAllFilesAccess(true);
        assertTrue(AppPermissions.hasAllPermissions(ActivityMocked));
    }

    @Test
    @Config(sdk = {30, 35})
    public void basePermissionMissingIsCheckedFirst()
    {
        when(ActivityMocked.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)).thenReturn(PackageManager.PERMISSION_DENIED);

        assertFalse(AppPermissions.hasAllPermissions(ActivityMocked));
        verify(PackageManagerMocked, never()).canRequestPackageInstalls();
    }

    // ------------------------------------------------ handleAllPermissions

    @Test
    @Config(sdk = 26)
    public void android8NotRootedCannotRequestInstallPermission()
    {
        when(PackageManagerMocked.canRequestPackageInstalls()).thenReturn(false);

        create().handleAllPermissions();

        verify(ActivityMocked, never()).startActivity(any());
        verify(ActivityMocked, never()).startActivityForResult(any(), anyInt());
        verify(ShellExecuteMocked, never()).executeAsRoot(anyString());
    }

    @Test
    @Config(sdk = {30, 35})
    public void android11NotRootedOpensAllFilesAccessSettings()
    {
        when(PackageManagerMocked.canRequestPackageInstalls()).thenReturn(true);
        setAllFilesAccess(false);

        create().handleAllPermissions();

        ArgumentCaptor<Intent> intent = ArgumentCaptor.forClass(Intent.class);
        verify(ActivityMocked, times(1)).startActivityForResult(intent.capture(), eq(100));
        assertEquals(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, intent.getValue().getAction());
        assertEquals("package:" + LAUNCHER, intent.getValue().getData().toString());
    }

    @Test
    @Config(sdk = 30)
    public void android11FallsBackToGeneralAllFilesSettings()
    {
        when(PackageManagerMocked.canRequestPackageInstalls()).thenReturn(true);
        setAllFilesAccess(false);
        doThrow(new RuntimeException("no activity")).when(ActivityMocked).startActivityForResult(any(), anyInt());

        create().handleAllPermissions();

        ArgumentCaptor<Intent> intent = ArgumentCaptor.forClass(Intent.class);
        verify(ActivityMocked, times(1)).startActivity(intent.capture());
        assertEquals(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION, intent.getValue().getAction());
    }

    @Test
    @Config(sdk = {30, 35})
    public void rootedGrantsMissingPermissionsViaAppOps()
    {
        when(MainConfigurationMocked.isDeviceRooted()).thenReturn(true);
        when(PackageManagerMocked.canRequestPackageInstalls()).thenReturn(false);
        when(ShellExecuteMocked.executeAsRoot(anyString())).thenReturn(true);
        setAllFilesAccess(false);

        create().handleAllPermissions();

        verify(ShellExecuteMocked, times(1)).executeAsRoot("appops set --uid " + LAUNCHER + " REQUEST_INSTALL_PACKAGES allow");
        verify(ShellExecuteMocked, times(1)).executeAsRoot("appops set --uid " + LAUNCHER + " MANAGE_EXTERNAL_STORAGE allow");
        verify(ActivityMocked, never()).startActivityForResult(any(), anyInt());
    }

    @Test
    @Config(sdk = 35)
    public void allGrantedRequestsNothing()
    {
        when(PackageManagerMocked.canRequestPackageInstalls()).thenReturn(true);
        setAllFilesAccess(true);

        create().handleAllPermissions();

        verify(ActivityMocked, never()).startActivityForResult(any(), anyInt());
        verify(ActivityMocked, never()).requestPermissions(any(), anyInt());
    }

    // ------------------------------------------------ overlay

    @Test
    @Config(sdk = 25)
    public void overlayGrantedNeedsNoRequest()
    {
        ShadowSettings.setCanDrawOverlays(true);
        when(MainConfigurationMocked.isDeviceRooted()).thenReturn(true);
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);

        assertTrue(create().verifyOverlayPermissions(intent));
        verify(ActivityMocked, never()).startActivityForResult(any(), anyInt());
    }

    @Test
    @Config(sdk = 25)
    public void overlayMissingOnRootedDeviceIsRequested()
    {
        ShadowSettings.setCanDrawOverlays(false);
        when(MainConfigurationMocked.isDeviceRooted()).thenReturn(true);
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);

        assertFalse(create().verifyOverlayPermissions(intent));
        verify(ActivityMocked, times(1)).startActivityForResult(intent, 12);
    }

    @Test
    @Config(sdk = 25)
    public void overlayMissingNotRootedIsNotRequested()
    {
        ShadowSettings.setCanDrawOverlays(false);
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);

        assertFalse(create().verifyOverlayPermissions(intent));
        verify(ActivityMocked, never()).startActivityForResult(any(), anyInt());
    }

    private AppPermissions create()
    {
        return new AppPermissions(ActivityMocked, MainConfigurationMocked, ShellExecuteMocked);
    }

    /** Environment.isExternalStorageManager() reads this app op */
    private void setAllFilesAccess(boolean allowed)
    {
        assertTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R);
        Context app = RuntimeEnvironment.getApplication();
        AppOpsManager appOps = (AppOpsManager) app.getSystemService(Context.APP_OPS_SERVICE);
        shadowOf(appOps).setMode("android:manage_external_storage", Process.myUid(), app.getPackageName(),
                allowed ? AppOpsManager.MODE_ALLOWED : AppOpsManager.MODE_ERRORED);
        assertEquals(allowed, android.os.Environment.isExternalStorageManager());
    }
}
