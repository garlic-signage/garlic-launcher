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

/**
 * Applies volume and brightness, given in percent.
 */
public class SystemSettings
{
    private static final String TAG = "SystemSettings";

    Context           MyContext;
    MainConfiguration MyMainConfiguration;
    ShellExecute      MyShellExecute;
    private final int sdk_int;

    public SystemSettings(Context myContext, MainConfiguration myMainConfiguration, ShellExecute myShellExecute)
    {
        this(myContext, myMainConfiguration, myShellExecute, Build.VERSION.SDK_INT);
    }

    /**
     * For tests: Build.VERSION.SDK_INT is 0 in plain unit tests.
     */
    SystemSettings(Context myContext, MainConfiguration myMainConfiguration, ShellExecute myShellExecute, int sdk_int)
    {
        MyContext           = myContext;
        MyMainConfiguration = myMainConfiguration;
        MyShellExecute      = myShellExecute;
        this.sdk_int        = sdk_int;
    }

    public void configMasterVolume(int percent)
    {
        AudioManager audio = (AudioManager) MyContext.getSystemService(Context.AUDIO_SERVICE);
        if (audio == null)
        {
            Log.w(TAG, "No AudioManager, volume not set");
            return;
        }

        int maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int volume = (int) (maxVolume * convertPercent(percent));
        try
        {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0);
        }
        catch (SecurityException e)
        {
            Log.w(TAG, "Volume not set: " + e.getMessage());
        }
    }

    /**
     * Android 9 and newer: as device owner.
     * Before Android 9 there is no API for a device owner. Rooted devices use
     * the shell; on other devices the brightness cannot be set.
     */
    public void configBrightness(int percent)
    {
        int brightness = (int) (255 * convertPercent(percent));
        if (sdk_int >= Build.VERSION_CODES.P)
        {
            try
            {
                DeviceOwner.setScreenBrightness(
                        (DevicePolicyManager) MyContext.getSystemService(Context.DEVICE_POLICY_SERVICE),
                        new ComponentName(MyContext, AdminReceiver.class),
                        brightness);
            }
            catch (SecurityException e)
            {
                Log.w(TAG, "Brightness not set: " + e.getMessage());
            }
            return;
        }

        if (MyMainConfiguration.isDeviceRooted())
        {
            // 0 = manual mode, otherwise the automatic brightness overrides the value
            if (!MyShellExecute.executeAsRoot("settings put system screen_brightness_mode 0\n" +
                    "settings put system screen_brightness " + brightness))
            {
                Log.w(TAG, "Brightness not set via shell: " + MyShellExecute.getErrorText());
            }
            return;
        }

        Log.w(TAG, "Brightness can be set only from Android 9 or on rooted devices");
    }

    /**
     * Values outside 0 - 100 are limited.
     */
    private float convertPercent(int value)
    {
        return (float) Math.max(0, Math.min(100, value)) / 100f;
    }

}
