package com.sagiadinos.garlic.launcher.configuration;

import android.content.Context;

import java.io.File;
import java.io.IOException;

import com.sagiadinos.garlic.launcher.helper.ShellExecute;

/**
 * Applies the content of a configuration.xml: player name, content url, volume and brightness.
 * Used for the config of the player (SettingsProvider) and from USB (ConfigXMLReceiver).
 *
 * WiFi and ethernet are only parsed and returned, not applied: a wrong remote network config
 * locks the device out and the password would be in clear text on the shared storage.
 * So only the USB way applies them.
 */
public class ConfigApplier
{
    private final Context MyContext;
    private final MainConfiguration MyMainConfiguration;

    public ConfigApplier(Context context, MainConfiguration mainConfiguration)
    {
        MyContext           = context;
        MyMainConfiguration = mainConfiguration;
    }

    /**
     * @return the parsed network data, not applied
     */
    public NetworkData apply(String xml)
    {
        NetworkData MyNetworkData = new NetworkData();
        apply(new ConfigXMLModel(MyNetworkData, MyMainConfiguration), xml);
        return MyNetworkData;
    }

    /**
     * @return the parsed network data, not applied
     */
    public NetworkData applyFile(File config_xml) throws IOException
    {
        NetworkData MyNetworkData       = new NetworkData();
        ConfigXMLModel MyConfigXMLModel = new ConfigXMLModel(MyNetworkData, MyMainConfiguration);
        apply(MyConfigXMLModel, MyConfigXMLModel.readConfigXml(config_xml));
        return MyNetworkData;
    }

    private void apply(ConfigXMLModel MyConfigXMLModel, String xml)
    {
        MyConfigXMLModel.parseConfigXml(xml); // stores content url and player name

        // only what the content contains, otherwise a config without volume would set 100 %
        SystemSettings MySystemSettings = new SystemSettings(MyContext, MyMainConfiguration, new ShellExecute(Runtime.getRuntime()));
        if (MyConfigXMLModel.hasVolume())
        {
            MyMainConfiguration.storeVolume(MyConfigXMLModel.getVolume());
            MySystemSettings.configMasterVolume(MyConfigXMLModel.getVolume());
        }
        if (MyConfigXMLModel.hasBrightness())
        {
            MyMainConfiguration.storeBrightness(MyConfigXMLModel.getBrightness());
            MySystemSettings.configBrightness(MyConfigXMLModel.getBrightness());
        }
    }
}
