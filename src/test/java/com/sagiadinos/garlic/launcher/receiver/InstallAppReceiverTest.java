package com.sagiadinos.garlic.launcher.receiver;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

import com.sagiadinos.garlic.launcher.helper.Installer;
import com.sagiadinos.garlic.launcher.helper.ShellExecute;
import com.sagiadinos.garlic.launcher.helper.SignatureVerifier;
import com.sagiadinos.garlic.launcher.helper.TaskExecutionReport;
import com.sagiadinos.garlic.launcher.testutil.FakeSharedPreferences;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Contract: action "com.sagiadinos.garlic.launcher.receiver.InstallAppReceiver",
 * extras "apk_path" and "task_id".
 * Only APKs signed like the launcher are installed. APKs from a path containing
 * "cache" (player download cache) are deleted afterwards in every case.
 */
class InstallAppReceiverTest
{
    private static final String ACTION          = "com.sagiadinos.garlic.launcher.receiver.InstallAppReceiver";
    private static final String LAUNCHER_PACKAGE = "com.sagiadinos.garlic.launcher";

    @TempDir
    File tmp;

    Context ContextMocked;
    PackageManager PackageManagerMocked;
    FakeSharedPreferences Prefs;

    @BeforeEach
    void setUp()
    {
        ContextMocked        = mock(Context.class);
        PackageManagerMocked = mock(PackageManager.class);
        Prefs                = FakeSharedPreferences.attachTo(ContextMocked);
        when(ContextMocked.getPackageName()).thenReturn(LAUNCHER_PACKAGE);
        when(ContextMocked.getPackageManager()).thenReturn(PackageManagerMocked);
    }

    @Test
    void onReceiveIntentNull()
    {
        new InstallAppReceiver().onReceive(ContextMocked, null);

        verifyNoInteractions(ContextMocked);
    }

    @Test
    void onReceiveActionNull()
    {
        Intent IntentMocked = mock(Intent.class);
        when(IntentMocked.getAction()).thenReturn(null);

        new InstallAppReceiver().onReceive(ContextMocked, IntentMocked);

        verifyNoInteractions(ContextMocked);
    }

    @Test
    void onReceiveWrongActionDoesNothing()
    {
        Intent IntentMocked = createIntent("some.other.action", "/path/app.apk", "t1");

        try (MockedConstruction<Installer> installer = mockConstruction(Installer.class);
             MockedStatic<TaskExecutionReport> report = mockStatic(TaskExecutionReport.class))
        {
            new InstallAppReceiver().onReceive(ContextMocked, IntentMocked);

            assertEquals(0, installer.constructed().size());
            report.verifyNoInteractions();
        }
    }

    @Test
    void onReceiveInForeignPackageDoesNothing()
    {
        when(ContextMocked.getPackageName()).thenReturn("com.example.other");
        Intent IntentMocked = createIntent(ACTION, "/path/app.apk", "t1");

        try (MockedConstruction<Installer> installer = mockConstruction(Installer.class);
             MockedStatic<TaskExecutionReport> report = mockStatic(TaskExecutionReport.class))
        {
            new InstallAppReceiver().onReceive(ContextMocked, IntentMocked);

            assertEquals(0, installer.constructed().size());
            report.verifyNoInteractions();
        }
    }

    @Test
    void missingApkPathReportsAborted()
    {
        Intent IntentMocked = createIntent(ACTION, null, "t1");

        try (MockedConstruction<Installer> installer = mockConstruction(Installer.class);
             MockedConstruction<SignatureVerifier> verifier = mockConstruction(SignatureVerifier.class);
             MockedStatic<TaskExecutionReport> report = mockStatic(TaskExecutionReport.class))
        {
            new InstallAppReceiver().onReceive(ContextMocked, IntentMocked);

            report.verify(() -> TaskExecutionReport.append("t1", "aborted"), times(1));
            assertEquals(0, verifier.constructed().size());
            verifyNoInteractions(installer.constructed().get(0));
        }
    }

    @Test
    void missingTaskIdUsesEmptyTaskId()
    {
        Intent IntentMocked = createIntent(ACTION, null, null);

        try (MockedConstruction<Installer> ignored = mockConstruction(Installer.class);
             MockedStatic<TaskExecutionReport> report = mockStatic(TaskExecutionReport.class))
        {
            new InstallAppReceiver().onReceive(ContextMocked, IntentMocked);

            report.verify(() -> TaskExecutionReport.append("", "aborted"), times(1));
        }
    }

    @Test
    void foreignSignatureIsRejectedAndCachedApkDeleted() throws IOException
    {
        File apk            = createApk("cache");
        Intent IntentMocked = createIntent(ACTION, apk.getAbsolutePath(), "t1");

        try (MockedConstruction<Installer> installer = mockConstruction(Installer.class);
             MockedConstruction<SignatureVerifier> verifier = mockVerifier(false);
             MockedStatic<TaskExecutionReport> report = mockStatic(TaskExecutionReport.class);
             MockedStatic<Log> ignoredLog = mockStatic(Log.class))
        {
            new InstallAppReceiver().onReceive(ContextMocked, IntentMocked);

            assertEquals(PackageManagerMocked, verifierArgs(verifier)[0]);
            assertEquals(LAUNCHER_PACKAGE, verifierArgs(verifier)[1]);
            report.verify(() -> TaskExecutionReport.append("t1", "aborted"), times(1));
            verifyNoInteractions(installer.constructed().get(0));
            assertFalse(apk.exists());
        }
    }

    @Test
    void foreignSignatureOutsideCacheIsNotDeleted() throws IOException
    {
        File apk            = createApk("usb");
        Intent IntentMocked = createIntent(ACTION, apk.getAbsolutePath(), "t1");

        try (MockedConstruction<Installer> ignored = mockConstruction(Installer.class);
             MockedConstruction<SignatureVerifier> ignoredVerifier = mockVerifier(false);
             MockedStatic<TaskExecutionReport> ignoredReport = mockStatic(TaskExecutionReport.class);
             MockedStatic<Log> ignoredLog = mockStatic(Log.class))
        {
            new InstallAppReceiver().onReceive(ContextMocked, IntentMocked);

            assertTrue(apk.exists());
        }
    }

    @Test
    void notRootedInstallsViaPackageInstaller() throws IOException
    {
        File apk            = createApk("cache");
        Intent IntentMocked = createIntent(ACTION, apk.getAbsolutePath(), "t1");

        try (MockedConstruction<Installer> installer = mockConstruction(Installer.class);
             MockedConstruction<SignatureVerifier> ignoredVerifier = mockVerifier(true);
             MockedStatic<TaskExecutionReport> report = mockStatic(TaskExecutionReport.class))
        {
            new InstallAppReceiver().onReceive(ContextMocked, IntentMocked);

            Installer InstallerMocked = installer.constructed().get(0);
            verify(InstallerMocked, times(1)).installPackage("t1", apk.getAbsolutePath());
            verify(InstallerMocked, never()).installViaRootedShell(any(), anyString());
            // the package installer reports itself asynchronously
            report.verifyNoInteractions();
            assertFalse(apk.exists());
        }
    }

    @Test
    void notRootedInstallFailureReportsAborted() throws IOException
    {
        File apk            = createApk("cache");
        Intent IntentMocked = createIntent(ACTION, apk.getAbsolutePath(), "t1");

        try (MockedConstruction<Installer> ignored = mockConstruction(Installer.class,
                     (mock, context) -> doThrow(new IOException("fail")).when(mock).installPackage(anyString(), anyString()));
             MockedConstruction<SignatureVerifier> ignoredVerifier = mockVerifier(true);
             MockedStatic<TaskExecutionReport> report = mockStatic(TaskExecutionReport.class))
        {
            new InstallAppReceiver().onReceive(ContextMocked, IntentMocked);

            report.verify(() -> TaskExecutionReport.append("t1", "aborted"), times(1));
            assertFalse(apk.exists());
        }
    }

    @Test
    void rootedInstallSuccessReportsCompleted() throws IOException
    {
        Prefs.edit().putBoolean("is_device_rooted", true).commit();
        File apk            = createApk("cache");
        Intent IntentMocked = createIntent(ACTION, apk.getAbsolutePath(), "t1");

        try (MockedConstruction<Installer> installer = mockConstruction(Installer.class,
                     (mock, context) -> when(mock.installViaRootedShell(any(ShellExecute.class), anyString())).thenReturn(true));
             MockedConstruction<SignatureVerifier> ignoredVerifier = mockVerifier(true);
             MockedConstruction<ShellExecute> ignoredShell = mockConstruction(ShellExecute.class);
             MockedStatic<TaskExecutionReport> report = mockStatic(TaskExecutionReport.class))
        {
            new InstallAppReceiver().onReceive(ContextMocked, IntentMocked);

            Installer InstallerMocked = installer.constructed().get(0);
            verify(InstallerMocked, times(1)).installViaRootedShell(any(ShellExecute.class), eq(apk.getAbsolutePath()));
            verify(InstallerMocked, never()).installPackage(anyString(), anyString());
            report.verify(() -> TaskExecutionReport.append("t1", "completed"), times(1));
            assertFalse(apk.exists());
        }
    }

    @Test
    void rootedInstallFailureReportsAborted() throws IOException
    {
        Prefs.edit().putBoolean("is_device_rooted", true).commit();
        File apk            = createApk("cache");
        Intent IntentMocked = createIntent(ACTION, apk.getAbsolutePath(), "t1");

        try (MockedConstruction<Installer> ignored = mockConstruction(Installer.class,
                     (mock, context) -> when(mock.installViaRootedShell(any(ShellExecute.class), anyString())).thenReturn(false));
             MockedConstruction<SignatureVerifier> ignoredVerifier = mockVerifier(true);
             MockedConstruction<ShellExecute> ignoredShell = mockConstruction(ShellExecute.class);
             MockedStatic<TaskExecutionReport> report = mockStatic(TaskExecutionReport.class))
        {
            new InstallAppReceiver().onReceive(ContextMocked, IntentMocked);

            report.verify(() -> TaskExecutionReport.append("t1", "aborted"), times(1));
            assertFalse(apk.exists());
        }
    }

    private File createApk(String sub_dir) throws IOException
    {
        File dir = new File(tmp, sub_dir);
        assertTrue(dir.mkdirs());
        File apk = new File(dir, "garlic-player.apk");
        assertTrue(apk.createNewFile());
        return apk;
    }

    private Intent createIntent(String action, String apk_path, String task_id)
    {
        Intent IntentMocked = mock(Intent.class);
        when(IntentMocked.getAction()).thenReturn(action);
        when(IntentMocked.getStringExtra("apk_path")).thenReturn(apk_path);
        when(IntentMocked.getStringExtra("task_id")).thenReturn(task_id);
        return IntentMocked;
    }

    private MockedConstruction<SignatureVerifier> mockVerifier(boolean signed_like_launcher)
    {
        return mockConstruction(SignatureVerifier.class, (mock, context) -> {
            verifierArguments.addAll(context.arguments());
            when(mock.isSignedLikeReference(anyString())).thenReturn(signed_like_launcher);
        });
    }

    private Object[] verifierArgs(MockedConstruction<SignatureVerifier> verifier)
    {
        assertEquals(1, verifier.constructed().size());
        return verifierArguments.toArray();
    }

    private final List<Object> verifierArguments = new ArrayList<>();
}
