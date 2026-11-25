/*
 garlic-launcher: Android Launcher for the Digital Signage Software garlic-player

 Copyright (C) 2020 Nikolaos Sagiadinos <ns@smil-control.com>
 This file is part of the garlic-launcher source code

 This program is free software: you can redistribute it and/or  modify
 it under the terms of the GNU Affero General Public License, version 3,
 as published by the Free Software Foundation.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package com.sagiadinos.garlic.launcher.configuration;

import android.os.Environment;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class ConfigXMLModel
{
    private NetworkData MyNetworkData;
    private MainConfiguration MyMainConfiguration;
    private int volume = 100;
    private int brightness = 100;
    private boolean has_volume = false;
    private boolean has_brightness = false;
    private String standby_mode = "";
    private Set<String> reboot_days;
    private String reboot_time = "";

    public ConfigXMLModel(NetworkData myNetworkData, MainConfiguration myMainConfiguration)
    {
        this.MyNetworkData        = myNetworkData;
        this.MyMainConfiguration = myMainConfiguration;
    }

    public int getVolume()
    {
        return volume;
    }

    public int getBrightness()
    {
        return brightness;
    }

    /** true when the file contains a valid volume, otherwise getVolume() is the default */
    public boolean hasVolume()
    {
        return has_volume;
    }

    /** true when the file contains a valid brightness, otherwise getBrightness() is the default */
    public boolean hasBrightness()
    {
        return has_brightness;
    }

    public String getStandbyMode()
    {
        return standby_mode;
    }

    public Set<String> getRebootDays()
    {

        return reboot_days;
    }

    public String getRebootTime()
    {
        return reboot_time;
    }

    public String readConfigXml(File config_xml) throws IOException
    {
        StringBuilder stringBuilder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(config_xml)))
        {
            char[] buffer = new char[1024];
            int    read;
            // only the chars read, the last block is not full
            while ((read = reader.read(buffer)) != -1)
            {
                stringBuilder.append(buffer, 0, read);
            }
        }
        return stringBuilder.toString();
    }

    public void parseConfigXml(String xml)
    {
        try
        {
            XmlPullParser xpp = createXmlPullParser();
            xpp.setInput(new StringReader(xml));

            int eventType = xpp.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT)
            {
                if (eventType == XmlPullParser.START_TAG && xpp.getName().equals("prop"))
                {
                    parseProp(xpp);
                }
                eventType = xpp.next();
            }
        }
        catch (XmlPullParserException | IOException e)
        {
            Log.e("config_xml", Objects.requireNonNull(e.getMessage()));
        }

    }

    public void copy(File src, File dst) throws IOException
    {
        try (InputStream in = new FileInputStream(src))
        {
            try (OutputStream out = new FileOutputStream(dst))
            {
                // Transfer bytes from in to out
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0)
                {
                    out.write(buf, 0, len);
                }
            }
        }
    }

    public void storeConfigXmlForPlayer()
    {
        try
        {
            File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/config.xml");

            if (!file.exists() && !file.createNewFile())
            {
                throw new IOException("File could not be created");
            }
            FileWriter writer = new FileWriter(file);
            writer.write(generateConfigXML());
            writer.flush();
            writer.close();
        }
        catch (IOException e)
        {
            Log.e("config_xml", Objects.requireNonNull(e.getMessage()));
        }
    }

    private void parseProp(XmlPullParser xpp)
    {
        String name = xpp.getAttributeValue(null, "name");
        if (name == null)
        {
            // like an unsupported attribute: ignore it
            Log.w("config_xml", "prop without name ignored");
            return;
        }

        String value = xpp.getAttributeValue(null, "value");
        // a prop without value is ignored and keeps the current value;
        // only the DHCP flag keeps its old meaning: no value means false
        if (value == null && !name.equals("net.ethernet.dhcp.enabled"))
        {
            Log.w("config_xml", name + " without value ignored");
            return;
        }

        switch (name)
        {
            case "net.wifi.ssid":
                MyNetworkData.setWifiSSID(value);
                break;
            case "net.wifi.authentication":
                MyNetworkData.setWifiAuthentication(value);
                break;
            case "net.wifi.encryption":
                MyNetworkData.setWifiEncryption(value);
                break;
            case "net.wifi.password":
                MyNetworkData.setWifiPassword(value);
                break;
            case "net.ethernet.ip":
                MyNetworkData.setEthernetIP(value);
                break;
            case "net.ethernet.dhcp.enabled":
                MyNetworkData.setEthernetDHCP(Boolean.parseBoolean(value));
                break;
            case "net.ethernet.netmask":
                MyNetworkData.setEthernetNetmask(value);
                break;
            case "net.ethernet.gateway":
                MyNetworkData.setEthernetGateway(value);
                break;
            case "net.ethernet.domain":
                MyNetworkData.setEthernetDomain(value);
                break;
            case "audio.soundLevel":
            case "volume":
            case "hardware.audioOut.0.masterSoundLevel":
                Integer parsed_volume = parsePercent(name, value);
                if (parsed_volume != null)
                {
                    volume     = parsed_volume;
                    has_volume = true;
                }
                break;
            case "display.brightness":
            case "brightness":
                Integer parsed_brightness = parsePercent(name, value);
                if (parsed_brightness != null)
                {
                    brightness     = parsed_brightness;
                    has_brightness = true;
                }
                break;
            case "standby_mode":
                standby_mode = value;
                break;
            case "schedule.reboot.time":
                reboot_time = value;
                break;
            case "schedule.reboot.days":
                String[] tmp = value.toLowerCase().trim().split(" ");
                reboot_days = new HashSet<>();
                reboot_days.addAll(Arrays.asList(tmp));
                break;
            case "net.ethernet.dnsServers":
                MyNetworkData.setEthernetDNS(value);
                break;
            case "content.serverUrl":
                MyMainConfiguration.storeSmilIndex(value);
                break;
            default:
                // do nothing on unsupported attributes
                break;
        }
    }

    /**
     * "50" or "50%". Returns null for a not numeric value, which is ignored.
     */
    private Integer parsePercent(String name, String value)
    {
        try
        {
            return Integer.parseInt(value.replaceAll("%", ""));
        }
        catch (NumberFormatException e)
        {
            Log.w("config_xml", name + " with invalid value ignored: " + value);
            return null;
        }
    }

    private XmlPullParser createXmlPullParser() throws XmlPullParserException
    {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newPullParser();
    }

    private String generateConfigXML()
    {
        String smil_index_url =  MyMainConfiguration.getSmilIndex().replace("&amp;", "&"); // make sure that there are no ampersands setted
        return "<configuration>\n" +
                "\t<userPref>\n" +
                "\t\t<prop name=\"content.bootFromServer\" value=\"true\"/>\n" +
                "\t\t<prop name=\"content.serverUrl\" value=\""+  smil_index_url.replaceAll("&", "&amp;") +"\"/>\n" +
                "\t</userPref>\n" +
                "</configuration>";
    }

}
