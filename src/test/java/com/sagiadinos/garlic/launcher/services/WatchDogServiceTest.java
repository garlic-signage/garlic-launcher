package com.sagiadinos.garlic.launcher.services;

import android.app.Activity;
import android.app.Application;
import android.app.Service;
import android.content.Intent;
import android.os.Looper;

import com.sagiadinos.garlic.launcher.GarlicLauncherApplication;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * While the player runs, the watchdog asks every few seconds whether a
 * launcher activity came to the foreground (InForegroundReceiver).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {25, 35})
public class WatchDogServiceTest
{
    private static final String IN_FOREGROUND_ACTION = "com.sagiadinos.garlic.launcher.receiver.InForegroundReceiver";

    Application app;

    @Before
    public void setUp()
    {
        app = RuntimeEnvironment.getApplication();
        shadowOf(app).clearBroadcastIntents();
    }

    @Test
    public void usesLauncherApplication()
    {
        assertTrue(app instanceof GarlicLauncherApplication);
    }

    @Test
    public void startIsNotSticky()
    {
        WatchDogService service = Robolectric.buildService(WatchDogService.class).create().get();

        assertEquals(Service.START_NOT_STICKY, service.onStartCommand(new Intent(), 0, 1));
    }

    @Test
    public void noBroadcastWhileLauncherInBackground()
    {
        Robolectric.buildService(WatchDogService.class).create();

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30));

        assertEquals(0, inForegroundBroadcasts());
    }

    @Test
    public void broadcastsWhileLauncherInForeground()
    {
        ActivityController<Activity> launcher = Robolectric.buildActivity(Activity.class).setup();
        Robolectric.buildService(WatchDogService.class).create();

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(7));
        assertEquals(0, inForegroundBroadcasts());

        // first check after 8 s, then every 5 s
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertEquals(1, inForegroundBroadcasts());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5));
        assertEquals(2, inForegroundBroadcasts());

        Intent sent = lastInForegroundBroadcast();
        assertTrue((sent.getFlags() & Intent.FLAG_INCLUDE_STOPPED_PACKAGES) != 0);

        launcher.pause();
        shadowOf(app).clearBroadcastIntents();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20));
        assertEquals(0, inForegroundBroadcasts());
    }

    @Test
    public void destroyStopsChecks()
    {
        Robolectric.buildActivity(Activity.class).setup();
        ServiceController<WatchDogService> service = Robolectric.buildService(WatchDogService.class).create();

        service.destroy();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30));

        assertEquals(0, inForegroundBroadcasts());
    }

    private int inForegroundBroadcasts()
    {
        return inForeground().size();
    }

    private Intent lastInForegroundBroadcast()
    {
        List<Intent> sent = inForeground();
        return sent.get(sent.size() - 1);
    }

    private List<Intent> inForeground()
    {
        List<Intent> result = new ArrayList<>();
        for (Intent intent : shadowOf(app).getBroadcastIntents())
        {
            if (IN_FOREGROUND_ACTION.equals(intent.getAction()))
                result.add(intent);
        }
        return result;
    }
}
