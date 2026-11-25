package com.sagiadinos.garlic.launcher;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import com.sagiadinos.garlic.launcher.configuration.MainConfiguration;
import com.sagiadinos.garlic.launcher.configuration.PasswordHasher;
import com.sagiadinos.garlic.launcher.configuration.SharedPreferencesModel;
import com.sagiadinos.garlic.launcher.helper.RootChecker;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * The provider as garlic-player uses it: through a ContentResolver with the
 * authority and paths of the manifest. SettingsProviderTest tests the class alone.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {25, 35})
public class SettingsProviderContractTest
{
    private static final String AUTHORITY = "com.sagiadinos.garlic.launcher.SettingsProvider";

    ContentResolver resolver;
    MainConfiguration MyMainConfiguration;

    @Before
    public void setUp()
    {
        Context ctx         = RuntimeEnvironment.getApplication();
        MyMainConfiguration = new MainConfiguration(new SharedPreferencesModel(ctx));
        Robolectric.setupContentProvider(SettingsProvider.class, AUTHORITY);
        resolver            = ctx.getContentResolver();
    }

    @Test
    public void smilContentUrl()
    {
        MyMainConfiguration.storeSmilIndex("https://cms.test/index.smil");

        assertEquals("https://cms.test/index.smil", queryValue("smil_content_url", null));
    }

    @Test
    public void uuid()
    {
        MyMainConfiguration.firstStart(new RootChecker());
        String uuid = MyMainConfiguration.getUUID();
        assertNotNull(uuid);

        assertEquals(uuid, queryValue("uuid", null));
    }

    @Test
    public void launcherVersion()
    {
        String version = queryValue("launcher_version", null);

        assertNotNull(version);
        assertTrue(version.endsWith(BuildConfig.DEBUG ? "debug" : ""));
    }

    @Test
    public void servicePasswordInSelection()
    {
        MyMainConfiguration.setServicePassword("secret", new PasswordHasher());

        assertEquals("valid", queryValue("service_password", "secret"));
        assertEquals("invalid", queryValue("service_password", "wrong"));
    }

    @Test
    public void unknownPathIsRejected()
    {
        assertThrows(IllegalArgumentException.class, () -> resolver.query(uri("unknown"), null, null, null, null));
    }

    @Test
    public void writesAreRejected()
    {
        ContentValues values = new ContentValues();
        values.put("uuid", "x");

        assertThrows(UnsupportedOperationException.class, () -> resolver.insert(uri("uuid"), values));
        assertThrows(UnsupportedOperationException.class, () -> resolver.update(uri("uuid"), values, null, null));
        assertThrows(UnsupportedOperationException.class, () -> resolver.delete(uri("uuid"), null, null));
    }

    @Test
    public void type()
    {
        assertEquals("vnd.android.cursor.item/vnd.com.sagiadinos.garlic.launcher.SettingsProvider.provider.string",
                resolver.getType(uri("uuid")));
    }

    private String queryValue(String path, String selection)
    {
        try (Cursor cursor = resolver.query(uri(path), null, selection, null, null))
        {
            assertNotNull(cursor);
            assertEquals(1, cursor.getCount());
            assertEquals(path, cursor.getColumnName(0));
            assertTrue(cursor.moveToFirst());
            return cursor.getString(cursor.getColumnIndexOrThrow(path));
        }
    }

    private static Uri uri(String path)
    {
        return Uri.parse("content://" + AUTHORITY + "/" + path);
    }
}
