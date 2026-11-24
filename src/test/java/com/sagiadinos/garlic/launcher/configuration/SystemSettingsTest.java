package com.sagiadinos.garlic.launcher.configuration;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.media.AudioManager;

import com.sagiadinos.garlic.launcher.helper.DeviceOwner;
import com.sagiadinos.garlic.launcher.receiver.AdminReceiver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemSettingsTest
{
    Context ContextMocked;

    @BeforeEach
    void setUp()
    {
        ContextMocked = mock(Context.class);
    }

    @Test
    void configMasterVolumeConvertsPercentToStreamSteps()
    {
        AudioManager AudioMocked = mock(AudioManager.class);
        when(ContextMocked.getSystemService(Context.AUDIO_SERVICE)).thenReturn(AudioMocked);
        when(AudioMocked.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).thenReturn(15);

        new SystemSettings(ContextMocked).configMasterVolume(50);

        verify(AudioMocked, times(1)).setStreamVolume(AudioManager.STREAM_MUSIC, 7, 0);
    }

    @Test
    void configMasterVolumeFull()
    {
        AudioManager AudioMocked = mock(AudioManager.class);
        when(ContextMocked.getSystemService(Context.AUDIO_SERVICE)).thenReturn(AudioMocked);
        when(AudioMocked.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).thenReturn(15);

        new SystemSettings(ContextMocked).configMasterVolume(100);

        verify(AudioMocked, times(1)).setStreamVolume(AudioManager.STREAM_MUSIC, 15, 0);
    }

    @Test
    void configBrightnessConvertsPercentTo255()
    {
        DevicePolicyManager DpmMocked = mock(DevicePolicyManager.class);
        when(ContextMocked.getSystemService(Context.DEVICE_POLICY_SERVICE)).thenReturn(DpmMocked);

        try (MockedStatic<DeviceOwner> owner = mockStatic(DeviceOwner.class);
             MockedConstruction<ComponentName> component = mockConstruction(ComponentName.class,
                     (mock, context) -> assertEquals(AdminReceiver.class, context.arguments().get(1))))
        {
            new SystemSettings(ContextMocked).configBrightness(50);

            owner.verify(() -> DeviceOwner.setScreenBrightness(DpmMocked, component.constructed().get(0), 127), times(1));
        }
    }
}
