package com.sagiadinos.garlic.launcher.helper;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InstallerTest
{
    @Mock
    Context ContextMocked;
    @Mock
    PackageInstaller PackageInstallerMocked;

    @BeforeEach
    void setUp()
    {
        ContextMocked = mock(Context.class);
        PackageInstallerMocked = mock(PackageInstaller.class);
    }

    @AfterEach
    void tearDown()
    {
        ContextMocked = null;
        PackageInstallerMocked = null;
    }

    @Test
    void isGarlicPlayerInstalled() throws PackageManager.NameNotFoundException
    {
        PackageManager PackageManagerMocked = mock(PackageManager.class);
        when(ContextMocked.getPackageManager()).thenReturn(PackageManagerMocked);

        assertTrue(Installer.isMediaPlayerInstalled(ContextMocked));
        verify(PackageManagerMocked, times(1)).getPackageInfo(DeviceOwner.PLAYER_PACKAGE_NAME, PackageManager.GET_META_DATA);
    }

    @Test
    void installViaShell()
    {
        ShellExecute ShellExecuteMocked = mock(ShellExecute.class);
        PackageManager PackageManagerMocked = mock(PackageManager.class);
        String package_path = "heidewitzka.der.kapitän";

        when(ShellExecuteMocked.executeAsRoot("pm install -r " + package_path + "\n")).thenReturn(true);
        when(ContextMocked.getPackageManager()).thenReturn(PackageManagerMocked);
        when(PackageManagerMocked.getPackageInstaller()).thenReturn(PackageInstallerMocked);

        Installer MyTestClass = new Installer(ContextMocked);

        assertTrue(MyTestClass.installViaRootedShell(ShellExecuteMocked, package_path));

        verify(ShellExecuteMocked, times(1)).executeAsRoot("pm install -r " + package_path + "\n");

    }

    @Test
    void installPackageWithoutParsableApkDoesNothing() throws IOException
    {
        PackageManager PackageManagerMocked = mockPackageManager();
        when(PackageManagerMocked.getPackageArchiveInfo("/path/app.apk", 0)).thenReturn(null);

        new Installer(ContextMocked).installPackage("t1", "/path/app.apk");

        verify(PackageInstallerMocked, never()).createSession(any());
    }

    @Test
    void installPackageWritesApkIntoSessionAndCommits(@TempDir File tmp) throws IOException
    {
        File apk = new File(tmp, "player.apk");
        byte[] content = new byte[40000];
        for (int i = 0; i < content.length; i++)
            content[i] = (byte) (i % 251);
        Files.write(apk.toPath(), content);

        PackageManager PackageManagerMocked = mockPackageManager();
        mockArchivePackage(PackageManagerMocked, apk.getAbsolutePath(), DeviceOwner.PLAYER_PACKAGE_NAME);
        PackageInstaller.Session SessionMocked = mock(PackageInstaller.Session.class);
        ByteArrayOutputStream written          = new ByteArrayOutputStream();
        PendingIntent PendingMocked            = mock(PendingIntent.class);
        IntentSender SenderMocked              = mock(IntentSender.class);
        when(PackageInstallerMocked.createSession(any())).thenReturn(7);
        when(PackageInstallerMocked.openSession(7)).thenReturn(SessionMocked);
        when(SessionMocked.openWrite(DeviceOwner.PLAYER_PACKAGE_NAME, 0, -1)).thenReturn(written);
        when(PendingMocked.getIntentSender()).thenReturn(SenderMocked);

        try (MockedConstruction<PackageInstaller.SessionParams> params = mockConstruction(PackageInstaller.SessionParams.class,
                     (mock, context) -> assertEquals(PackageInstaller.SessionParams.MODE_FULL_INSTALL, context.arguments().get(0)));
             MockedConstruction<Intent> intents = mockConstruction(Intent.class,
                     (mock, context) -> assertEquals("com.sagiadinos.garlic.launcher.ACTION_UPDATE", context.arguments().get(0)));
             MockedStatic<PendingIntent> pending = mockStatic(PendingIntent.class))
        {
            pending.when(() -> PendingIntent.getBroadcast(eq(ContextMocked), eq(7), any(Intent.class), eq(0))).thenReturn(PendingMocked);

            new Installer(ContextMocked).installPackage("t1", apk.getAbsolutePath());

            verify(params.constructed().get(0), times(1)).setAppPackageName(DeviceOwner.PLAYER_PACKAGE_NAME);
            assertArrayEquals(content, written.toByteArray());
            verify(SessionMocked, times(1)).fsync(written);
            verify(SessionMocked, times(1)).commit(SenderMocked);
            verify(SessionMocked, times(1)).close();
            assertEquals(1, intents.constructed().size());
        }
    }

    @Test
    void installPackageReportsResultOfSession(@TempDir File tmp) throws IOException
    {
        File apk = new File(tmp, "player.apk");
        Files.write(apk.toPath(), new byte[]{1, 2, 3});
        PackageManager PackageManagerMocked = mockPackageManager();
        mockArchivePackage(PackageManagerMocked, apk.getAbsolutePath(), DeviceOwner.PLAYER_PACKAGE_NAME);
        PackageInstaller.Session SessionMocked = mock(PackageInstaller.Session.class);
        when(PackageInstallerMocked.createSession(any())).thenReturn(7);
        when(PackageInstallerMocked.openSession(7)).thenReturn(SessionMocked);
        when(SessionMocked.openWrite(anyString(), anyLong(), anyLong())).thenReturn(new ByteArrayOutputStream());

        try (MockedConstruction<PackageInstaller.SessionParams> ignoredParams = mockConstruction(PackageInstaller.SessionParams.class);
             MockedConstruction<Intent> ignoredIntents = mockConstruction(Intent.class);
             MockedStatic<PendingIntent> pending = mockStatic(PendingIntent.class);
             MockedStatic<TaskExecutionReport> report = mockStatic(TaskExecutionReport.class))
        {
            pending.when(() -> PendingIntent.getBroadcast(any(), anyInt(), any(), anyInt())).thenReturn(mock(PendingIntent.class));

            new Installer(ContextMocked).installPackage("task-5", apk.getAbsolutePath());

            ArgumentCaptor<PackageInstaller.SessionCallback> callback = ArgumentCaptor.forClass(PackageInstaller.SessionCallback.class);
            verify(PackageInstallerMocked, times(1)).registerSessionCallback(callback.capture());

            // progress events report nothing
            callback.getValue().onCreated(7);
            callback.getValue().onBadgingChanged(7);
            callback.getValue().onActiveChanged(7, true);
            callback.getValue().onProgressChanged(7, 0.5f);
            report.verifyNoInteractions();

            callback.getValue().onFinished(7, true);
            report.verify(() -> TaskExecutionReport.append("task-5", "completed"), times(1));

            callback.getValue().onFinished(7, false);
            report.verify(() -> TaskExecutionReport.append("task-5", "aborted"), times(1));
        }
    }

    @Test
    void installPackageWithMissingFileThrows() throws IOException
    {
        PackageManager PackageManagerMocked = mockPackageManager();
        mockArchivePackage(PackageManagerMocked, "/missing.apk", DeviceOwner.PLAYER_PACKAGE_NAME);

        try (MockedConstruction<PackageInstaller.SessionParams> ignored = mockConstruction(PackageInstaller.SessionParams.class))
        {
            when(PackageInstallerMocked.openSession(anyInt())).thenReturn(mock(PackageInstaller.Session.class));
            Installer MyTestClass = new Installer(ContextMocked);

            assertThrows(FileNotFoundException.class, () -> MyTestClass.installPackage("t1", "/missing.apk"));
        }
    }

    @Test
    void uninstallInstalledPackage() throws PackageManager.NameNotFoundException
    {
        PackageManager PackageManagerMocked = mockPackageManager();
        PendingIntent PendingMocked         = mock(PendingIntent.class);
        IntentSender SenderMocked           = mock(IntentSender.class);
        when(PendingMocked.getIntentSender()).thenReturn(SenderMocked);

        try (MockedConstruction<Intent> ignored = mockConstruction(Intent.class);
             MockedStatic<PendingIntent> pending = mockStatic(PendingIntent.class))
        {
            pending.when(() -> PendingIntent.getActivity(eq(ContextMocked), eq(0), any(Intent.class), eq(0))).thenReturn(PendingMocked);

            new Installer(ContextMocked).uninstall("com.example.app");

            verify(PackageManagerMocked, times(1)).getPackageInfo("com.example.app", PackageManager.GET_META_DATA);
            verify(PackageInstallerMocked, times(1)).uninstall("com.example.app", SenderMocked);
        }
    }

    @Test
    void uninstallMissingPackageDoesNothing() throws PackageManager.NameNotFoundException
    {
        PackageManager PackageManagerMocked = mockPackageManager();
        when(PackageManagerMocked.getPackageInfo("com.example.app", PackageManager.GET_META_DATA))
                .thenThrow(mock(PackageManager.NameNotFoundException.class));

        new Installer(ContextMocked).uninstall("com.example.app");

        verify(PackageInstallerMocked, never()).uninstall(anyString(), any());
    }

    @Test
    void hasPlayerPermissionsBothGranted() throws PackageManager.NameNotFoundException
    {
        mockPlayerPermissions(new String[]{"android.permission.READ_EXTERNAL_STORAGE", "android.permission.INTERNET", "android.permission.WRITE_EXTERNAL_STORAGE"},
                new int[]{PackageInfo.REQUESTED_PERMISSION_GRANTED, 0, PackageInfo.REQUESTED_PERMISSION_GRANTED});

        assertTrue(Installer.hasPlayerPermissions(ContextMocked));
    }

    @Test
    void hasPlayerPermissionsOnlyRead() throws PackageManager.NameNotFoundException
    {
        mockPlayerPermissions(new String[]{"android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE"},
                new int[]{PackageInfo.REQUESTED_PERMISSION_GRANTED, 0});

        assertFalse(Installer.hasPlayerPermissions(ContextMocked));
    }

    @Test
    void hasPlayerPermissionsNoneRequested() throws PackageManager.NameNotFoundException
    {
        mockPlayerPermissions(null, null);

        assertFalse(Installer.hasPlayerPermissions(ContextMocked));
    }

    @Test
    void hasPlayerPermissionsPlayerNotInstalled() throws PackageManager.NameNotFoundException
    {
        PackageManager PackageManagerMocked = mockPackageManager();
        when(PackageManagerMocked.getPackageInfo(DeviceOwner.PLAYER_PACKAGE_NAME, PackageManager.GET_PERMISSIONS))
                .thenThrow(mock(PackageManager.NameNotFoundException.class));

        assertFalse(Installer.hasPlayerPermissions(ContextMocked));
    }

    @Test
    void determineOtherLauncherSkipsGarlicLauncher()
    {
        PackageManager PackageManagerMocked = mock(PackageManager.class);
        ResolveInfo garlic = createResolveInfo(DeviceOwner.LAUNCHER_PACKAGE_NAME);
        ResolveInfo other  = createResolveInfo("com.android.launcher3");

        try (MockedConstruction<Intent> intents = mockConstruction(Intent.class))
        {
            when(PackageManagerMocked.queryIntentActivities(any(Intent.class), eq(0))).thenReturn(Arrays.asList(garlic, other));

            assertSame(other, Installer.determineOtherLauncherPackagename(PackageManagerMocked));
            verify(intents.constructed().get(0), times(1)).addCategory(Intent.CATEGORY_HOME);
        }
    }

    @Test
    void determineOtherLauncherWithoutOther()
    {
        PackageManager PackageManagerMocked = mock(PackageManager.class);

        try (MockedConstruction<Intent> ignored = mockConstruction(Intent.class))
        {
            when(PackageManagerMocked.queryIntentActivities(any(Intent.class), eq(0)))
                    .thenReturn(Collections.singletonList(createResolveInfo(DeviceOwner.LAUNCHER_PACKAGE_NAME)));

            assertNull(Installer.determineOtherLauncherPackagename(PackageManagerMocked));
        }
    }

    private PackageManager mockPackageManager()
    {
        PackageManager PackageManagerMocked = mock(PackageManager.class);
        when(ContextMocked.getPackageManager()).thenReturn(PackageManagerMocked);
        when(PackageManagerMocked.getPackageInstaller()).thenReturn(PackageInstallerMocked);
        return PackageManagerMocked;
    }

    private void mockArchivePackage(PackageManager pm, String path, String package_name)
    {
        PackageInfo info = mock(PackageInfo.class);
        info.packageName = package_name;
        when(pm.getPackageArchiveInfo(path, 0)).thenReturn(info);
    }

    private void mockPlayerPermissions(String[] permissions, int[] flags) throws PackageManager.NameNotFoundException
    {
        PackageManager PackageManagerMocked = mockPackageManager();
        PackageInfo info               = mock(PackageInfo.class);
        info.requestedPermissions      = permissions;
        info.requestedPermissionsFlags = flags;
        when(PackageManagerMocked.getPackageInfo(DeviceOwner.PLAYER_PACKAGE_NAME, PackageManager.GET_PERMISSIONS)).thenReturn(info);
    }

    private ResolveInfo createResolveInfo(String package_name)
    {
        ResolveInfo info                = mock(ResolveInfo.class);
        info.activityInfo               = mock(ActivityInfo.class);
        info.activityInfo.packageName   = package_name;
        return info;
    }

    @Test
    void isPackageInstalled() throws PackageManager.NameNotFoundException
    {
        PackageManager PackageManagerMocked = mock(PackageManager.class);
        when(ContextMocked.getPackageManager()).thenReturn(PackageManagerMocked);
        String check_package = "com.sagiadinos.test";

        assertTrue(Installer.isPackageInstalled(ContextMocked, check_package));
        verify(PackageManagerMocked, times(1)).getPackageInfo(check_package, PackageManager.GET_META_DATA);
    }

    @Test
    void isPackageInstalledFails() throws PackageManager.NameNotFoundException
    {
        PackageManager PackageManagerMocked = mock(PackageManager.class);
        when(ContextMocked.getPackageManager()).thenReturn(PackageManagerMocked);
        String check_package = "com.sagiadinos.test";

        when(PackageManagerMocked.getPackageInfo(check_package, PackageManager.GET_META_DATA)).thenThrow(new PackageManager.NameNotFoundException());

        assertFalse(Installer.isPackageInstalled(ContextMocked, check_package));

    }

    @Test
    void getAppNameFromPkgNameSucceed()
    {
        PackageManager PackageManagerMocked = mock(PackageManager.class);
        PackageInfo    PackageInfoMocked    = mock(PackageInfo.class);
        PackageInfoMocked.packageName = "the_package_name";
        String         file_path = "the/path/to/file.apk";
        String         expected  = "the_package_name";

        when(ContextMocked.getPackageManager()).thenReturn(PackageManagerMocked);
        when(PackageManagerMocked.getPackageArchiveInfo(file_path, 0)).thenReturn(PackageInfoMocked);

        assertEquals(expected, Installer.getAppNameFromPkgName(ContextMocked, file_path));

        //assertEquals(expected, verify(PackageInfoMocked, times(1)).packageName);

    }

    @Test
    void getAppNameFromPkgNameFails()
    {
        PackageManager PackageManagerMocked = mock(PackageManager.class);
        PackageInfo    PackageInfoMocked    = mock(PackageInfo.class);
        String         file_path = "the/path/to/file.apk";
        when(ContextMocked.getPackageManager()).thenReturn(PackageManagerMocked);
        when(PackageManagerMocked.getPackageArchiveInfo(file_path, 0)).thenReturn(null);

        assertEquals("", Installer.getAppNameFromPkgName(ContextMocked, file_path));

      //  assertNull(verify(PackageInfoMocked, times(0)).packageName);
    }
}