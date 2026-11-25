package com.sagiadinos.garlic.launcher;

import android.app.Activity;
import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {25, 35})
public class GarlicLauncherApplicationTest
{
    @Test
    public void foregroundFollowsActivityLifecycle()
    {
        GarlicLauncherApplication app = (GarlicLauncherApplication) RuntimeEnvironment.getApplication();
        assertFalse(app.isOnForeground());

        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).setup();
        assertTrue(app.isOnForeground());

        controller.pause();
        assertFalse(app.isOnForeground());

        controller.resume();
        assertTrue(app.isOnForeground());

        controller.pause().stop().destroy();
        assertFalse(app.isOnForeground());
    }

    @Test
    public void pauseOfAnotherActivityKeepsForeground()
    {
        GarlicLauncherApplication app = (GarlicLauncherApplication) RuntimeEnvironment.getApplication();

        ActivityController<Activity> first = Robolectric.buildActivity(Activity.class).setup();
        Robolectric.buildActivity(ActivityConfigAdmin.class).setup();
        assertTrue(app.isOnForeground());

        // the activity in the foreground is ActivityConfigAdmin, Activity only pauses late
        first.pause();
        assertTrue(app.isOnForeground());
    }

    /**
     * The foreground activity is only weakly referenced and may be collected.
     */
    @Test
    public void pauseAfterForegroundActivityWasCollected() throws Exception
    {
        GarlicLauncherApplication app = (GarlicLauncherApplication) RuntimeEnvironment.getApplication();
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Field field = GarlicLauncherApplication.class.getDeclaredField("foreground_activity");
        field.setAccessible(true);
        field.set(app, new WeakReference<Context>(null));

        app.onActivityPaused(activity);

        assertFalse(app.isOnForeground());
    }
}
