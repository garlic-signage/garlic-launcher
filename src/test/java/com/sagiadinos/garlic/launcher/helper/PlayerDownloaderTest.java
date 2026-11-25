package com.sagiadinos.garlic.launcher.helper;

import android.app.Activity;
import android.content.Intent;
import android.net.Network;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.sagiadinos.garlic.launcher.R;

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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Downloads from a file: URL instead of the network.
 */
class PlayerDownloaderTest
{
    private static final String PLAYER_PACKAGE = "com.sagiadinos.garlic.player";
    private static final String INSTALL_ACTION = "com.sagiadinos.garlic.launcher.receiver.InstallAppReceiver";

    @TempDir
    File tmp;

    Activity ActivityMocked;
    ProgressBar ProgressBarMocked;
    TextView TextViewMocked;
    File cache_dir;
    List<String> informationTexts;
    List<String> sentActions;

    @BeforeEach
    void setUp()
    {
        ActivityMocked    = mock(Activity.class);
        ProgressBarMocked = mock(ProgressBar.class);
        TextViewMocked    = mock(TextView.class);
        cache_dir         = new File(tmp, "cache");
        assertTrue(cache_dir.mkdirs());
        informationTexts  = new ArrayList<>();
        sentActions       = new ArrayList<>();

        when(ActivityMocked.getExternalCacheDir()).thenReturn(cache_dir);
        when(ActivityMocked.getString(R.string.download_player_in_progress)).thenReturn("download");
        when(ActivityMocked.getString(R.string.install_player_in_progress)).thenReturn("install");
        when(ActivityMocked.getString(R.string.no_network)).thenReturn("no network");
        // the UI thread is the test thread
        doAnswer(i -> {
            ((Runnable) i.getArgument(0)).run();
            return null;
        }).when(ActivityMocked).runOnUiThread(any(Runnable.class));
        doAnswer(i -> informationTexts.add(String.valueOf((Object) i.getArgument(0))))
                .when(TextViewMocked).setText(any(CharSequence.class));
    }

    @Test
    void defaultsDownloadFromGarlicSignageAfterWaiting()
    {
        assertEquals("https://garlic-signage.com/downloads/garlic-player.apk", PlayerDownloader.DEFAULT_DOWNLOAD_URL);
        assertEquals(5000, PlayerDownloader.DEFAULT_WAIT_MILLIS);
    }

    @Test
    void apkIsStoredInExternalCache()
    {
        PlayerDownloader MyTestClass = new PlayerDownloader(ActivityMocked, ProgressBarMocked, TextViewMocked);

        assertEquals(cache_dir + "/garlic-player.apk", MyTestClass.getApkPath());
    }

    @Test
    void downloadedPlayerIsHandedToInstallAppReceiver() throws IOException
    {
        File source                  = write(new File(tmp, "server.apk"), "player apk content");
        PlayerDownloader MyTestClass = createClass(source.toURI().toString());

        try (MockedStatic<Installer> installer = mockPackageName(PLAYER_PACKAGE);
             MockedConstruction<Intent> intents = mockIntents())
        {
            MyTestClass.onAvailable(mock(Network.class));

            assertEquals("player apk content", read(new File(MyTestClass.getApkPath())));
            installer.verify(() -> Installer.getAppNameFromPkgName(ActivityMocked, MyTestClass.getApkPath()), times(1));
            assertEquals(List.of(INSTALL_ACTION), sentActions);
            Intent sent = intents.constructed().get(0);
            verify(sent, times(1)).putExtra("apk_path", MyTestClass.getApkPath());
            verify(ActivityMocked, times(1)).sendBroadcast(sent);
            assertEquals(List.of("download", "install"), informationTexts);
            verify(ProgressBarMocked).setProgress(100, true);
        }
    }

    @Test
    void downloadedForeignPackageIsNotInstalled() throws IOException
    {
        File source                  = write(new File(tmp, "server.apk"), "other apk");
        PlayerDownloader MyTestClass = createClass(source.toURI().toString());

        try (MockedStatic<Installer> ignored = mockPackageName("com.example.other");
             MockedConstruction<Intent> ignoredIntents = mockIntents())
        {
            MyTestClass.onAvailable(mock(Network.class));

            assertEquals(0, sentActions.size());
            verify(ActivityMocked, never()).sendBroadcast(any(Intent.class));
            assertEquals(List.of("download"), informationTexts);
        }
    }

    @Test
    void oldDownloadIsReplaced() throws IOException
    {
        File source                  = write(new File(tmp, "server.apk"), "new");
        PlayerDownloader MyTestClass = createClass(source.toURI().toString());
        write(new File(MyTestClass.getApkPath()), "old download with more content");

        try (MockedStatic<Installer> ignored = mockPackageName(PLAYER_PACKAGE);
             MockedConstruction<Intent> ignoredIntents = mockIntents())
        {
            MyTestClass.onAvailable(mock(Network.class));

            assertEquals("new", read(new File(MyTestClass.getApkPath())));
        }
    }

    @Test
    void failedDownloadShowsErrorAndInstallsNothing()
    {
        String missing               = new File(tmp, "missing.apk").toURI().toString();
        PlayerDownloader MyTestClass = createClass(missing);

        try (MockedStatic<Installer> installer = mockPackageName(PLAYER_PACKAGE);
             MockedConstruction<Intent> ignoredIntents = mockIntents())
        {
            MyTestClass.onAvailable(mock(Network.class));

            assertEquals(2, informationTexts.size());
            assertEquals("download", informationTexts.get(0));
            assertTrue(informationTexts.get(1).contains("missing.apk"), informationTexts.get(1));
            installer.verifyNoInteractions();
            assertEquals(0, sentActions.size());
            assertFalse(new File(MyTestClass.getApkPath()).exists());
        }
    }

    @Test
    void lostNetworkShowsHint()
    {
        PlayerDownloader MyTestClass = createClass("file:///unused");

        MyTestClass.onLost(mock(Network.class));

        assertEquals(List.of("no network"), informationTexts);
        verify(ProgressBarMocked, never()).setProgress(anyInt(), anyBoolean());
    }

    private PlayerDownloader createClass(String url)
    {
        return new PlayerDownloader(ActivityMocked, ProgressBarMocked, TextViewMocked, url, 0);
    }

    private MockedStatic<Installer> mockPackageName(String package_name)
    {
        MockedStatic<Installer> installer = mockStatic(Installer.class);
        installer.when(() -> Installer.getAppNameFromPkgName(eq(ActivityMocked), anyString())).thenReturn(package_name);
        return installer;
    }

    private MockedConstruction<Intent> mockIntents()
    {
        return mockConstruction(Intent.class, (mock, context) -> {
            if (!context.arguments().isEmpty() && context.arguments().get(0) instanceof String)
                sentActions.add((String) context.arguments().get(0));
        });
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
}
