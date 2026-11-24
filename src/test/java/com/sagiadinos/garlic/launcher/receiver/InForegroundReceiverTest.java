package com.sagiadinos.garlic.launcher.receiver;

import android.content.Context;
import android.content.Intent;

import com.sagiadinos.garlic.launcher.MainActivity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Contract: the garlic-player watchdog sends
 * "com.sagiadinos.garlic.launcher.receiver.InForegroundReceiver"
 * when a launcher activity is in the foreground.
 */
class InForegroundReceiverTest
{
    private static final String ACTION           = "com.sagiadinos.garlic.launcher.receiver.InForegroundReceiver";
    private static final String LAUNCHER_PACKAGE = "com.sagiadinos.garlic.launcher";

    Context ContextMocked;
    MainActivity MainActivityMocked;
    InForegroundReceiver MyTestClass;

    @BeforeEach
    void setUp()
    {
        ContextMocked      = mock(Context.class);
        MainActivityMocked = mock(MainActivity.class);
        when(ContextMocked.getPackageName()).thenReturn(LAUNCHER_PACKAGE);
        when(MainActivityMocked.getPackageName()).thenReturn(LAUNCHER_PACKAGE);
        MyTestClass = new InForegroundReceiver();
        MyTestClass.setMyActivity(MainActivityMocked);
    }

    @Test
    void onReceiveIntentNull()
    {
        MyTestClass.onReceive(ContextMocked, null);

        verifyNoInteractions(MainActivityMocked);
    }

    @Test
    void onReceiveActionNull()
    {
        MyTestClass.onReceive(ContextMocked, createIntent(null));

        verifyNoInteractions(MainActivityMocked);
    }

    @Test
    void onReceiveWrongActionDoesNothing()
    {
        MyTestClass.onReceive(ContextMocked, createIntent("some.other.action"));

        verify(MainActivityMocked, never()).isPlayerPlaying();
        verify(MainActivityMocked, never()).startGarlicPlayerInstantly(any());
    }

    @Test
    void onReceiveInForeignPackageDoesNothing()
    {
        when(ContextMocked.getPackageName()).thenReturn("com.example.other");

        MyTestClass.onReceive(ContextMocked, createIntent(ACTION));

        verify(MainActivityMocked, never()).isPlayerPlaying();
        verify(MainActivityMocked, never()).startGarlicPlayerInstantly(any());
    }

    @Test
    void playerPlayingRestartsPlayer()
    {
        when(MainActivityMocked.isPlayerPlaying()).thenReturn(true);

        MyTestClass.onReceive(ContextMocked, createIntent(ACTION));

        verify(MainActivityMocked, times(1)).startGarlicPlayerInstantly(isNull());
    }

    @Test
    void playerNotPlayingStaysInLauncher()
    {
        when(MainActivityMocked.isPlayerPlaying()).thenReturn(false);

        MyTestClass.onReceive(ContextMocked, createIntent(ACTION));

        verify(MainActivityMocked, never()).startGarlicPlayerInstantly(any());
    }

    private Intent createIntent(String action)
    {
        Intent IntentMocked = mock(Intent.class);
        when(IntentMocked.getAction()).thenReturn(action);
        return IntentMocked;
    }
}
