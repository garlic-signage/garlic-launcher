package com.sagiadinos.garlic.launcher.services;

import android.content.Context;
import android.view.View;
import android.view.WindowManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowWindowManagerImpl;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The own back button overlay on rooted devices.
 * A click runs "su" and is therefore not tested here.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {25, 35})
public class HUDTest
{
    @Test
    public void createAddsOverlayAndDestroyRemovesIt()
    {
        ServiceController<HUD> controller = Robolectric.buildService(HUD.class).create();

        List<View> views = overlayViews(controller.get());
        assertEquals(1, views.size());
        WindowManager.LayoutParams params = (WindowManager.LayoutParams) views.get(0).getLayoutParams();
        assertEquals(64, params.width);
        assertEquals(64, params.height);
        assertTrue((params.flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0);

        controller.destroy();

        assertTrue(overlayViews(controller.get()).isEmpty());
    }

    private List<View> overlayViews(HUD service)
    {
        WindowManager wm = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
        return ((ShadowWindowManagerImpl) Shadow.extract(wm)).getViews();
    }
}
