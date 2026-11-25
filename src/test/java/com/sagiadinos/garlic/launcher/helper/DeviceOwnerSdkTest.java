package com.sagiadinos.garlic.launcher.helper;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.provider.Settings;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * A device owner can set the brightness only from Android 9.
 */
@RunWith(RobolectricTestRunner.class)
public class DeviceOwnerSdkTest
{
    @Test
    @Config(sdk = {28, 35})
    public void brightnessFromAndroid9SwitchesToManualModeFirst()
    {
        DevicePolicyManager DpmMocked = mock(DevicePolicyManager.class);
        ComponentName admin           = new ComponentName("com.sagiadinos.garlic.launcher", "AdminReceiver");

        DeviceOwner.setScreenBrightness(DpmMocked, admin, 127);

        InOrder order = inOrder(DpmMocked);
        order.verify(DpmMocked).setSystemSetting(admin, Settings.System.SCREEN_BRIGHTNESS_MODE,
                String.valueOf(Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL));
        order.verify(DpmMocked).setSystemSetting(admin, Settings.System.SCREEN_BRIGHTNESS, "127");
    }

    @Test
    @Config(sdk = {25, 27})
    public void brightnessBeforeAndroid9DoesNothing()
    {
        DevicePolicyManager DpmMocked = mock(DevicePolicyManager.class);

        DeviceOwner.setScreenBrightness(DpmMocked, new ComponentName("a", "b"), 127);

        verifyNoInteractions(DpmMocked);
    }
}
