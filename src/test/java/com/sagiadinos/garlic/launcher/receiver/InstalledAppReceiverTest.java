package com.sagiadinos.garlic.launcher.receiver;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.sagiadinos.garlic.launcher.testutil.FakeSharedPreferences;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tracks whether garlic-player is installed and optionally reboots
 * after any package change. An update fires REMOVED, ADDED (both with
 * EXTRA_REPLACING) and REPLACED; only REPLACED must be handled then.
 */
class InstalledAppReceiverTest
{
    private static final String PLAYER_URI     = "package:com.sagiadinos.garlic.player";
    private static final String OTHER_URI      = "package:com.example.other";
    private static final String COMMAND_ACTION = "com.sagiadinos.garlic.launcher.receiver.CommandReceiver";

    Context ContextMocked;
    FakeSharedPreferences Prefs;
    List<String> sentActions;

    @BeforeEach
    void setUp()
    {
        ContextMocked = mock(Context.class);
        Prefs         = FakeSharedPreferences.attachTo(ContextMocked);
        sentActions   = new ArrayList<>();
    }

    @Test
    void onReceiveIntentNull()
    {
        new InstalledAppReceiver().onReceive(ContextMocked, null);

        verifyNoInteractions(ContextMocked);
    }

    @Test
    void onReceiveActionNull()
    {
        Intent IntentMocked = mock(Intent.class);
        when(IntentMocked.getAction()).thenReturn(null);

        new InstalledAppReceiver().onReceive(ContextMocked, IntentMocked);

        verifyNoInteractions(ContextMocked);
    }

    @Test
    void playerAddedMarksPlayerInstalled()
    {
        Intent IntentMocked = createIntent(Intent.ACTION_PACKAGE_ADDED, PLAYER_URI, false);

        try (MockedConstruction<Intent> ignored = mockIntents())
        {
            new InstalledAppReceiver().onReceive(ContextMocked, IntentMocked);

            assertTrue(Prefs.getBoolean("is_player_installed", false));
            assertEquals(0, sentActions.size());
        }
    }

    @Test
    void playerRemovedMarksPlayerNotInstalled()
    {
        Prefs.edit().putBoolean("is_player_installed", true).commit();
        Intent IntentMocked = createIntent(Intent.ACTION_PACKAGE_REMOVED, PLAYER_URI, false);

        try (MockedConstruction<Intent> ignored = mockIntents())
        {
            new InstalledAppReceiver().onReceive(ContextMocked, IntentMocked);

            assertFalse(Prefs.getBoolean("is_player_installed", true));
        }
    }

    @Test
    void playerReplacedMarksPlayerInstalled()
    {
        Intent IntentMocked = createIntent(Intent.ACTION_PACKAGE_REPLACED, PLAYER_URI, true);

        try (MockedConstruction<Intent> ignored = mockIntents())
        {
            new InstalledAppReceiver().onReceive(ContextMocked, IntentMocked);

            assertTrue(Prefs.getBoolean("is_player_installed", false));
        }
    }

    @Test
    void removedDuringUpdateIsIgnored()
    {
        Prefs.edit().putBoolean("is_player_installed", true)
                    .putBoolean("reboot_after_install", true).commit();
        Intent IntentMocked = createIntent(Intent.ACTION_PACKAGE_REMOVED, PLAYER_URI, true);

        try (MockedConstruction<Intent> ignored = mockIntents())
        {
            new InstalledAppReceiver().onReceive(ContextMocked, IntentMocked);

            assertTrue(Prefs.getBoolean("is_player_installed", false));
            assertEquals(0, sentActions.size());
        }
    }

    @Test
    void addedDuringUpdateIsIgnored()
    {
        Prefs.edit().putBoolean("reboot_after_install", true).commit();
        Intent IntentMocked = createIntent(Intent.ACTION_PACKAGE_ADDED, PLAYER_URI, true);

        try (MockedConstruction<Intent> ignored = mockIntents())
        {
            new InstalledAppReceiver().onReceive(ContextMocked, IntentMocked);

            assertFalse(Prefs.contains("is_player_installed"));
            assertEquals(0, sentActions.size());
        }
    }

    @Test
    void otherPackageDoesNotChangePlayerState()
    {
        Intent IntentMocked = createIntent(Intent.ACTION_PACKAGE_ADDED, OTHER_URI, false);

        try (MockedConstruction<Intent> ignored = mockIntents())
        {
            new InstalledAppReceiver().onReceive(ContextMocked, IntentMocked);

            assertFalse(Prefs.contains("is_player_installed"));
        }
    }

    @Test
    void noRebootWhenRebootAfterInstallIsOff()
    {
        Intent IntentMocked = createIntent(Intent.ACTION_PACKAGE_ADDED, OTHER_URI, false);

        try (MockedConstruction<Intent> ignored = mockIntents())
        {
            new InstalledAppReceiver().onReceive(ContextMocked, IntentMocked);

            verify(ContextMocked, never()).sendBroadcast(any(Intent.class));
        }
    }

    @Test
    void rebootAfterAddedPackage()
    {
        assertRebootFor(Intent.ACTION_PACKAGE_ADDED, OTHER_URI, false);
    }

    @Test
    void rebootAfterRemovedPackage()
    {
        assertRebootFor(Intent.ACTION_PACKAGE_REMOVED, OTHER_URI, false);
    }

    @Test
    void rebootAfterReplacedPackage()
    {
        assertRebootFor(Intent.ACTION_PACKAGE_REPLACED, PLAYER_URI, true);
    }

    @Test
    void noRebootForOtherActions()
    {
        Prefs.edit().putBoolean("reboot_after_install", true).commit();
        Intent IntentMocked = createIntent("android.intent.action.PACKAGE_CHANGED", OTHER_URI, false);

        try (MockedConstruction<Intent> ignored = mockIntents())
        {
            new InstalledAppReceiver().onReceive(ContextMocked, IntentMocked);

            assertEquals(0, sentActions.size());
        }
    }

    /**
     * Current behaviour: package broadcasts always carry extras on Android.
     * Without extras the receiver crashes.
     */
    @Test
    void missingExtrasThrows()
    {
        Intent IntentMocked = createIntent(Intent.ACTION_PACKAGE_ADDED, PLAYER_URI, false);
        when(IntentMocked.getExtras()).thenReturn(null);

        InstalledAppReceiver MyTestClass = new InstalledAppReceiver();
        assertThrows(NullPointerException.class, () -> MyTestClass.onReceive(ContextMocked, IntentMocked));
    }

    private void assertRebootFor(String action, String uri, boolean replacing)
    {
        Prefs.edit().putBoolean("reboot_after_install", true).commit();
        Intent IntentMocked = createIntent(action, uri, replacing);

        try (MockedConstruction<Intent> intents = mockIntents())
        {
            new InstalledAppReceiver().onReceive(ContextMocked, IntentMocked);

            assertEquals(List.of(COMMAND_ACTION), sentActions);
            Intent sent = intents.constructed().get(0);
            verify(sent, times(1)).putExtra("command", "reboot");
            verify(sent, times(1)).putExtra("task_id", "app_installed");
            verify(ContextMocked, times(1)).sendBroadcast(sent);
        }
    }

    private Intent createIntent(String action, String uri, boolean replacing)
    {
        Intent IntentMocked = mock(Intent.class);
        Uri UriMocked       = mock(Uri.class);
        Bundle BundleMocked = mock(Bundle.class);
        when(UriMocked.toString()).thenReturn(uri);
        when(BundleMocked.containsKey(Intent.EXTRA_REPLACING)).thenReturn(replacing);
        when(IntentMocked.getAction()).thenReturn(action);
        when(IntentMocked.getData()).thenReturn(UriMocked);
        when(IntentMocked.getExtras()).thenReturn(BundleMocked);
        return IntentMocked;
    }

    private MockedConstruction<Intent> mockIntents()
    {
        return mockConstruction(Intent.class, (mock, context) -> {
            if (!context.arguments().isEmpty() && context.arguments().get(0) instanceof String)
                sentActions.add((String) context.arguments().get(0));
        });
    }
}
