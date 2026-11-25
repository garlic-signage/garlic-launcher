package com.sagiadinos.garlic.launcher.configuration;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;

import com.sagiadinos.garlic.launcher.helper.DeviceOwner;
import com.sagiadinos.garlic.launcher.helper.ShellExecute;
import com.sagiadinos.garlic.launcher.receiver.AdminReceiver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SystemSettingsTest
{
    private static final int NOUGAT_MR1 = Build.VERSION_CODES.N_MR1;
    private static final int PIE        = Build.VERSION_CODES.P;

    Context ContextMocked;
    MainConfiguration MainConfigurationMocked;
    ShellExecute ShellExecuteMocked;
    AudioManager AudioMocked;
    DevicePolicyManager DpmMocked;

    @BeforeEach
    void setUp()
    {
        ContextMocked           = mock(Context.class);
        MainConfigurationMocked = mock(MainConfiguration.class);
        ShellExecuteMocked      = mock(ShellExecute.class);
        AudioMocked             = mock(AudioManager.class);
        DpmMocked               = mock(DevicePolicyManager.class);
        when(ContextMocked.getSystemService(Context.AUDIO_SERVICE)).thenReturn(AudioMocked);
        when(ContextMocked.getSystemService(Context.DEVICE_POLICY_SERVICE)).thenReturn(DpmMocked);
        when(AudioMocked.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).thenReturn(15);
    }

    @Test
    void configMasterVolumeConvertsPercentToStreamSteps()
    {
        create(NOUGAT_MR1).configMasterVolume(50);

        verify(AudioMocked, times(1)).setStreamVolume(AudioManager.STREAM_MUSIC, 7, 0);
    }

    @Test
    void configMasterVolumeFull()
    {
        create(PIE).configMasterVolume(100);

        verify(AudioMocked, times(1)).setStreamVolume(AudioManager.STREAM_MUSIC, 15, 0);
    }

    @Test
    void configMasterVolumeIsLimitedTo0To100()
    {
        create(PIE).configMasterVolume(250);
        create(PIE).configMasterVolume(-20);

        verify(AudioMocked, times(1)).setStreamVolume(AudioManager.STREAM_MUSIC, 15, 0);
        verify(AudioMocked, times(1)).setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
    }

    @Test
    void configMasterVolumeWithoutAudioManager()
    {
        when(ContextMocked.getSystemService(Context.AUDIO_SERVICE)).thenReturn(null);

        try (MockedStatic<Log> ignored = mockStatic(Log.class))
        {
            create(PIE).configMasterVolume(50);
        }
    }

    @Test
    void configMasterVolumeRefusedBySystem()
    {
        doThrow(new SecurityException("do not disturb")).when(AudioMocked).setStreamVolume(anyInt(), anyInt(), anyInt());

        try (MockedStatic<Log> log = mockStatic(Log.class))
        {
            create(PIE).configMasterVolume(50);

            log.verify(() -> Log.w(anyString(), anyString()), times(1));
        }
    }

    @Test
    void configBrightnessAsDeviceOwnerFromAndroid9()
    {
        try (MockedStatic<DeviceOwner> owner = mockStatic(DeviceOwner.class);
             MockedConstruction<ComponentName> component = mockConstruction(ComponentName.class,
                     (mock, context) -> assertEquals(AdminReceiver.class, context.arguments().get(1))))
        {
            create(PIE).configBrightness(50);

            owner.verify(() -> DeviceOwner.setScreenBrightness(DpmMocked, component.constructed().get(0), 127), times(1));
            verifyNoInteractions(ShellExecuteMocked);
        }
    }

    @Test
    void configBrightnessIsLimitedTo0To100()
    {
        try (MockedStatic<DeviceOwner> owner = mockStatic(DeviceOwner.class);
             MockedConstruction<ComponentName> ignored = mockConstruction(ComponentName.class))
        {
            create(PIE).configBrightness(300);
            create(PIE).configBrightness(-1);

            owner.verify(() -> DeviceOwner.setScreenBrightness(any(), any(), org.mockito.ArgumentMatchers.eq(255)), times(1));
            owner.verify(() -> DeviceOwner.setScreenBrightness(any(), any(), org.mockito.ArgumentMatchers.eq(0)), times(1));
        }
    }

    @Test
    void configBrightnessNotDeviceOwner()
    {
        try (MockedStatic<DeviceOwner> owner = mockStatic(DeviceOwner.class);
             MockedConstruction<ComponentName> ignored = mockConstruction(ComponentName.class);
             MockedStatic<Log> log = mockStatic(Log.class))
        {
            owner.when(() -> DeviceOwner.setScreenBrightness(any(), any(), anyInt())).thenThrow(new SecurityException("no owner"));

            create(PIE).configBrightness(50);

            log.verify(() -> Log.w(anyString(), anyString()), times(1));
        }
    }

    @Test
    void configBrightnessBeforeAndroid9OnRootedDeviceUsesShell()
    {
        when(MainConfigurationMocked.isDeviceRooted()).thenReturn(true);
        when(ShellExecuteMocked.executeAsRoot(anyString())).thenReturn(true);

        try (MockedStatic<DeviceOwner> owner = mockStatic(DeviceOwner.class))
        {
            create(NOUGAT_MR1).configBrightness(50);

            verify(ShellExecuteMocked, times(1)).executeAsRoot(
                    "settings put system screen_brightness_mode 0\nsettings put system screen_brightness 127");
            owner.verifyNoInteractions();
        }
    }

    @Test
    void configBrightnessBeforeAndroid9ShellFails()
    {
        when(MainConfigurationMocked.isDeviceRooted()).thenReturn(true);
        when(ShellExecuteMocked.executeAsRoot(anyString())).thenReturn(false);
        when(ShellExecuteMocked.getErrorText()).thenReturn("su denied");

        try (MockedStatic<Log> log = mockStatic(Log.class))
        {
            create(NOUGAT_MR1).configBrightness(50);

            log.verify(() -> Log.w(anyString(), org.mockito.ArgumentMatchers.contains("su denied")), times(1));
        }
    }

    @Test
    void configBrightnessBeforeAndroid9NotRootedIsNotPossible()
    {
        when(MainConfigurationMocked.isDeviceRooted()).thenReturn(false);

        try (MockedStatic<DeviceOwner> owner = mockStatic(DeviceOwner.class);
             MockedStatic<Log> log = mockStatic(Log.class))
        {
            create(NOUGAT_MR1).configBrightness(50);

            verify(ShellExecuteMocked, never()).executeAsRoot(anyString());
            owner.verifyNoInteractions();
            log.verify(() -> Log.w(anyString(), anyString()), times(1));
        }
    }

    private SystemSettings create(int sdk_int)
    {
        return new SystemSettings(ContextMocked, MainConfigurationMocked, ShellExecuteMocked, sdk_int);
    }
}
