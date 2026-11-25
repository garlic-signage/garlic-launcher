package com.sagiadinos.garlic.launcher.deepstandby;

import android.content.Context;

import com.sagiadinos.garlic.launcher.configuration.MainConfiguration;
import com.sagiadinos.garlic.launcher.configuration.SharedPreferencesModel;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowBuild;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Deep standby exists only on rooted Christiansen players (model "BT-...").
 * Robolectric, because Build.MODEL is null in plain unit tests.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 25)
public class StandbyFactoryTest
{
    Context ctx;
    MainConfiguration MyMainConfiguration;

    @Before
    public void setUp()
    {
        ctx                 = RuntimeEnvironment.getApplication();
        MyMainConfiguration = new MainConfiguration(new SharedPreferencesModel(ctx));
    }

    @Test
    public void rootedChristiansenPlayer()
    {
        ShadowBuild.setModel("BT-4K");
        new SharedPreferencesModel(ctx).storeBoolean("is_device_rooted", true);

        assertTrue(new StandbyFactory(MyMainConfiguration, ctx).determinePlayerModel() instanceof ChristiansenPlayer);
    }

    @Test
    public void notRootedChristiansenPlayer()
    {
        ShadowBuild.setModel("BT-4K");
        new SharedPreferencesModel(ctx).storeBoolean("is_device_rooted", false);

        assertNull(new StandbyFactory(MyMainConfiguration, ctx).determinePlayerModel());
    }

    @Test
    public void rootedOtherDevice()
    {
        ShadowBuild.setModel("Pixel 8");
        new SharedPreferencesModel(ctx).storeBoolean("is_device_rooted", true);

        assertNull(new StandbyFactory(MyMainConfiguration, ctx).determinePlayerModel());
    }
}
