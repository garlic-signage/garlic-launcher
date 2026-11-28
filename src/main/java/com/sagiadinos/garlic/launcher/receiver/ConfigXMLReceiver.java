package com.sagiadinos.garlic.launcher.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.sagiadinos.garlic.launcher.configuration.ConfigApplier;
import com.sagiadinos.garlic.launcher.configuration.MainConfiguration;
import com.sagiadinos.garlic.launcher.configuration.NetworkData;
import com.sagiadinos.garlic.launcher.configuration.SharedPreferencesModel;
import com.sagiadinos.garlic.launcher.helper.TaskExecutionReport;
import com.sagiadinos.garlic.launcher.helper.WiFi;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

public class ConfigXMLReceiver extends BroadcastReceiver
{
    Context MyContext;
    NetworkData MyNetWorkData = null;
    MainConfiguration MyMainConfiguration;

    @Override
    public void onReceive(Context context, Intent intent)
    {
        if (intent == null || intent.getAction() == null)
        {
            return;
        }

        MyContext           = context;
        MyMainConfiguration = new MainConfiguration(new SharedPreferencesModel(MyContext));


        String task_id = "";
        if (intent.getStringExtra("task_id") != null)
        {
            task_id        = intent.getStringExtra("task_id");
        }

        String config_path = intent.getStringExtra("config_path");
        if (config_path == null)
        {
            Log.w("ConfigXMLReceiver", "config_path missing");
            TaskExecutionReport.append(task_id, "aborted");
            return;
        }
        File config_xml = new File(config_path);

        if (parseConfigFile(config_xml))
        {
            Intent i = new Intent("com.sagiadinos.garlic.launcher.receiver.CommandReceiver");
            i.putExtra("command", "reboot");
            i.putExtra("task_id", task_id);
            MyContext.sendBroadcast(i);
        }
        else
        {
            TaskExecutionReport.append(task_id, "aborted");

        }
    }

    /**
     * Name, content url, volume and brightness like the config of the player,
     * WiFi only from USB.
     */
    private boolean parseConfigFile(File file)
    {
        boolean ret = false;
        try
        {
            MyNetWorkData = new ConfigApplier(MyContext, MyMainConfiguration).applyFile(file);

            if (!MyNetWorkData.getWifiSSID().isEmpty())
            {
                configWiFi();
            }

            ret = true;
        }
        catch (IOException e)
        {
            Log.e("Usb config_xml", Objects.requireNonNull(e.getMessage()));
        }
        return ret;
    }

    // WifiConfiguration is deprecated, see WiFi
    @SuppressWarnings("deprecation")
    private void configWiFi()
    {
        WiFi MyWiFi = new WiFi((WifiManager) MyContext.getSystemService(Context.WIFI_SERVICE), new WifiConfiguration());
        MyWiFi.prepareConnection(MyNetWorkData);
        MyWiFi.connectToNetwork();
    }

}
