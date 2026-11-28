package com.sagiadinos.garlic.launcher.configuration;

import android.content.Context;

import com.sagiadinos.garlic.launcher.helper.ShellExecute;

/**
 * Applies the content of a configuration.xml sent by the player via the SettingsProvider:
 * player name, content url, volume and brightness.
 * WiFi and ethernet are ignored on purpose: a wrong remote network config locks the device
 * out and the password would be in clear text on the shared storage. They stay USB only.
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

    public void apply(String xml)
    {
        // network data is parsed but not applied, see class comment
        ConfigXMLModel MyConfigXMLModel = new ConfigXMLModel(new NetworkData(), MyMainConfiguration);
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
