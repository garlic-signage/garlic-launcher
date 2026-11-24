package com.sagiadinos.garlic.launcher.receiver;

import android.content.Context;
import android.content.Intent;

import com.sagiadinos.garlic.launcher.testutil.FakeSharedPreferences;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BootCompletedReceiverTest
{
    Context ContextMocked;
    FakeSharedPreferences Prefs;

    @BeforeEach
    void setUp()
    {
        ContextMocked = mock(Context.class);
        Prefs         = FakeSharedPreferences.attachTo(ContextMocked);
    }

    @Test
    void onReceiveIntentNull()
    {
        new BootCompletedReceiver().onReceive(ContextMocked, null);

        verifyNoInteractions(ContextMocked);
    }

    @Test
    void onReceiveActionNull()
    {
        Intent IntentMocked = mock(Intent.class);
        when(IntentMocked.getAction()).thenReturn(null);

        new BootCompletedReceiver().onReceive(ContextMocked, IntentMocked);

        verifyNoInteractions(ContextMocked);
    }

    @Test
    void bootCompletedSetsJustBooted()
    {
        Intent IntentMocked = mock(Intent.class);
        when(IntentMocked.getAction()).thenReturn(Intent.ACTION_BOOT_COMPLETED);

        new BootCompletedReceiver().onReceive(ContextMocked, IntentMocked);

        assertTrue(Prefs.getBoolean("just_booted", false));
    }

    @Test
    void otherActionDoesNotSetJustBooted()
    {
        Intent IntentMocked = mock(Intent.class);
        when(IntentMocked.getAction()).thenReturn("some.other.action");

        new BootCompletedReceiver().onReceive(ContextMocked, IntentMocked);

        assertFalse(Prefs.contains("just_booted"));
    }
}
