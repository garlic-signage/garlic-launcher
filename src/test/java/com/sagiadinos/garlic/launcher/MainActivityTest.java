package com.sagiadinos.garlic.launcher;

import android.Manifest;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.Application;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Looper;
import android.os.Process;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.sagiadinos.garlic.launcher.configuration.MainConfiguration;
import com.sagiadinos.garlic.launcher.configuration.PasswordHasher;
import com.sagiadinos.garlic.launcher.configuration.SharedPreferencesModel;
import com.sagiadinos.garlic.launcher.helper.DeviceOwner;
import com.sagiadinos.garlic.launcher.receiver.AdminReceiver;
import com.sagiadinos.garlic.launcher.services.WatchDogService;
import com.sagiadinos.garlic.launcher.testutil.ShadowRootChecker;
import com.sagiadinos.garlic.launcher.testutil.ShadowShellExecute;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowEnvironment;

import java.time.Duration;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * MainActivity on a real (Robolectric) Android of the lowest supported
 * version and a current one. The broadcasts of garlic-player go through
 * Android, not directly to the receivers.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {25, 35})
public class MainActivityTest
{
    private static final String PLAYER_PACKAGE         = DeviceOwner.PLAYER_PACKAGE_NAME;
    private static final String PLAYER_ACTIVITY        = PLAYER_PACKAGE + ".MainActivity";
    private static final String PLAYER_CLOSED_ACTION   = "com.sagiadinos.garlic.launcher.receiver.PlayerClosedReceiver";
    private static final String IN_FOREGROUND_ACTION   = "com.sagiadinos.garlic.launcher.receiver.InForegroundReceiver";
    private static final String OTHER_LAUNCHER         = "com.android.launcher3";

    Application app;
    MainConfiguration MyMainConfiguration;

    @Before
    public void setUp()
    {
        // Robolectric does not call the resetters of own shadows
        ShadowShellExecute.reset();
        ShadowRootChecker.reset();
        app                 = RuntimeEnvironment.getApplication();
        MyMainConfiguration = new MainConfiguration(new SharedPreferencesModel(app));
        // Environment.isExternalStorageManager() reads the first external storage dir,
        // Robolectric has none by default
        ShadowEnvironment.addExternalDir("external");
    }

    // ---------------------------------------------------------------- without device owner

    @Test
    public void withoutDeviceOwnerShowsHintOnly()
    {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();

        TextView tvInformation = activity.findViewById(R.id.tvInformation);
        assertEquals(app.getString(R.string.no_device_owner), tvInformation.getText().toString());
        assertEquals(View.VISIBLE, tvInformation.getVisibility());
        assertTrue(shadowOf(app).getRegisteredReceivers().isEmpty());
        assertNull(shadowOf(app).getNextStartedActivity());
    }

    @Test
    public void firstStartCreatesUuidAndDefaultContent()
    {
        assertTrue(MyMainConfiguration.isFirstStart());

        Robolectric.buildActivity(MainActivity.class).setup();

        assertFalse(MyMainConfiguration.isFirstStart());
        assertNotNull(MyMainConfiguration.getUUID());
        assertNotNull(MyMainConfiguration.getSmilIndex());
    }

    @Test
    public void notRootedDoesNotUseShell()
    {
        Robolectric.buildActivity(MainActivity.class).setup();

        assertFalse(MyMainConfiguration.isDeviceRooted());
        assertTrue(ShadowShellExecute.getCommands().isEmpty());
    }

    @Test
    public void rootedDeviceTriesToBecomeDeviceOwner()
    {
        ShadowRootChecker.setRooted(true);

        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();

        assertTrue(MyMainConfiguration.isDeviceRooted());
        assertEquals(List.of("dpm set-device-owner com.sagiadinos.garlic.launcher/.receiver.AdminReceiver"),
                ShadowShellExecute.getCommands());
        assertEquals(app.getString(R.string.no_device_owner), text(activity, R.id.tvInformation));
    }

    @Test
    public void rootedDeviceOwnerGrantsMissingPermissionsViaShell()
    {
        ShadowRootChecker.setRooted(true);
        becomeDeviceOwner();

        Robolectric.buildActivity(MainActivity.class).setup();

        assertTrue(ShadowShellExecute.getCommands().contains(
                "pm grant com.sagiadinos.garlic.launcher android.permission.WRITE_EXTERNAL_STORAGE"));
        assertTrue(ShadowShellExecute.getCommands().contains(
                "pm grant com.sagiadinos.garlic.launcher android.permission.READ_EXTERNAL_STORAGE"));
    }

    @Test
    public void withoutDeviceOwnerSurvivesLifecycle()
    {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup();

        controller.pause().stop().restart().start().resume().pause().stop().destroy();
    }

    // ---------------------------------------------------------------- as device owner

    @Test
    public void deviceOwnerRegistersReceiversForPlayer()
    {
        becomeDeviceOwner();

        Robolectric.buildActivity(MainActivity.class).setup();

        assertTrue(isReceiverRegisteredFor(IN_FOREGROUND_ACTION));
        assertTrue(isReceiverRegisteredFor(PLAYER_CLOSED_ACTION));
    }

    @Test
    public void deviceOwnerPermitsLockTaskForLauncherAndPlayer()
    {
        becomeDeviceOwner();

        Robolectric.buildActivity(MainActivity.class).setup();

        DevicePolicyManager dpm = (DevicePolicyManager) app.getSystemService(Context.DEVICE_POLICY_SERVICE);
        assertTrue(dpm.isLockTaskPermitted(DeviceOwner.LAUNCHER_PACKAGE_NAME));
        assertTrue(dpm.isLockTaskPermitted(PLAYER_PACKAGE));
    }

    @Test
    public void withoutPlayerShowsHintAndHidesPlayButton()
    {
        becomeDeviceOwner();

        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();

        assertEquals(app.getString(R.string.no_garlic), text(activity, R.id.tvInformation));
        assertEquals(View.INVISIBLE, activity.findViewById(R.id.btStartPlayer).getVisibility());
        assertFalse(MyMainConfiguration.isPlayerInstalled());
    }

    @Test
    public void installedPlayerStartsAfterCountdown()
    {
        becomeDeviceOwner();
        installPlayer();
        MyMainConfiguration.storePlayerStartDelay(5);

        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();

        assertTrue(MyMainConfiguration.isPlayerInstalled());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.btStartPlayer).getVisibility());
        assertFalse(activity.isPlayerPlaying());
        assertNull(nextStartedPlayer());

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6));

        assertTrue(activity.isPlayerPlaying());
        assertPlayerStarted();
        assertWatchDogStarted();
    }

    @Test
    public void playerStartsInstantlyAfterBootWhenConfigured()
    {
        becomeDeviceOwner();
        installPlayer();
        MyMainConfiguration.toggleNoPlayerStartDelayAfterBoot(true);
        MyMainConfiguration.toggleJustBooted(true);

        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();

        assertTrue(activity.isPlayerPlaying());
        assertPlayerStarted();
        assertFalse(MyMainConfiguration.isJustBooted());
    }

    @Test
    public void playButtonStopsCountdownAndSecondClickStartsPlayer()
    {
        becomeDeviceOwner();
        installPlayer();
        MyMainConfiguration.storePlayerStartDelay(5);
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));

        Button btStartPlayer = activity.findViewById(R.id.btStartPlayer);
        btStartPlayer.performClick();

        assertEquals(app.getString(R.string.play), btStartPlayer.getText().toString());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10));
        assertFalse(activity.isPlayerPlaying());
        assertNull(nextStartedPlayer());

        btStartPlayer.performClick();

        assertTrue(activity.isPlayerPlaying());
        assertPlayerStarted();
    }

    @Test
    public void playerClosedBroadcastStopsPlayer()
    {
        becomeDeviceOwner();
        installPlayer();
        MainActivity activity = startedPlayer();

        app.sendBroadcast(new Intent(PLAYER_CLOSED_ACTION));
        shadowOf(Looper.getMainLooper()).idle();

        assertFalse(activity.isPlayerPlaying());
        assertEquals(app.getString(R.string.play), text(activity, R.id.btStartPlayer));
        Intent stopped = shadowOf(app).getNextStoppedService();
        assertNotNull(stopped);
        assertEquals(WatchDogService.class.getName(), stopped.getComponent().getClassName());
    }

    @Test
    public void inForegroundBroadcastRestartsPlayingPlayer()
    {
        becomeDeviceOwner();
        installPlayer();
        MainActivity activity = startedPlayer();
        clearStartedActivities();

        app.sendBroadcast(new Intent(IN_FOREGROUND_ACTION));
        shadowOf(Looper.getMainLooper()).idle();

        assertTrue(activity.isPlayerPlaying());
        assertPlayerStarted();
    }

    @Test
    public void inForegroundBroadcastKeepsStoppedPlayerStopped()
    {
        becomeDeviceOwner();
        installPlayer();
        MainActivity activity = startedPlayer();
        app.sendBroadcast(new Intent(PLAYER_CLOSED_ACTION));
        shadowOf(Looper.getMainLooper()).idle();
        clearStartedActivities();

        app.sendBroadcast(new Intent(IN_FOREGROUND_ACTION));
        shadowOf(Looper.getMainLooper()).idle();

        assertFalse(activity.isPlayerPlaying());
        assertNull(nextStartedPlayer());
    }

    @Test
    public void destroyUnregistersReceivers()
    {
        becomeDeviceOwner();
        grantAllPermissions();

        Robolectric.buildActivity(MainActivity.class).setup().pause().stop().destroy();

        assertFalse(isReceiverRegisteredFor(IN_FOREGROUND_ACTION));
        assertFalse(isReceiverRegisteredFor(PLAYER_CLOSED_ACTION));
    }

    /**
     * From Android 8 hasAllPermissions() is false with targetSdk 25.
     * The receivers must be unregistered anyway.
     */
    @Test
    public void destroyUnregistersReceiversWithoutAllPermissions()
    {
        becomeDeviceOwner();

        Robolectric.buildActivity(MainActivity.class).setup().pause().stop().destroy();

        assertFalse(isReceiverRegisteredFor(IN_FOREGROUND_ACTION));
        assertFalse(isReceiverRegisteredFor(PLAYER_CLOSED_ACTION));
    }

    @Test
    public void recreateDoesNotRegisterReceiversTwice()
    {
        becomeDeviceOwner();
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup();

        controller.recreate();
        controller.recreate();

        assertEquals(1, receiversRegisteredFor(IN_FOREGROUND_ACTION));
        assertEquals(1, receiversRegisteredFor(PLAYER_CLOSED_ACTION));
    }

    @Test
    public void destroyWithoutDeviceOwnerDoesNotCrash()
    {
        Robolectric.buildActivity(MainActivity.class).setup().pause().stop().destroy();

        assertTrue(shadowOf(app).getRegisteredReceivers().isEmpty());
    }

    // ---------------------------------------------------------------- service and strict mode

    @Test
    public void serviceModeShowsAdminButtons()
    {
        becomeDeviceOwner();

        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();

        assertEquals(View.VISIBLE, activity.findViewById(R.id.btAdminConfiguration).getVisibility());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.btAndroidSettings).getVisibility());
        assertTrue(activity.findViewById(R.id.btStartPlayer).isEnabled());
        assertEquals(app.getString(R.string.enter_strict_mode), text(activity, R.id.btToggleServiceMode));
    }

    @Test
    public void strictModeHidesAdminButtons()
    {
        becomeDeviceOwner();
        MyMainConfiguration.setStrictKioskMode(true);

        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();

        assertEquals(View.GONE, activity.findViewById(R.id.btAdminConfiguration).getVisibility());
        assertEquals(View.GONE, activity.findViewById(R.id.btAndroidSettings).getVisibility());
        assertFalse(activity.findViewById(R.id.btStartPlayer).isEnabled());
        assertEquals(app.getString(R.string.enter_service_mode), text(activity, R.id.btToggleServiceMode));
    }

    @Test
    public void toggleButtonOnlyWithActiveServicePassword()
    {
        becomeDeviceOwner();
        MainActivity without = Robolectric.buildActivity(MainActivity.class).setup().get();
        assertEquals(View.GONE, without.findViewById(R.id.btToggleServiceMode).getVisibility());

        MyMainConfiguration.toggleActiveServicePassword(true);
        MainActivity with = Robolectric.buildActivity(MainActivity.class).setup().get();
        assertEquals(View.VISIBLE, with.findViewById(R.id.btToggleServiceMode).getVisibility());
    }

    @Test
    public void correctServicePasswordLeavesStrictMode()
    {
        becomeDeviceOwner();
        MyMainConfiguration.setStrictKioskMode(true);
        MyMainConfiguration.toggleActiveServicePassword(true);
        MyMainConfiguration.setServicePassword("secret", new PasswordHasher());
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();

        enterServicePassword(activity, "secret");

        assertFalse(MyMainConfiguration.isStrictKioskModeActive());
    }

    @Test
    public void correctServicePasswordEntersStrictMode()
    {
        becomeDeviceOwner();
        MyMainConfiguration.toggleActiveServicePassword(true);
        MyMainConfiguration.setServicePassword("secret", new PasswordHasher());
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();

        enterServicePassword(activity, "secret");

        assertTrue(MyMainConfiguration.isStrictKioskModeActive());
    }

    @Test
    public void wrongServicePasswordKeepsStrictMode()
    {
        becomeDeviceOwner();
        MyMainConfiguration.setStrictKioskMode(true);
        MyMainConfiguration.toggleActiveServicePassword(true);
        MyMainConfiguration.setServicePassword("secret", new PasswordHasher());
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();

        enterServicePassword(activity, "wrong");

        assertTrue(MyMainConfiguration.isStrictKioskModeActive());
    }

    @Test
    public void adminButtonOpensConfiguration()
    {
        becomeDeviceOwner();
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        clearStartedActivities();

        activity.findViewById(R.id.btAdminConfiguration).performClick();

        Intent started = shadowOf(app).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(ActivityConfigAdmin.class.getName(), started.getComponent().getClassName());
    }

    @Test
    public void androidSettingsButtonOpensSettings()
    {
        becomeDeviceOwner();
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();

        activity.findViewById(R.id.btAndroidSettings).performClick();

        Intent started = shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(android.provider.Settings.ACTION_SETTINGS, started.getAction());
    }

    // ---------------------------------------------------------------- leaving the kiosk by touch

    @Test
    public void tenTouchesInCornerOpenOtherLauncher()
    {
        becomeDeviceOwner();
        installOtherLauncher();
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        clearStartedActivities();

        touchCorner(activity, 9);
        assertNull(shadowOf(app).getNextStartedActivity());

        touchCorner(activity, 1);

        Intent started = shadowOf(app).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(new ComponentName(OTHER_LAUNCHER, OTHER_LAUNCHER + ".Launcher"), started.getComponent());
        assertTrue(started.hasCategory(Intent.CATEGORY_HOME));
        assertFalse(activity.isPlayerPlaying());
    }

    @Test
    public void touchesInStrictModeAreIgnored()
    {
        becomeDeviceOwner();
        installOtherLauncher();
        MyMainConfiguration.setStrictKioskMode(true);
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        clearStartedActivities();

        touchCorner(activity, 20);

        assertNull(shadowOf(app).getNextStartedActivity());
    }

    @Test
    public void touchesWithoutDeviceOwnerAreIgnored()
    {
        installOtherLauncher();
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        clearStartedActivities();

        touchCorner(activity, 20);

        assertNull(shadowOf(app).getNextStartedActivity());
    }

    @Test
    public void touchesInMiddleAreIgnored()
    {
        becomeDeviceOwner();
        installOtherLauncher();
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        clearStartedActivities();

        for (int i = 0; i < 20; i++)
        {
            activity.onTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 1, 1, 0));
        }

        assertNull(shadowOf(app).getNextStartedActivity());
    }

    @Test
    public void withoutOtherLauncherNothingIsStarted()
    {
        becomeDeviceOwner();
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        clearStartedActivities();

        touchCorner(activity, 10);

        assertNull(shadowOf(app).getNextStartedActivity());
    }

    // ---------------------------------------------------------------- more player start cases

    @Test
    public void emptyServicePasswordRestartsCountdown()
    {
        becomeDeviceOwner();
        installPlayer();
        MyMainConfiguration.storePlayerStartDelay(5);
        MyMainConfiguration.toggleActiveServicePassword(true);
        MyMainConfiguration.setServicePassword("secret", new PasswordHasher());
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();

        enterServicePassword(activity, "");
        clearStartedActivities();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6));

        assertTrue(activity.isPlayerPlaying());
        assertPlayerStarted();
    }

    @Test
    public void withoutContentUrlCountdownDoesNotStart()
    {
        becomeDeviceOwner();
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        MyMainConfiguration.storeSmilIndex(null);

        activity.startGarlicPlayerDelayed();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(60));

        assertEquals(app.getString(R.string.play), text(activity, R.id.btStartPlayer));
        assertFalse(activity.isPlayerPlaying());
    }

    @Test
    public void playerWithoutLaunchActivityShowsHint()
    {
        becomeDeviceOwner();
        installPlayerPackageOnly();
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();

        activity.startGarlicPlayerInstantly(null);

        assertEquals(PLAYER_PACKAGE + " do not exist", text(activity, R.id.tvInformation));
    }

    @Test
    public void rootedDeviceGrantsMissingPlayerPermissions()
    {
        ShadowRootChecker.setRooted(true);
        ShadowShellExecute.setResult(true);
        becomeDeviceOwner();
        installPlayerPackageOnly();

        Robolectric.buildActivity(MainActivity.class).setup();

        assertTrue(ShadowShellExecute.getCommands().contains(
                "pm grant " + PLAYER_PACKAGE + " android.permission.READ_EXTERNAL_STORAGE"));
        assertTrue(ShadowShellExecute.getCommands().contains(
                "pm grant " + PLAYER_PACKAGE + " android.permission.WRITE_EXTERNAL_STORAGE"));
    }

    @Test
    public void setPlayerStatus()
    {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();

        activity.setPlayerStatus(MainActivity.PlayerState.PLAYING);
        assertTrue(activity.isPlayerPlaying());

        activity.setPlayerStatus(MainActivity.PlayerState.WAITING);
        assertFalse(activity.isPlayerPlaying());
    }

    @Test
    public void restartUpdatesServiceModeButton()
    {
        becomeDeviceOwner();
        grantAllPermissions();
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup();
        assertEquals(View.GONE, controller.get().findViewById(R.id.btToggleServiceMode).getVisibility());

        controller.pause().stop();
        MyMainConfiguration.toggleActiveServicePassword(true);
        controller.restart().start().resume();

        assertEquals(View.VISIBLE, controller.get().findViewById(R.id.btToggleServiceMode).getVisibility());
    }

    // ---------------------------------------------------------------- helpers

    private void touchCorner(MainActivity activity, int count)
    {
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        for (int i = 0; i < count; i++)
        {
            activity.onTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN,
                    metrics.widthPixels - 1, metrics.heightPixels - 1, 0));
        }
    }

    private void installOtherLauncher()
    {
        ComponentName launcher = new ComponentName(OTHER_LAUNCHER, OTHER_LAUNCHER + ".Launcher");
        shadowOf(app.getPackageManager()).addActivityIfNotPresent(launcher);
        IntentFilter filter = new IntentFilter(Intent.ACTION_MAIN);
        filter.addCategory(Intent.CATEGORY_HOME);
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        shadowOf(app.getPackageManager()).addIntentFilterForActivity(launcher, filter);
    }

    private void installPlayerPackageOnly()
    {
        PackageInfo info                 = new PackageInfo();
        info.packageName                 = PLAYER_PACKAGE;
        info.applicationInfo             = new ApplicationInfo();
        info.applicationInfo.packageName = PLAYER_PACKAGE;
        shadowOf(app.getPackageManager()).installPackage(info);
    }


    private void becomeDeviceOwner()
    {
        DevicePolicyManager dpm = (DevicePolicyManager) app.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin     = new ComponentName(app, AdminReceiver.class);
        shadowOf(dpm).setActiveAdmin(admin);
        shadowOf(dpm).setDeviceOwner(admin);
    }

    private void grantAllPermissions()
    {
        ShadowApplication shadowApp = shadowOf(app);
        shadowApp.grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            shadowOf(app.getPackageManager()).setCanRequestPackageInstalls(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        {
            // Environment.isExternalStorageManager() reads this app op
            AppOpsManager appOps = (AppOpsManager) app.getSystemService(Context.APP_OPS_SERVICE);
            shadowOf(appOps).setMode("android:manage_external_storage", Process.myUid(),
                    app.getPackageName(), AppOpsManager.MODE_ALLOWED);
        }
    }

    private void installPlayer()
    {
        PackageInfo info                   = new PackageInfo();
        info.packageName                   = PLAYER_PACKAGE;
        info.requestedPermissions          = new String[] {
                "android.permission.READ_EXTERNAL_STORAGE",
                "android.permission.WRITE_EXTERNAL_STORAGE"
        };
        info.requestedPermissionsFlags     = new int[] {
                PackageInfo.REQUESTED_PERMISSION_GRANTED,
                PackageInfo.REQUESTED_PERMISSION_GRANTED
        };
        info.applicationInfo               = new ApplicationInfo();
        info.applicationInfo.packageName   = PLAYER_PACKAGE;
        shadowOf(app.getPackageManager()).installPackage(info);

        ComponentName player = new ComponentName(PLAYER_PACKAGE, PLAYER_ACTIVITY);
        shadowOf(app.getPackageManager()).addActivityIfNotPresent(player);
        IntentFilter filter = new IntentFilter(Intent.ACTION_MAIN);
        filter.addCategory(Intent.CATEGORY_LAUNCHER);
        shadowOf(app.getPackageManager()).addIntentFilterForActivity(player, filter);
    }

    private MainActivity startedPlayer()
    {
        MyMainConfiguration.toggleNoPlayerStartDelayAfterBoot(true);
        MyMainConfiguration.toggleJustBooted(true);
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        assertTrue(activity.isPlayerPlaying());
        return activity;
    }

    private void enterServicePassword(MainActivity activity, String password)
    {
        activity.findViewById(R.id.btToggleServiceMode).performClick();
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(dialog);
        EditText input = (EditText) shadowOf(dialog).getView();
        input.setText(password);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
    }

    private boolean isReceiverRegisteredFor(String action)
    {
        for (ShadowApplication.Wrapper wrapper : shadowOf(app).getRegisteredReceivers())
        {
            if (wrapper.getIntentFilter().hasAction(action))
                return true;
        }
        return false;
    }

    private int receiversRegisteredFor(String action)
    {
        int count = 0;
        for (ShadowApplication.Wrapper wrapper : shadowOf(app).getRegisteredReceivers())
        {
            if (wrapper.getIntentFilter().hasAction(action))
                count++;
        }
        return count;
    }

    private void clearStartedActivities()
    {
        //noinspection StatementWithEmptyBody
        while (shadowOf(app).getNextStartedActivity() != null);
    }

    private Intent nextStartedPlayer()
    {
        Intent started;
        while ((started = shadowOf(app).getNextStartedActivity()) != null)
        {
            if (started.getComponent() != null && PLAYER_PACKAGE.equals(started.getComponent().getPackageName()))
                return started;
            if (PLAYER_PACKAGE.equals(started.getPackage()))
                return started;
        }
        return null;
    }

    private void assertPlayerStarted()
    {
        assertNotNull("garlic-player was not started", nextStartedPlayer());
    }

    private void assertWatchDogStarted()
    {
        Intent service;
        while ((service = shadowOf(app).getNextStartedService()) != null)
        {
            if (service.getComponent() != null && WatchDogService.class.getName().equals(service.getComponent().getClassName()))
                return;
        }
        throw new AssertionError("WatchDogService was not started");
    }

    private String text(MainActivity activity, int id)
    {
        return ((TextView) activity.findViewById(id)).getText().toString();
    }
}
