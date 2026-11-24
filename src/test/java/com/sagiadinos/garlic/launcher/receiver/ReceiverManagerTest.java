package com.sagiadinos.garlic.launcher.receiver;

import android.content.BroadcastReceiver;
import android.content.IntentFilter;

import com.sagiadinos.garlic.launcher.MainActivity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The two receivers for garlic-player are registered at runtime
 * without permission, so every player version can reach them.
 */
class ReceiverManagerTest
{
    MainActivity MainActivityMocked;
    List<String> filterActions;

    @BeforeEach
    void setUp()
    {
        MainActivityMocked = mock(MainActivity.class);
        filterActions      = new ArrayList<>();
    }

    @Test
    void registerAllReceiver()
    {
        try (MockedConstruction<IntentFilter> filters = mockFilters())
        {
            new ReceiverManager(MainActivityMocked).registerAllReceiver();

            ArgumentCaptor<BroadcastReceiver> receivers = ArgumentCaptor.forClass(BroadcastReceiver.class);
            ArgumentCaptor<IntentFilter> registered     = ArgumentCaptor.forClass(IntentFilter.class);
            verify(MainActivityMocked, times(2)).registerReceiver(receivers.capture(), registered.capture());

            assertEquals(List.of(
                    "com.sagiadinos.garlic.launcher.receiver.InForegroundReceiver",
                    "com.sagiadinos.garlic.launcher.receiver.PlayerClosedReceiver"
            ), filterActions);
            assertEquals(filters.constructed(), registered.getAllValues());
            assertInstanceOf(InForegroundReceiver.class, receivers.getAllValues().get(0));
            assertInstanceOf(PlayerClosedReceiver.class, receivers.getAllValues().get(1));
            assertEquals(MainActivityMocked, ((InForegroundReceiver) receivers.getAllValues().get(0)).MyActivity);
            assertEquals(MainActivityMocked, ((PlayerClosedReceiver) receivers.getAllValues().get(1)).MyActivity);
        }
    }

    @Test
    void unregisterWithoutRegisterDoesNothing()
    {
        new ReceiverManager(MainActivityMocked).unregisterAllReceiver();

        verify(MainActivityMocked, never()).unregisterReceiver(any());
    }

    @Test
    void unregisterAllReceiver()
    {
        try (MockedConstruction<IntentFilter> ignored = mockFilters())
        {
            ReceiverManager MyTestClass = new ReceiverManager(MainActivityMocked);
            MyTestClass.registerAllReceiver();

            ArgumentCaptor<BroadcastReceiver> receivers = ArgumentCaptor.forClass(BroadcastReceiver.class);
            verify(MainActivityMocked, times(2)).registerReceiver(receivers.capture(), any(IntentFilter.class));

            MyTestClass.unregisterAllReceiver();

            verify(MainActivityMocked, times(1)).unregisterReceiver(receivers.getAllValues().get(0));
            verify(MainActivityMocked, times(1)).unregisterReceiver(receivers.getAllValues().get(1));
        }
    }

    private MockedConstruction<IntentFilter> mockFilters()
    {
        return mockConstruction(IntentFilter.class,
                (mock, context) -> filterActions.add((String) context.arguments().get(0)));
    }
}
