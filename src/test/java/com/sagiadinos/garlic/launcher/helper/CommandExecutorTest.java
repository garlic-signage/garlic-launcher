package com.sagiadinos.garlic.launcher.helper;

import android.content.ComponentName;
import android.content.Context;

import com.sagiadinos.garlic.launcher.testutil.FakeSharedPreferences;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Dispatch of the commands. The single commands are tested in CommandReceiverTest,
 * which uses the CommandExecutor too.
 */
class CommandExecutorTest
{
    Context ContextMocked;

    @BeforeEach
    void setUp()
    {
        ContextMocked = mock(Context.class);
        FakeSharedPreferences.attachTo(ContextMocked);
    }

    @Test
    void nullCommandIsRejected()
    {
        assertFalse(new CommandExecutor(ContextMocked).execute(null, null, null));
        verify(ContextMocked, never()).getSystemService(anyString());
    }

    @Test
    void unknownCommandIsRejected()
    {
        assertFalse(new CommandExecutor(ContextMocked).execute("format_disk", "t1", null));
        verify(ContextMocked, never()).getSystemService(anyString());
    }

    @Test
    void rebootWithNullTaskIdReportsEmptyTaskId()
    {
        try (MockedStatic<TaskExecutionReport> report = mockStatic(TaskExecutionReport.class);
             MockedStatic<DeviceOwner> ignoredOwner = mockStatic(DeviceOwner.class);
             MockedConstruction<ComponentName> ignored = mockConstruction(ComponentName.class))
        {
            assertTrue(new CommandExecutor(ContextMocked).execute("reboot", null, null));

            report.verify(() -> TaskExecutionReport.append("", "completed"), times(1));
        }
    }
}
