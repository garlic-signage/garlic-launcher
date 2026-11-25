package com.sagiadinos.garlic.launcher.helper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The player cache is deleted only when less than 15 % of the disc are free.
 */
class CleanUpCacheTest
{
    @TempDir
    File tmp;

    File cache;
    File nested_file;
    DiscSpace DiscSpaceMocked;

    @BeforeEach
    void setUp() throws IOException
    {
        cache = new File(tmp, "garlic-player/cache");
        File nested = new File(cache, "media/images");
        assertTrue(nested.mkdirs());
        nested_file = new File(nested, "image.jpg");
        assertTrue(nested_file.createNewFile());
        assertTrue(new File(cache, "index.smil").createNewFile());
        DiscSpaceMocked = mock(DiscSpace.class);
    }

    @Test
    void cacheIsDeletedRecursivelyBelow15PercentFree()
    {
        when(DiscSpaceMocked.getFreePercent()).thenReturn(14);

        new CleanUp(tmp.getAbsolutePath(), DiscSpaceMocked).removeCache();

        assertFalse(cache.exists());
        assertTrue(new File(tmp, "garlic-player").exists());
    }

    @Test
    void cacheIsKeptAt15PercentFree()
    {
        when(DiscSpaceMocked.getFreePercent()).thenReturn(15);

        new CleanUp(tmp.getAbsolutePath(), DiscSpaceMocked).removeCache();

        assertTrue(nested_file.exists());
    }

    @Test
    void removeAllDeletesApksXmlAndFullCache() throws IOException
    {
        File apk = new File(cache, "garlic-player.apk");
        File xml = new File(cache, "config.xml");
        assertTrue(apk.createNewFile());
        assertTrue(xml.createNewFile());
        when(DiscSpaceMocked.getFreePercent()).thenReturn(50);

        new CleanUp(tmp.getAbsolutePath(), DiscSpaceMocked).removeAll();

        assertFalse(apk.exists());
        assertFalse(xml.exists());
        // enough space: the media stay
        assertTrue(nested_file.exists());
        assertTrue(new File(cache, "index.smil").exists());
    }

    @Test
    void missingCacheDirIsNoProblem()
    {
        when(DiscSpaceMocked.getFreePercent()).thenReturn(1);
        CleanUp MyTestClass = new CleanUp(new File(tmp, "other").getAbsolutePath(), DiscSpaceMocked);

        MyTestClass.removeAll();

        assertTrue(cache.exists());
    }

    /**
     * listFiles() returns null for a directory without read permission.
     */
    @Test
    void unreadableDirInCacheDoesNotCrash()
    {
        File locked = new File(cache, "media");
        assumeTrue(locked.setReadable(false) && !locked.canRead(), "needs a user without root rights");
        when(DiscSpaceMocked.getFreePercent()).thenReturn(1);

        try
        {
            new CleanUp(tmp.getAbsolutePath(), DiscSpaceMocked).removeCache();

            assertTrue(locked.exists());
            assertFalse(new File(cache, "index.smil").exists());
        }
        finally
        {
            assertTrue(locked.setReadable(true));
        }
    }
}
