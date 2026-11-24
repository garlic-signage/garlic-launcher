package com.sagiadinos.garlic.launcher;

import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import com.sagiadinos.garlic.launcher.configuration.MainConfiguration;
import com.sagiadinos.garlic.launcher.configuration.PasswordHasher;
import com.sagiadinos.garlic.launcher.configuration.SharedPreferencesModel;
import com.sagiadinos.garlic.launcher.testutil.FakeSharedPreferences;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * Contract of the content provider garlic-player reads from:
 * content://com.sagiadinos.garlic.launcher.SettingsProvider/{smil_content_url|service_password|uuid|launcher_version}
 * The cursor has one row with one column named like the path segment.
 * service_password expects the cleartext in "selection" and returns "valid" or "invalid".
 */
class SettingsProviderTest
{
    private static final String AUTHORITY = "com.sagiadinos.garlic.launcher.SettingsProvider";

    /** paths registered in the static UriMatcher, recorded while the class is initialised */
    private static final Map<String, Integer> registeredUris = new HashMap<>();

    Context ContextMocked;
    PackageManager PackageManagerMocked;
    FakeSharedPreferences Prefs;
    SettingsProvider MyTestClass;
    List<String[]> cursorColumns;
    List<Object> cursorValues;

    /**
     * SettingsProvider builds its UriMatcher in a static initialiser.
     * The android.jar stub cannot do that, so the class is initialised while
     * UriMatcher construction is mocked. The mock behaves like a real matcher
     * for "content://authority/path" URIs.
     */
    @BeforeAll
    static void initialiseProviderClass() throws ClassNotFoundException
    {
        Answer<Object> matcher = invocation -> {
            switch (invocation.getMethod().getName())
            {
                case "addURI":
                    registeredUris.put(invocation.getArgument(0) + "/" + invocation.getArgument(1), invocation.getArgument(2));
                    return null;
                case "match":
                    Uri uri = invocation.getArgument(0);
                    String key = uri.getAuthority() + "/" + String.join("/", uri.getPathSegments());
                    return registeredUris.getOrDefault(key, UriMatcher.NO_MATCH);
                default:
                    return null;
            }
        };

        try (MockedConstruction<UriMatcher> ignored = mockConstruction(UriMatcher.class, withSettings().defaultAnswer(matcher)))
        {
            Class.forName(SettingsProvider.class.getName(), true, SettingsProvider.class.getClassLoader());
        }
    }

    @BeforeEach
    void setUp() throws PackageManager.NameNotFoundException
    {
        ContextMocked        = mock(Context.class);
        PackageManagerMocked = mock(PackageManager.class);
        Prefs                = FakeSharedPreferences.attachTo(ContextMocked);
        cursorColumns        = new ArrayList<>();
        cursorValues         = new ArrayList<>();

        PackageInfo LauncherInfo = mock(PackageInfo.class);
        LauncherInfo.versionName = "v0.1.0.174";
        when(ContextMocked.getPackageManager()).thenReturn(PackageManagerMocked);
        when(PackageManagerMocked.getPackageInfo("com.sagiadinos.garlic.launcher", 0)).thenReturn(LauncherInfo);

        // ContentProvider() is a stub, so create the instance without constructor
        MyTestClass = mock(SettingsProvider.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        doReturn(ContextMocked).when(MyTestClass).getContext();
        assertTrue(MyTestClass.onCreate());
    }

    @Test
    void registersPlayerUris()
    {
        assertEquals(Map.of(
                AUTHORITY + "/smil_content_url", 1,
                AUTHORITY + "/service_password", 2,
                AUTHORITY + "/uuid", 3,
                AUTHORITY + "/launcher_version", 4
        ), registeredUris);
    }

    @Test
    void querySmilContentUrl()
    {
        Prefs.edit().putString("smil_index_uri", "https://example.com/index.smil").commit();

        assertQueryResult("smil_content_url", null, "https://example.com/index.smil");
    }

    @Test
    void querySmilContentUrlNotSet()
    {
        assertQueryResult("smil_content_url", null, null);
    }

    @Test
    void queryUuid()
    {
        Prefs.edit().putString("uuid", "0b6a8c1e-0000-4000-8000-000000000001").commit();

        assertQueryResult("uuid", null, "0b6a8c1e-0000-4000-8000-000000000001");
    }

    @Test
    void queryLauncherVersion()
    {
        String expected = "v0.1.0.174" + (BuildConfig.DEBUG ? "debug" : "");

        assertQueryResult("launcher_version", null, expected);
    }

    @Test
    void queryServicePasswordValid()
    {
        new MainConfiguration(new SharedPreferencesModel(ContextMocked)).setServicePassword("secret", new PasswordHasher());

        assertQueryResult("service_password", "secret", "valid");
    }

    @Test
    void queryServicePasswordInvalid()
    {
        new MainConfiguration(new SharedPreferencesModel(ContextMocked)).setServicePassword("secret", new PasswordHasher());

        assertQueryResult("service_password", "wrong", "invalid");
    }

    @Test
    void queryUnknownPathThrows()
    {
        Uri UriMocked = createUri("unknown");

        try (MockedConstruction<MatrixCursor> ignored = mockCursor())
        {
            assertThrows(IllegalArgumentException.class,
                    () -> MyTestClass.query(UriMocked, null, null, null, null));
        }
    }

    @Test
    void getTypeIsFixedString()
    {
        assertEquals("vnd.android.cursor.item/vnd.com.sagiadinos.garlic.launcher.SettingsProvider.provider.string",
                MyTestClass.getType(createUri("uuid")));
    }

    @Test
    void writeOperationsAreNotSupported()
    {
        Uri UriMocked = createUri("uuid");
        ContentValues ValuesMocked = mock(ContentValues.class);

        assertThrows(UnsupportedOperationException.class, () -> MyTestClass.insert(UriMocked, ValuesMocked));
        assertThrows(UnsupportedOperationException.class, () -> MyTestClass.update(UriMocked, ValuesMocked, null, null));
        assertThrows(UnsupportedOperationException.class, () -> MyTestClass.delete(UriMocked, null, null));
    }

    private void assertQueryResult(String path, String selection, Object expected)
    {
        Uri UriMocked = createUri(path);

        try (MockedConstruction<MatrixCursor> cursor = mockCursor())
        {
            Cursor result = MyTestClass.query(UriMocked, null, selection, null, null);

            assertSame(cursor.constructed().get(0), result);
            assertEquals(1, cursorColumns.size());
            assertArrayEquals(new String[] {path}, cursorColumns.get(0));
            assertEquals(1, cursorValues.size());
            assertEquals(expected, cursorValues.get(0));
        }
    }

    private MockedConstruction<MatrixCursor> mockCursor()
    {
        return mockConstruction(MatrixCursor.class, (mock, context) -> {
            cursorColumns.add((String[]) context.arguments().get(0));
            MatrixCursor.RowBuilder RowMocked = mock(MatrixCursor.RowBuilder.class);
            when(RowMocked.add(any())).thenAnswer(invocation -> {
                cursorValues.add(invocation.getArgument(0));
                return RowMocked;
            });
            when(mock.newRow()).thenReturn(RowMocked);
        });
    }

    private Uri createUri(String path)
    {
        Uri UriMocked = mock(Uri.class);
        when(UriMocked.getAuthority()).thenReturn(AUTHORITY);
        when(UriMocked.getPathSegments()).thenReturn(List.of(path));
        when(UriMocked.toString()).thenReturn("content://" + AUTHORITY + "/" + path);
        return UriMocked;
    }
}
