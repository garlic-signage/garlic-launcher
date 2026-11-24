package com.sagiadinos.garlic.launcher.deepstandby;

import android.content.Context;
import android.content.Intent;

import com.sagiadinos.garlic.launcher.configuration.MainConfiguration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChristiansenPlayerTest
{
    Context ContextMocked;
    List<String> actions;
    List<Map<String, Object>> extras;

    @BeforeEach
    void setUp()
    {
        ContextMocked = mock(Context.class);
        actions       = new ArrayList<>();
        extras        = new ArrayList<>();
    }

    @Test
    void wakeupSecondsHaveMinimumOf600()
    {
        ChristiansenPlayer MyTestClass = new ChristiansenPlayer(mock(MainConfiguration.class), ContextMocked);
        assertEquals(600, MyTestClass.wakeup_seconds);

        MyTestClass.setSecondsToWakup(0);
        assertEquals(600, MyTestClass.wakeup_seconds);

        MyTestClass.setSecondsToWakup(599);
        assertEquals(600, MyTestClass.wakeup_seconds);

        MyTestClass.setSecondsToWakup(3600);
        assertEquals(3600, MyTestClass.wakeup_seconds);
    }

    @Test
    void executeStandbySendsPowerOffAndPowerOnBroadcasts()
    {
        ChristiansenPlayer MyTestClass = new ChristiansenPlayer(mock(MainConfiguration.class), ContextMocked);
        MyTestClass.setSecondsToWakup(3600);

        try (MockedConstruction<Intent> intents = mockIntents())
        {
            Calendar before = Calendar.getInstance();
            MyTestClass.executeStandby();
            Calendar after = Calendar.getInstance();

            assertEquals(List.of("rk.android.turnofftime.action", "rk.android.turnontime.action"), actions);
            verify(ContextMocked, times(1)).sendBroadcast(intents.constructed().get(0));
            verify(ContextMocked, times(1)).sendBroadcast(intents.constructed().get(1));

            Map<String, Object> off = extras.get(0);
            assertEquals(true, off.get("enable"));
            assertTimeIn(off, "offHour", "offMin", "offWeek", before, after, 121);

            Map<String, Object> on = extras.get(1);
            assertEquals(true, on.get("turnonenable"));
            assertTimeIn(on, "turnonhour", "turnonmin", "onWeek", before, after, 3610);
        }
    }

    private void assertTimeIn(Map<String, Object> values, String hour, String minute, String week,
                              Calendar before, Calendar after, int offset_seconds)
    {
        Calendar from = (Calendar) before.clone();
        Calendar to   = (Calendar) after.clone();
        from.add(Calendar.SECOND, offset_seconds);
        to.add(Calendar.SECOND, offset_seconds);

        String actual = values.get(hour) + ":" + values.get(minute) + ":" + values.get(week);
        assertTrue(actual.equals(format(from)) || actual.equals(format(to)), actual);
    }

    private String format(Calendar c)
    {
        return c.get(Calendar.HOUR_OF_DAY) + ":" + c.get(Calendar.MINUTE) + ":" + c.get(Calendar.DAY_OF_WEEK);
    }

    private MockedConstruction<Intent> mockIntents()
    {
        return mockConstruction(Intent.class, (mock, context) -> {
            actions.add((String) context.arguments().get(0));
            Map<String, Object> values = new HashMap<>();
            extras.add(values);
            when(mock.putExtra(anyString(), anyString())).thenAnswer(i -> {
                values.put(i.getArgument(0), i.getArgument(1));
                return mock;
            });
            when(mock.putExtra(anyString(), anyBoolean())).thenAnswer(i -> {
                values.put(i.getArgument(0), i.getArgument(1));
                return mock;
            });
        });
    }
}
