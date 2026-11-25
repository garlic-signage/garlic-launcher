package com.sagiadinos.garlic.launcher.configuration;

import android.content.Context;
import android.os.Environment;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Parsing of config.xml (from USB or the player). Robolectric, because the
 * XmlPullParser of the android.jar stub has no implementation.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {25, 35})
public class ConfigXMLModelParseTest
{
    NetworkData MyNetworkData;
    MainConfiguration MyMainConfiguration;
    ConfigXMLModel MyModel;

    @Before
    public void setUp()
    {
        Context ctx         = RuntimeEnvironment.getApplication();
        MyNetworkData       = new NetworkData();
        MyMainConfiguration = new MainConfiguration(new SharedPreferencesModel(ctx));
        MyModel             = new ConfigXMLModel(MyNetworkData, MyMainConfiguration);
    }

    @Test
    public void defaultsWithoutProps()
    {
        MyModel.parseConfigXml("<configuration><userPref/></configuration>");

        assertEquals(100, MyModel.getVolume());
        assertEquals(100, MyModel.getBrightness());
        assertEquals("", MyModel.getStandbyMode());
        assertEquals("", MyModel.getRebootTime());
        assertNull(MyModel.getRebootDays());
        assertEquals("", MyNetworkData.getWifiSSID());
        assertNull(MyMainConfiguration.getSmilIndex());
    }

    /**
     * The sample as it comes from a USB stick, read with readConfigXml.
     */
    @Test
    public void sampleFileFromUsb() throws IOException
    {
        MyModel.parseConfigXml(MyModel.readConfigXml(new File("sampledata/files/config_wlan_dhcp.xml")));

        assertEquals("https://indexes.a-server.test/", MyMainConfiguration.getSmilIndex());
        assertEquals("THESSID", MyNetworkData.getWifiSSID());
        assertEquals("WPA2PSK", MyNetworkData.getWifiAuthentication());
        assertEquals("TKIP", MyNetworkData.getWifiEncryption());
        assertEquals("secret_passwd", MyNetworkData.getWifiPassword());
        assertTrue(MyNetworkData.isEthernetDHCP());
    }

    @Test
    public void ethernetProps()
    {
        MyModel.parseConfigXml(config(
                prop("net.ethernet.ip", "192.168.1.20") +
                prop("net.ethernet.dhcp.enabled", "false") +
                prop("net.ethernet.netmask", "255.255.255.0") +
                prop("net.ethernet.gateway", "192.168.1.1") +
                prop("net.ethernet.domain", "example.test") +
                prop("net.ethernet.dnsServers", "192.168.1.2")));

        assertEquals("192.168.1.20", MyNetworkData.getEthernetIP());
        assertFalse(MyNetworkData.isEthernetDHCP());
        assertEquals("255.255.255.0", MyNetworkData.getEthernetNetmask());
        assertEquals("192.168.1.1", MyNetworkData.getEthernetGateway());
        assertEquals("example.test", MyNetworkData.getEthernetDomain());
        assertEquals("192.168.1.2", MyNetworkData.getEthernetDNS());
    }

    @Test
    public void ethernetDhcpAcceptsOnlyTrue()
    {
        MyModel.parseConfigXml(config(prop("net.ethernet.dhcp.enabled", "yes")));

        assertFalse(MyNetworkData.isEthernetDHCP());
    }

    @Test
    public void volumeNames()
    {
        assertVolume("audio.soundLevel", "40", 40);
        assertVolume("volume", "50%", 50);
        assertVolume("hardware.audioOut.0.masterSoundLevel", "60%", 60);
    }

    @Test
    public void lastVolumeWins()
    {
        MyModel.parseConfigXml(config(prop("volume", "10") + prop("audio.soundLevel", "90")));

        assertEquals(90, MyModel.getVolume());
    }

    @Test
    public void brightnessNames()
    {
        MyModel.parseConfigXml(config(prop("display.brightness", "30%")));
        assertEquals(30, MyModel.getBrightness());

        MyModel.parseConfigXml(config(prop("brightness", "70")));
        assertEquals(70, MyModel.getBrightness());
    }

    @Test
    public void standbyAndReboot()
    {
        MyModel.parseConfigXml(config(
                prop("standby_mode", "deep") +
                prop("schedule.reboot.time", "03:30") +
                prop("schedule.reboot.days", " Monday TUESDAY sunday ")));

        assertEquals("deep", MyModel.getStandbyMode());
        assertEquals("03:30", MyModel.getRebootTime());
        Set<String> expected = new HashSet<>();
        expected.add("monday");
        expected.add("tuesday");
        expected.add("sunday");
        assertEquals(expected, MyModel.getRebootDays());
    }

    /**
     * Current behaviour: the days are split at single blanks only.
     */
    @Test
    public void rebootDaysWithDoubleBlankContainEmptyEntry()
    {
        MyModel.parseConfigXml(config(prop("schedule.reboot.days", "monday  friday")));

        assertTrue(MyModel.getRebootDays().contains(""));
        assertEquals(3, MyModel.getRebootDays().size());
    }

    @Test
    public void unknownPropsAreIgnored()
    {
        MyModel.parseConfigXml(config(prop("info.playerName", "Lobby") + prop("volume", "20")));

        assertEquals(20, MyModel.getVolume());
    }

    @Test
    public void propsOutsideUserPrefAreParsedToo()
    {
        MyModel.parseConfigXml("<configuration><security>" + prop("volume", "15") + "</security></configuration>");

        assertEquals(15, MyModel.getVolume());
    }

    @Test
    public void brokenXmlKeepsValuesParsedBefore()
    {
        MyModel.parseConfigXml("<configuration><userPref>" + prop("volume", "25") + "<prop name=");

        assertEquals(25, MyModel.getVolume());
        assertEquals(100, MyModel.getBrightness());
    }

    @Test
    public void emptyStringDoesNotThrow()
    {
        MyModel.parseConfigXml("");

        assertEquals(100, MyModel.getVolume());
    }

    @Test
    public void nonNumericVolumeKeepsDefault()
    {
        MyModel.parseConfigXml(config(prop("volume", "loud")));

        assertEquals(100, MyModel.getVolume());
    }

    @Test
    public void nonNumericVolumeKeepsValueBefore()
    {
        MyModel.parseConfigXml(config(prop("audio.soundLevel", "40") + prop("volume", "loud")));

        assertEquals(40, MyModel.getVolume());
    }

    @Test
    public void nonNumericBrightnessKeepsDefault()
    {
        MyModel.parseConfigXml(config(prop("display.brightness", "bright%")));

        assertEquals(100, MyModel.getBrightness());
    }

    @Test
    public void volumeAndBrightnessWithoutValueKeepDefault()
    {
        MyModel.parseConfigXml(config("<prop name=\"volume\"/><prop name=\"brightness\"/>"));

        assertEquals(100, MyModel.getVolume());
        assertEquals(100, MyModel.getBrightness());
    }

    @Test
    public void invalidVolumeDoesNotStopFollowingProps()
    {
        MyModel.parseConfigXml(config(prop("volume", "loud") + prop("brightness", "30") + prop("net.wifi.ssid", "THESSID")));

        assertEquals(100, MyModel.getVolume());
        assertEquals(30, MyModel.getBrightness());
        assertEquals("THESSID", MyNetworkData.getWifiSSID());
    }

    @Test
    public void propWithoutNameIsIgnored()
    {
        MyModel.parseConfigXml(config("<prop value=\"1\"/>" + prop("volume", "20")));

        assertEquals(20, MyModel.getVolume());
    }

    @Test
    public void serverUrlWithEscapedAmpersand()
    {
        MyModel.parseConfigXml(config(prop("content.serverUrl", "https://cms.test/index.smil?a=1&amp;b=2")));

        assertEquals("https://cms.test/index.smil?a=1&b=2", MyMainConfiguration.getSmilIndex());
    }

    @Test
    public void storeConfigXmlForPlayerWritesEscapedServerUrl() throws IOException
    {
        MyMainConfiguration.storeSmilIndex("https://cms.test/index.smil?a=1&b=2");

        MyModel.storeConfigXmlForPlayer();

        String written = read(configForPlayer());
        assertTrue(written.contains("<prop name=\"content.bootFromServer\" value=\"true\"/>"));
        assertTrue(written.contains("<prop name=\"content.serverUrl\" value=\"https://cms.test/index.smil?a=1&amp;b=2\"/>"));
    }

    @Test
    public void storeConfigXmlForPlayerDoesNotEscapeTwice() throws IOException
    {
        MyMainConfiguration.storeSmilIndex("https://cms.test/index.smil?a=1&amp;b=2");

        MyModel.storeConfigXmlForPlayer();

        assertTrue(read(configForPlayer()).contains("value=\"https://cms.test/index.smil?a=1&amp;b=2\""));
    }

    @Test
    public void storeConfigXmlForPlayerOverwritesExistingFile() throws IOException
    {
        MyMainConfiguration.storeSmilIndex("https://first.test/");
        MyModel.storeConfigXmlForPlayer();
        MyMainConfiguration.storeSmilIndex("https://second.test/");

        MyModel.storeConfigXmlForPlayer();

        String written = read(configForPlayer());
        assertTrue(written.contains("https://second.test/"));
        assertFalse(written.contains("https://first.test/"));
    }

    /**
     * What the launcher writes for the player, it must read again.
     */
    @Test
    public void storedConfigCanBeParsedAgain() throws IOException
    {
        MyMainConfiguration.storeSmilIndex("https://cms.test/index.smil?a=1&b=2");
        MyModel.storeConfigXmlForPlayer();
        MyMainConfiguration.storeSmilIndex("overwritten");

        MyModel.parseConfigXml(MyModel.readConfigXml(configForPlayer()));

        assertEquals("https://cms.test/index.smil?a=1&b=2", MyMainConfiguration.getSmilIndex());
    }

    private void assertVolume(String name, String value, int expected)
    {
        MyModel.parseConfigXml(config(prop(name, value)));
        assertEquals(name, expected, MyModel.getVolume());
    }

    private File configForPlayer()
    {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "config.xml");
    }

    private static String config(String props)
    {
        return "<configuration><userPref>" + props + "</userPref></configuration>";
    }

    private static String prop(String name, String value)
    {
        return "<prop name=\"" + name + "\" value=\"" + value + "\"/>";
    }

    private static String read(File file) throws IOException
    {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
