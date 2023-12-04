package com.sagiadinos.garlic.launcher.helper;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.sagiadinos.garlic.launcher.BuildConfig;


public class VersionInformation
{
    Context ctx;

    public VersionInformation(Context ctx)
    {
        this.ctx = ctx;
    }

    public String forLauncher()
    {
        String debug = "";
        if (BuildConfig.DEBUG)
            debug = "debug";

        return determineVersion(DeviceOwner.LAUNCHER_PACKAGE_NAME) + debug;
    }

    public String forPlayer()
    {
        return determineVersion(DeviceOwner.PLAYER_PACKAGE_NAME);
    }

    private String determineVersion(String package_name)
    {
        String version = "";
        try
        {
            PackageInfo pInfo = ctx.getPackageManager().getPackageInfo(package_name, 0);
            version = pInfo.versionName; // + "." + pInfo.versionCode;
        }
        catch (PackageManager.NameNotFoundException e)
        {
            version = "not found";
        }

        return version;
    }

}
