package com.sagiadinos.garlic.launcher.configuration;

import android.content.Context;
import android.net.wifi.WifiManager;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

/**
 * Config from the player via SettingsProvider: player name, content url, volume, brightness.
 * WiFi and ethernet stay USB only. Robolectric, because of the XmlPullParser.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {25, 35})
public class ConfigApplierTest
{
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

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

    /** USB: same values from a file, the network data is returned for the receiver */
    @Test
    public void applyFileStoresValuesAndReturnsNetworkData() throws IOException
    {
        File config = tmp.newFile("configuration.xml");
        Files.write(config.toPath(), ("<configuration><userPref>" +
                "<prop name=\"info.playerName\" value=\"Lobby 2\"/>" +
                "<prop name=\"volume\" value=\"10%\"/>" +
                "<prop name=\"net.wifi.ssid\" value=\"SITE\"/>" +
                "</userPref></configuration>").getBytes(StandardCharsets.UTF_8));

        NetworkData network = MyApplier.applyFile(config);

        assertEquals("Lobby 2", MyMainConfiguration.getPlayerName());
        assertEquals(10, MyMainConfiguration.getVolume());
        assertEquals("SITE", network.getWifiSSID());
    }

    @Test
    public void applyFileWithMissingFileThrows()
    {
        assertThrows(IOException.class, () -> MyApplier.applyFile(new File(tmp.getRoot(), "missing.xml")));
    }

    @Test
    public void brokenXmlDoesNotThrow()
    {
        MyApplier.apply("<configuration><userPref><prop name=");

        assertEquals(100, MyMainConfiguration.getVolume());
    }
}
