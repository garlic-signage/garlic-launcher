package com.sagiadinos.garlic.launcher.helper;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.sagiadinos.garlic.launcher.BuildConfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VersionInformationTest
{
    Context ContextMocked;
    PackageManager PackageManagerMocked;

    @BeforeEach
    void setUp()
    {
        ContextMocked        = mock(Context.class);
        PackageManagerMocked = mock(PackageManager.class);
        when(ContextMocked.getPackageManager()).thenReturn(PackageManagerMocked);
    }

    @Test
    void forLauncherAddsDebugSuffixInDebugBuilds() throws PackageManager.NameNotFoundException
    {
        mockVersion("com.sagiadinos.garlic.launcher", "v0.1.0.174");

        String expected = "v0.1.0.174" + (BuildConfig.DEBUG ? "debug" : "");
        assertEquals(expected, new VersionInformation(ContextMocked).forLauncher());
    }

    @Test
    void forPlayer() throws PackageManager.NameNotFoundException
    {
        mockVersion("com.sagiadinos.garlic.player", "0.6.2.1234");

        assertEquals("0.6.2.1234", new VersionInformation(ContextMocked).forPlayer());
    }

    @Test
    void forPlayerNotInstalled() throws PackageManager.NameNotFoundException
    {
        when(PackageManagerMocked.getPackageInfo("com.sagiadinos.garlic.player", 0))
                .thenThrow(mock(PackageManager.NameNotFoundException.class));

        assertEquals("not found", new VersionInformation(ContextMocked).forPlayer());
    }

    private void mockVersion(String package_name, String version_name) throws PackageManager.NameNotFoundException
    {
        PackageInfo InfoMocked = mock(PackageInfo.class);
        InfoMocked.versionName = version_name;
        when(PackageManagerMocked.getPackageInfo(package_name, 0)).thenReturn(InfoMocked);
    }
}
