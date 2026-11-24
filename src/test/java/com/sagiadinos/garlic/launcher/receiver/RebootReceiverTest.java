package com.sagiadinos.garlic.launcher.receiver;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import com.sagiadinos.garlic.launcher.helper.DeviceOwner;
import com.sagiadinos.garlic.launcher.helper.TaskExecutionReport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Obsolete receiver, kept for older players.
 * Contract: action "com.sagiadinos.garlic.launcher.receiver.RebootReceiver",
 * extra "task_id". Unlike CommandReceiver it reboots in debug builds too.
 */
class RebootReceiverTest
{
    private static final String ACTION = "com.sagiadinos.garlic.launcher.receiver.RebootReceiver";

    Context ContextMocked;

    @BeforeEach
    void setUp()
    {
        ContextMocked = mock(Context.class);
        when(ContextMocked.getPackageName()).thenReturn("com.sagiadinos.garlic.launcher");
    }

    @Test
    void onReceiveIntentNull()
    {
        new RebootReceiver().onReceive(ContextMocked, null);

        verifyNoInteractions(ContextMocked);
    }

    @Test
    void onReceiveActionNull()
    {
        Intent IntentMocked = mock(Intent.class);
        when(IntentMocked.getAction()).thenReturn(null);

        new RebootReceiver().onReceive(ContextMocked, IntentMocked);

        verifyNoInteractions(ContextMocked);
    }

    @Test
    void onReceiveWrongActionDoesNothing()
    {
        Intent IntentMocked = createIntent("some.other.action", "t1");

        try (MockedStatic<TaskExecutionReport> report = mockStatic(TaskExecutionReport.class);
             MockedStatic<DeviceOwner> owner = mockStatic(DeviceOwner.class))
        {
            new RebootReceiver().onReceive(ContextMocked, IntentMocked);

            report.verifyNoInteractions();
            owner.verifyNoInteractions();
        }
    }

    @Test
    void rebootReportsCompletedAndReboots()
    {
        Intent IntentMocked           = createIntent(ACTION, "task-9");
        DevicePolicyManager DpmMocked = mock(DevicePolicyManager.class);
        when(ContextMocked.getSystemService(Context.DEVICE_POLICY_SERVICE)).thenReturn(DpmMocked);

        try (MockedStatic<TaskExecutionReport> report = mockStatic(TaskExecutionReport.class);
             MockedStatic<DeviceOwner> owner = mockStatic(DeviceOwner.class);
             MockedConstruction<ComponentName> component = mockConstruction(ComponentName.class,
                     (mock, context) -> {
                         assertEquals(ContextMocked, context.arguments().get(0));
                         assertEquals(AdminReceiver.class, context.arguments().get(1));
                     }))
        {
            new RebootReceiver().onReceive(ContextMocked, IntentMocked);

            report.verify(() -> TaskExecutionReport.append("task-9", "completed"), times(1));
            owner.verify(() -> DeviceOwner.reboot(DpmMocked, component.constructed().get(0)), times(1));
        }
    }

    @Test
    void rebootWithoutTaskIdReportsEmptyTaskId()
    {
        Intent IntentMocked = createIntent(ACTION, null);

        try (MockedStatic<TaskExecutionReport> report = mockStatic(TaskExecutionReport.class);
             MockedStatic<DeviceOwner> ignoredOwner = mockStatic(DeviceOwner.class);
             MockedConstruction<ComponentName> ignored = mockConstruction(ComponentName.class))
        {
            new RebootReceiver().onReceive(ContextMocked, IntentMocked);

            report.verify(() -> TaskExecutionReport.append("", "completed"), times(1));
        }
    }

    private Intent createIntent(String action, String task_id)
    {
        Intent IntentMocked = mock(Intent.class);
        when(IntentMocked.getAction()).thenReturn(action);
        when(IntentMocked.getStringExtra("task_id")).thenReturn(task_id);
        return IntentMocked;
    }
}
