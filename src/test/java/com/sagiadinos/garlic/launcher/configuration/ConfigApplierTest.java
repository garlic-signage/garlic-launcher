package com.sagiadinos.garlic.launcher.configuration;

import android.content.Context;
import android.net.wifi.WifiManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

/**
 * Config from the player via SettingsProvider: player name, content url, volume, brightness.
 * WiFi and ethernet stay USB only. Robolectric, because of the XmlPullParser.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {25, 35})
public class ConfigApplierTest
{
    Context ctx;
    MainConfiguration MyMainConfiguration;
    ConfigApplier MyApplier;

    @Before
    public void setUp()
    {
        ctx                 = RuntimeEnvironment.getApplication();
        MyMainConfiguration = new MainConfiguration(new SharedPreferencesModel(ctx));
        MyApplier           = new ConfigApplier(ctx, MyMainConfiguration);
    }

    @Test
    public void storesNameUrlVolumeAndBrightness()
    {
        MyApplier.apply("<configuration xmlns=\"http://schemas.adfotain.org/config-1.0\"><userPref>" +
                "<prop name=\"info.playerName\" value=\"Lobby 1\"/>" +
                "<prop name=\"content.serverUrl\" value=\"https://cms.test/smil-index\"/>" +
                "<prop name=\"volume\" value=\"20%\"/>" +
                "<prop name=\"brightness\" value=\"40%\"/>" +
                "</userPref></configuration>");

        assertEquals("Lobby 1", MyMainConfiguration.getPlayerName());
        assertEquals("https://cms.test/smil-index", MyMainConfiguration.getSmilIndex());
        assertEquals(20, MyMainConfiguration.getVolume());
        assertEquals(40, MyMainConfiguration.getBrightness());
    }

    @Test
    public void keepsVolumeAndBrightnessWhenMissing()
    {
        MyMainConfiguration.storeVolume(30);
        MyMainConfiguration.storeBrightness(60);

        MyApplier.apply("<configuration><userPref>" +
                "<prop name=\"content.serverUrl\" value=\"https://cms.test/smil-index\"/>" +
                "</userPref></configuration>");

        assertEquals(30, MyMainConfiguration.getVolume());
        assertEquals(60, MyMainConfiguration.getBrightness());
    }

    @Test
    @SuppressWarnings("deprecation") // getConfiguredNetworks, only to see that nothing was added
    public void ignoresWifi()
    {
        WifiManager wifi    = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
        int networks_before = wifi.getConfiguredNetworks().size();

        MyApplier.apply("<configuration><userPref>" +
                "<prop name=\"net.wifi.ssid\" value=\"EVIL\"/>" +
                "<prop name=\"net.wifi.password\" value=\"secret\"/>" +
                "</userPref></configuration>");

        assertEquals(networks_before, wifi.getConfiguredNetworks().size());
    }

    @Test
    public void brokenXmlDoesNotThrow()
    {
        MyApplier.apply("<configuration><userPref><prop name=");

        assertEquals(100, MyMainConfiguration.getVolume());
    }
}
