package com.sagiadinos.garlic.launcher.helper;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

import com.sagiadinos.garlic.launcher.BuildConfig;
import com.sagiadinos.garlic.launcher.configuration.MainConfiguration;
import com.sagiadinos.garlic.launcher.configuration.SharedPreferencesModel;
import com.sagiadinos.garlic.launcher.deepstandby.AbstractBaseStandby;
import com.sagiadinos.garlic.launcher.deepstandby.StandbyFactory;
import com.sagiadinos.garlic.launcher.receiver.AdminReceiver;

/**
 * Commands of the player: reboot, screen_off, screen_on, deep_standby.
 * Used by the SettingsProvider (call "command", signature checked) and by the
 * CommandReceiver (broadcast, legacy player, removed with launcher 2.0).
 */
public class CommandExecutor
{
    private final Context MyContext;
    private final MainConfiguration MyMainConfiguration;

    public CommandExecutor(Context context)
    {
        MyContext           = context;
        MyMainConfiguration = new MainConfiguration(new SharedPreferencesModel(MyContext));
    }

    /**
     * @return false for an unknown command
     */
    public boolean execute(String command, String task_id, String seconds_to_wakeup)
    {
        if (command == null)
            return false;

        switch (command)
        {
            case "reboot": reboot(task_id);
                return true;
            case "screen_off": setScreenOff();
                return true;
            case "screen_on": setScreenOn();
                return true;
            case "deep_standby": setDeepStandBy(seconds_to_wakeup);
                return true;
            default:
                return false;
        }
    }

    private void setScreenOff()
    {
        PowerManager MyPowerManager      = (PowerManager) MyContext.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock myWakeLock = MyPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Launcher:WakeLockTag");
        myWakeLock.acquire(2*24*60*60*1000L); // 2 days

        DeviceOwner.lockNow((DevicePolicyManager) MyContext.getSystemService(Context.DEVICE_POLICY_SERVICE));
    }

    private void setScreenOn()
    {
        if (MyMainConfiguration.isDeviceRooted())
        {
            ShellExecute MyShellExecute =  new ShellExecute(Runtime.getRuntime());
            MyShellExecute.executeAsRoot("input keyevent KEYCODE_WAKEUP");
        }
        else
        {
            // ToDo: Refactor this to an own AlarmHandler Class
            AlarmManager alarmManager = (AlarmManager) MyContext.getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent("com.sagiadinos.garlic.launcher.receiver.InForegroundReceiver");
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            PendingIntent alarmIntent = PendingIntent.getBroadcast(MyContext, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            long triggerTime = System.currentTimeMillis() + 10000; // set alarm for 10 second
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, alarmIntent);
        }
     }

     private void setDeepStandBy(String seconds_to_wakeup)
     {
         // ToDo: Refactor this in a method
         StandbyFactory MyStandByFactory   = new StandbyFactory(MyMainConfiguration, MyContext);
         AbstractBaseStandby MyDeepStandBy =  MyStandByFactory.determinePlayerModel();
         if (MyDeepStandBy == null)
             return;

         MyDeepStandBy.setSecondsToWakup(parseSeconds(seconds_to_wakeup));

         MyDeepStandBy.executeStandby();
     }

    /**
     * A missing or not numeric value is handled like 0,
     * the standby model raises it to its minimum.
     */
    private int parseSeconds(String value)
    {
        if (value == null)
            return 0;

        try
        {
            return Integer.parseInt(value.trim());
        }
        catch (NumberFormatException e)
        {
            Log.w("CommandReceiver", "seconds_to_wakeup is not a number: " + value);
            return 0;
        }
    }

    private void reboot(String task_id)
    {
        if (task_id == null)
            task_id = "";
        TaskExecutionReport.append(task_id, "completed");
        if (!BuildConfig.DEBUG)
        {
            DeviceOwner.reboot(
                    (DevicePolicyManager) MyContext.getSystemService(Context.DEVICE_POLICY_SERVICE),
                    new ComponentName(MyContext, AdminReceiver.class)
            );
        }


    }
}
