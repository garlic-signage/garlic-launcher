package com.sagiadinos.garlic.launcher.helper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs on temporary directories, not on the file system of the machine.
 */
class RootCheckerTest
{
    @TempDir
    File tmp;

    @Test
    void noBinaryMeansNotRooted()
    {
        assertFalse(create("bin/", "xbin/").isDeviceRooted());
    }

    @Test
    void suInAnyPathMeansRooted() throws IOException
    {
        createBinary("xbin", "su");

        assertTrue(create("bin/", "xbin/").isDeviceRooted());
    }

    @Test
    void busyboxMeansRooted() throws IOException
    {
        createBinary("bin", "busybox");

        assertTrue(create("bin/", "xbin/").isDeviceRooted());
    }

    @Test
    void otherBinariesDoNotCount() throws IOException
    {
        createBinary("bin", "sh");
        createBinary("bin", "sudo");

        assertFalse(create("bin/", "xbin/").isDeviceRooted());
    }

    /**
     * The result is determined once per instance.
     */
    @Test
    void resultIsCached() throws IOException
    {
        RootChecker MyTestClass = create("bin/");
        assertFalse(MyTestClass.isDeviceRooted());

        createBinary("bin", "su");

        assertFalse(MyTestClass.isDeviceRooted());
        assertTrue(create("bin/").isDeviceRooted());
    }

    @Test
    void defaultConstructorChecksTheDevicePaths()
    {
        // only that it runs; the result depends on the machine
        new RootChecker().isDeviceRooted();
    }

    private RootChecker create(String... sub_dirs)
    {
        String[] paths = new String[sub_dirs.length];
        for (int i = 0; i < sub_dirs.length; i++)
        {
            paths[i] = tmp.getAbsolutePath() + "/" + sub_dirs[i];
        }
        return new RootChecker(paths);
    }

    private void createBinary(String sub_dir, String name) throws IOException
    {
        File dir = new File(tmp, sub_dir);
        assertTrue(dir.isDirectory() || dir.mkdirs());
        assertTrue(new File(dir, name).createNewFile());
    }
}
