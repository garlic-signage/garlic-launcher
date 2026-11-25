package com.sagiadinos.garlic.launcher.testutil;

import com.sagiadinos.garlic.launcher.helper.RootChecker;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Resetter;

/**
 * RootChecker looks for su on the file system. On the machine that runs the
 * tests it finds the su of the host, so the tests decide instead.
 */
@Implements(RootChecker.class)
public class ShadowRootChecker
{
    private static boolean rooted = false;

    public static void setRooted(boolean value)
    {
        rooted = value;
    }

    @Implementation
    protected boolean isDeviceRooted()
    {
        return rooted;
    }

    @Resetter
    public static void reset()
    {
        rooted = false;
    }
}
