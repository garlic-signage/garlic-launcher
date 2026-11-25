package com.sagiadinos.garlic.launcher.receiver;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

import com.sagiadinos.garlic.launcher.BuildConfig;
import com.sagiadinos.garlic.launcher.deepstandby.AbstractBaseStandby;
import com.sagiadinos.garlic.launcher.deepstandby.StandbyFactory;
import com.sagiadinos.garlic.launcher.helper.DeviceOwner;
import com.sagiadinos.garlic.launcher.helper.ShellExecute;
import com.sagiadinos.garlic.launcher.helper.TaskExecutionReport;
import com.sagiadinos.garlic.launcher.testutil.FakeSharedPreferences;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Contract of CommandReceiver as used by garlic-player:
 * action "com.sagiadinos.garlic.launcher.receiver.CommandReceiver",
 * extras "command" (reboot|screen_off|screen_on|deep_standby),
 * "task_id" and "seconds_to_wakeup".
 */
class CommandReceiverTest
{
    private static final String ACTION = "com.sagiadinos.garlic.launcher.receiver.CommandReceiver";

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
        new CommandReceiver().onReceive(ContextMocked, null);

        verifyNoInteractions(ContextMocked);
    }

    @Test
    void onReceiveActionNull()
    {
        Intent IntentMocked = mock(Intent.class);
        when(IntentMocked.getAction()).thenReturn(null);

        new CommandReceiver().onReceive(ContextMocked, IntentMocked);

        verifyNoInteractions(ContextMocked);
        verify(IntentMocked, never()).getStringExtra(anyString());
    }

    @Test
    void onReceiveWithoutCommandDoesNothing()
    {
        Intent IntentMocked = createIntent(ACTION, null);

        try (MockedStatic<TaskExecutionReport> report = mockStatic(TaskExecutionReport.class))
        {
            new CommandReceiver().onReceive(ContextMocked, IntentMocked);

            report.verifyNoInteractions();
            verify(ContextMocked, never()).getSystemService(anyString());
        }
    }

    @Test
    void onReceiveUnknownCommandDoesNothing()
    {
        Intent IntentMocked = createIntent(ACTION, "self_destruct");

        try (MockedStatic<TaskExecutionReport> report = mockStatic(TaskExecutionReport.class))
        {
            new CommandReceiver().onReceive(ContextMocked, IntentMocked);

            report.verifyNoInteractions();
            verify(ContextMocked, never()).getSystemService(anyString());
        }
    }

    @Test
    void rebootReportsTaskCompleted()
    {
        Intent IntentMocked = createIntent(ACTION, "reboot");
        when(IntentMocked.getStringExtra("task_id")).thenReturn("task-4711");

        try (MockedStatic<TaskExecutionReport> report = mockStatic(TaskExecutionReport.class);
             MockedStatic<DeviceOwner> owner = mockStatic(DeviceOwner.class);
             MockedConstruction<ComponentName> ignored = mockConstruction(ComponentName.class))
        {
            new CommandReceiver().onReceive(ContextMocked, IntentMocked);

            report.verify(() -> TaskExecutionReport.append("task-4711", "completed"), times(1));
            verifyRebootOnlyInRelease(owner);
        }
    }

    @Test
    void rebootWithoutTaskIdReportsEmptyTaskId()
    {
        Intent IntentMocked = createIntent(ACTION, "reboot");
        when(IntentMocked.getStringExtra("task_id")).thenReturn(null);

        try (MockedStatic<TaskExecutionReport> report = mockStatic(TaskExecutionReport.class);
             MockedStatic<DeviceOwner> owner = mockStatic(DeviceOwner.class);
             MockedConstruction<ComponentName> ignored = mockConstruction(ComponentName.class))
        {
            new CommandReceiver().onReceive(ContextMocked, IntentMocked);

            report.verify(() -> TaskExecutionReport.append("", "completed"), times(1));
            verifyRebootOnlyInRelease(owner);
        }
    }

    /**
     * The action is only checked for null, not for its value.
     * Older players rely on the command extra only.
     */
    @Test
    void commandIsExecutedRegardlessOfActionName()
    {
        Intent IntentMocked = createIntent("any.other.action", "reboot");
        when(IntentMocked.getStringExtra("task_id")).thenReturn("t1");

        try (MockedStatic<TaskExecutionReport> report = mockStatic(TaskExecutionReport.class);
             MockedStatic<DeviceOwner> ignoredOwner = mockStatic(DeviceOwner.class);
             MockedConstruction<ComponentName> ignored = mockConstruction(ComponentName.class))
        {
            new CommandReceiver().onReceive(ContextMocked, IntentMocked);

            report.verify(() -> TaskExecutionReport.append("t1", "completed"), times(1));
        }
    }

    @Test
    void screenOffAcquiresWakeLockAndLocksScreen()
    {
        Intent IntentMocked                 = createIntent(ACTION, "screen_off");
        PowerManager PowerManagerMocked     = mock(PowerManager.class);
        PowerManager.WakeLock WakeLockMocked = mock(PowerManager.WakeLock.class);
        DevicePolicyManager DpmMocked       = mock(DevicePolicyManager.class);

        when(ContextMocked.getSystemService(Context.POWER_SERVICE)).thenReturn(PowerManagerMocked);
        when(ContextMocked.getSystemService(Context.DEVICE_POLICY_SERVICE)).thenReturn(DpmMocked);
        when(PowerManagerMocked.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Launcher:WakeLockTag")).thenReturn(WakeLockMocked);

        new CommandReceiver().onReceive(ContextMocked, IntentMocked);

        verify(WakeLockMocked, times(1)).acquire(2 * 24 * 60 * 60 * 1000L);
        verify(DpmMocked, times(1)).lockNow();
    }

    @Test
    void screenOnRootedSendsWakeupKeyEvent()
    {
        Prefs.edit().putBoolean("is_device_rooted", true).commit();
        Intent IntentMocked = createIntent(ACTION, "screen_on");

        try (MockedConstruction<ShellExecute> shell = mockConstruction(ShellExecute.class))
        {
            new CommandReceiver().onReceive(ContextMocked, IntentMocked);

            assertEquals(1, shell.constructed().size());
            verify(shell.constructed().get(0), times(1)).executeAsRoot("input keyevent KEYCODE_WAKEUP");
            verify(ContextMocked, never()).getSystemService(Context.ALARM_SERVICE);
        }
    }

    @Test
    void screenOnNotRootedSchedulesInForegroundAlarm()
    {
        Intent IntentMocked         = createIntent(ACTION, "screen_on");
        AlarmManager AlarmMocked    = mock(AlarmManager.class);
        PendingIntent PendingMocked = mock(PendingIntent.class);
        when(ContextMocked.getSystemService(Context.ALARM_SERVICE)).thenReturn(AlarmMocked);

        List<String> actions = new ArrayList<>();
        try (MockedConstruction<Intent> intents = mockConstruction(Intent.class,
                     (mock, context) -> actions.add((String) context.arguments().get(0)));
             MockedStatic<PendingIntent> pending = mockStatic(PendingIntent.class);
             MockedConstruction<ShellExecute> shell = mockConstruction(ShellExecute.class))
        {
            pending.when(() -> PendingIntent.getBroadcast(eq(ContextMocked), eq(0), any(Intent.class), anyInt()))
                   .thenReturn(PendingMocked);

            long before = System.currentTimeMillis();
            new CommandReceiver().onReceive(ContextMocked, IntentMocked);

            assertEquals(List.of("com.sagiadinos.garlic.launcher.receiver.InForegroundReceiver"), actions);
            verify(intents.constructed().get(0), times(1)).addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            pending.verify(() -> PendingIntent.getBroadcast(ContextMocked, 0, intents.constructed().get(0),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT), times(1));
            verify(AlarmMocked, times(1)).setExactAndAllowWhileIdle(eq(AlarmManager.RTC_WAKEUP),
                    org.mockito.ArgumentMatchers.longThat(t -> t >= before + 10000 && t <= System.currentTimeMillis() + 10000),
                    eq(PendingMocked));
            assertEquals(0, shell.constructed().size());
        }
    }

    @Test
    void deepStandbyPassesSecondsToStandbyModel()
    {
        Intent IntentMocked = createIntent(ACTION, "deep_standby");
        when(IntentMocked.getStringExtra("seconds_to_wakeup")).thenReturn("3600");
        AbstractBaseStandby StandbyMocked = mock(AbstractBaseStandby.class);

        try (MockedConstruction<StandbyFactory> ignored = mockConstruction(StandbyFactory.class,
                (mock, context) -> when(mock.determinePlayerModel()).thenReturn(StandbyMocked)))
        {
            new CommandReceiver().onReceive(ContextMocked, IntentMocked);

            verify(StandbyMocked, times(1)).setSecondsToWakup(3600);
            verify(StandbyMocked, times(1)).executeStandby();
        }
    }

    @Test
    void deepStandbyWithoutSecondsUsesZero()
    {
        Intent IntentMocked = createIntent(ACTION, "deep_standby");
        when(IntentMocked.getStringExtra("seconds_to_wakeup")).thenReturn(null);
        AbstractBaseStandby StandbyMocked = mock(AbstractBaseStandby.class);

        try (MockedConstruction<StandbyFactory> ignored = mockConstruction(StandbyFactory.class,
                (mock, context) -> when(mock.determinePlayerModel()).thenReturn(StandbyMocked)))
        {
            new CommandReceiver().onReceive(ContextMocked, IntentMocked);

            verify(StandbyMocked, times(1)).setSecondsToWakup(0);
            verify(StandbyMocked, times(1)).executeStandby();
        }
    }

    @Test
    void deepStandbyOnUnsupportedDeviceDoesNothing()
    {
        Intent IntentMocked = createIntent(ACTION, "deep_standby");
        when(IntentMocked.getStringExtra("seconds_to_wakeup")).thenReturn("3600");

        try (MockedConstruction<StandbyFactory> factory = mockConstruction(StandbyFactory.class,
                (mock, context) -> when(mock.determinePlayerModel()).thenReturn(null)))
        {
            new CommandReceiver().onReceive(ContextMocked, IntentMocked);

            verify(factory.constructed().get(0), times(1)).determinePlayerModel();
        }
    }

    /**
     * A not numeric value is handled like a missing one.
     */
    @Test
    void deepStandbyWithNonNumericSecondsUsesZero()
    {
        Intent IntentMocked = createIntent(ACTION, "deep_standby");
        when(IntentMocked.getStringExtra("seconds_to_wakeup")).thenReturn("one hour");
        AbstractBaseStandby StandbyMocked = mock(AbstractBaseStandby.class);

        try (MockedConstruction<StandbyFactory> ignored = mockConstruction(StandbyFactory.class,
                (mock, context) -> when(mock.determinePlayerModel()).thenReturn(StandbyMocked));
             MockedStatic<Log> log = mockStatic(Log.class))
        {
            new CommandReceiver().onReceive(ContextMocked, IntentMocked);

            verify(StandbyMocked, times(1)).setSecondsToWakup(0);
            verify(StandbyMocked, times(1)).executeStandby();
            log.verify(() -> Log.w(anyString(), anyString()), times(1));
        }
    }

    @Test
    void deepStandbySecondsWithBlanks()
    {
        Intent IntentMocked = createIntent(ACTION, "deep_standby");
        when(IntentMocked.getStringExtra("seconds_to_wakeup")).thenReturn(" 1200 ");
        AbstractBaseStandby StandbyMocked = mock(AbstractBaseStandby.class);

        try (MockedConstruction<StandbyFactory> ignored = mockConstruction(StandbyFactory.class,
                (mock, context) -> when(mock.determinePlayerModel()).thenReturn(StandbyMocked)))
        {
            new CommandReceiver().onReceive(ContextMocked, IntentMocked);

            verify(StandbyMocked, times(1)).setSecondsToWakup(1200);
        }
    }

    private Intent createIntent(String action, String command)
    {
        Intent IntentMocked = mock(Intent.class);
        when(IntentMocked.getAction()).thenReturn(action);
        when(IntentMocked.getStringExtra("command")).thenReturn(command);
        return IntentMocked;
    }

    private void verifyRebootOnlyInRelease(MockedStatic<DeviceOwner> owner)
    {
        if (BuildConfig.DEBUG)
            owner.verify(() -> DeviceOwner.reboot(any(), any()), never());
        else
            owner.verify(() -> DeviceOwner.reboot(any(), any()), times(1));
    }
}
