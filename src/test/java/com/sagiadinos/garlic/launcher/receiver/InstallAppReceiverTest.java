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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
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
 * The APK is copied to the private cache of the launcher first; only this copy
 * is checked and installed, so the sender cannot swap the file in between.
 * Only APKs signed like the launcher are installed. APKs from a path containing
 * "cache" (player download cache) are deleted afterwards in every case.
 */
class InstallAppReceiverTest
{
    private static final String ACTION           = "com.sagiadinos.garlic.launcher.receiver.InstallAppReceiver";
    private static final String LAUNCHER_PACKAGE = "com.sagiadinos.garlic.launcher";
    private static final String APK_CONTENT      = "signed apk";

    @TempDir
    File tmp;

    Context ContextMocked;
    PackageManager PackageManagerMocked;
    FakeSharedPreferences Prefs;
    File private_copy;
    List<Object> verifierArguments;
    List<String> verifiedContent;

    @BeforeEach
    void setUp()
    {
        ContextMocked        = mock(Context.class);
        PackageManagerMocked = mock(PackageManager.class);
        Prefs                = FakeSharedPreferences.attachTo(ContextMocked);
        verifierArguments    = new ArrayList<>();
        verifiedContent      = new ArrayList<>();

        File launcher_cache = new File(tmp, "launcher/cache");
        private_copy        = new File(launcher_cache, "install/install.apk");
        when(ContextMocked.getPackageName()).thenReturn(LAUNCHER_PACKAGE);
        when(ContextMocked.getPackageManager()).thenReturn(PackageManagerMocked);
        when(ContextMocked.getCacheDir()).thenReturn(launcher_cache);
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
    void unreadableApkReportsAborted()
    {
        Intent IntentMocked = createIntent(ACTION, new File(tmp, "cache/missing.apk").getAbsolutePath(), "t1");

        try (MockedConstruction<Installer> installer = mockConstruction(Installer.class);
             MockedConstruction<SignatureVerifier> verifier = mockConstruction(SignatureVerifier.class);
             MockedStatic<TaskExecutionReport> report = mockStatic(TaskExecutionReport.class);
             MockedStatic<Log> ignoredLog = mockStatic(Log.class))
        {
            new InstallAppReceiver().onReceive(ContextMocked, IntentMocked);

            report.verify(() -> TaskExecutionReport.append("t1", "aborted"), times(1));
            assertEquals(0, verifier.constructed().size());
            verifyNoInteractions(installer.constructed().get(0));
            assertFalse(private_copy.exists());
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

            assertEquals(1, verifier.constructed().size());
            assertEquals(List.of(PackageManagerMocked, LAUNCHER_PACKAGE), verifierArguments);
            verify(verifier.constructed().get(0), times(1)).isSignedLikeReference(private_copy.getAbsolutePath());
            report.verify(() -> TaskExecutionReport.append("t1", "aborted"), times(1));
            verifyNoInteractions(installer.constructed().get(0));
            assertFalse(apk.exists());
            assertFalse(private_copy.exists());
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
            assertFalse(private_copy.exists());
        }
    }

    @Test
    void notRootedInstallsPrivateCopyViaPackageInstaller() throws IOException
    {
        File apk            = createApk("cache");
        Intent IntentMocked = createIntent(ACTION, apk.getAbsolutePath(), "t1");
        List<String> installedContent = new ArrayList<>();

        try (MockedConstruction<Installer> installer = mockConstruction(Installer.class, (mock, context) ->
                     doAnswer(i -> installedContent.add(read(new File((String) i.getArgument(1)))))
                             .when(mock).installPackage(anyString(), anyString()));
             MockedConstruction<SignatureVerifier> verifier = mockVerifier(true);
             MockedStatic<TaskExecutionReport> report = mockStatic(TaskExecutionReport.class))
        {
            new InstallAppReceiver().onReceive(ContextMocked, IntentMocked);

            Installer InstallerMocked = installer.constructed().get(0);
            verify(verifier.constructed().get(0), times(1)).isSignedLikeReference(private_copy.getAbsolutePath());
            verify(InstallerMocked, times(1)).installPackage("t1", private_copy.getAbsolutePath());
            verify(InstallerMocked, never()).installViaRootedShell(any(), anyString());
            assertEquals(List.of(APK_CONTENT), installedContent);
            // the package installer reports itself asynchronously
            report.verifyNoInteractions();
            assertFalse(apk.exists());
            assertFalse(private_copy.exists());
        }
    }

    /**
     * The sender swaps its file after the launcher copied it.
     * The checked and the installed file must still be the same.
     */
    @Test
    void swappedSourceAfterCopyDoesNotReachInstaller() throws IOException
    {
        File apk            = createApk("usb");
        Intent IntentMocked = createIntent(ACTION, apk.getAbsolutePath(), "t1");
        List<String> installedContent = new ArrayList<>();

        try (MockedConstruction<Installer> ignored = mockConstruction(Installer.class, (mock, context) ->
                     doAnswer(i -> installedContent.add(read(new File((String) i.getArgument(1)))))
                             .when(mock).installPackage(anyString(), anyString()));
             MockedConstruction<SignatureVerifier> ignoredVerifier = mockConstruction(SignatureVerifier.class, (mock, context) ->
                     when(mock.isSignedLikeReference(anyString())).thenAnswer(i -> {
                         verifiedContent.add(read(new File((String) i.getArgument(0))));
                         write(apk, "malicious apk");
                         return true;
                     }));
             MockedStatic<TaskExecutionReport> ignoredReport = mockStatic(TaskExecutionReport.class))
        {
            new InstallAppReceiver().onReceive(ContextMocked, IntentMocked);

            assertEquals(List.of(APK_CONTENT), verifiedContent);
            assertEquals(List.of(APK_CONTENT), installedContent);
            assertEquals("malicious apk", read(apk));
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
            assertFalse(private_copy.exists());
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
            verify(InstallerMocked, times(1)).installViaRootedShell(any(ShellExecute.class), eq(private_copy.getAbsolutePath()));
            verify(InstallerMocked, never()).installPackage(anyString(), anyString());
            report.verify(() -> TaskExecutionReport.append("t1", "completed"), times(1));
            assertFalse(apk.exists());
            assertFalse(private_copy.exists());
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
            assertFalse(private_copy.exists());
        }
    }

    @Test
    void previousCopyIsOverwritten() throws IOException
    {
        assertTrue(private_copy.getParentFile().mkdirs());
        write(private_copy, "left over from an earlier run with more content");
        File apk            = createApk("usb");
        Intent IntentMocked = createIntent(ACTION, apk.getAbsolutePath(), "t1");

        try (MockedConstruction<Installer> ignored = mockConstruction(Installer.class);
             MockedConstruction<SignatureVerifier> ignoredVerifier = mockConstruction(SignatureVerifier.class, (mock, context) ->
                     when(mock.isSignedLikeReference(anyString())).thenAnswer(i -> {
                         verifiedContent.add(read(new File((String) i.getArgument(0))));
                         return true;
                     }));
             MockedStatic<TaskExecutionReport> ignoredReport = mockStatic(TaskExecutionReport.class))
        {
            new InstallAppReceiver().onReceive(ContextMocked, IntentMocked);

            assertEquals(List.of(APK_CONTENT), verifiedContent);
        }
    }

    private File createApk(String sub_dir) throws IOException
    {
        File dir = new File(tmp, sub_dir);
        assertTrue(dir.mkdirs());
        return write(new File(dir, "garlic-player.apk"), APK_CONTENT);
    }

    private static File write(File file, String content) throws IOException
    {
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static String read(File file) throws IOException
    {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
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
}
