package com.sagiadinos.garlic.launcher.receiver;

import android.content.Context;
import android.content.Intent;

import com.sagiadinos.garlic.launcher.MainActivity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Contract: garlic-player sends
 * "com.sagiadinos.garlic.launcher.receiver.PlayerClosedReceiver"
 * when it is closed regularly.
 */
class PlayerClosedReceiverTest
{
    private static final String ACTION = "com.sagiadinos.garlic.launcher.receiver.PlayerClosedReceiver";

    Context ContextMocked;
    MainActivity MainActivityMocked;
    PlayerClosedReceiver MyTestClass;

    @BeforeEach
    void setUp()
    {
        ContextMocked      = mock(Context.class);
        MainActivityMocked = mock(MainActivity.class);
        MyTestClass        = new PlayerClosedReceiver();
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

        verifyNoInteractions(MainActivityMocked);
    }

    @Test
    void playerClosedStopsPlayer()
    {
        MyTestClass.onReceive(ContextMocked, createIntent(ACTION));

        verify(MainActivityMocked, times(1)).stopPlayer();
    }

    private Intent createIntent(String action)
    {
        Intent IntentMocked = mock(Intent.class);
        when(IntentMocked.getAction()).thenReturn(action);
        return IntentMocked;
    }
}
