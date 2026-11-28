package com.sagiadinos.garlic.launcher.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.sagiadinos.garlic.launcher.helper.CommandExecutor;

/**
 * Legacy way for commands of the player, without caller check.
 * Newer players use call("command") of the SettingsProvider.
 * Will be removed with launcher 2.0.
 */
public class CommandReceiver extends BroadcastReceiver
{
    @Override
    public void onReceive(Context context, Intent intent)
    {
        if (intent == null || intent.getAction() == null)
        {
            return;
        }

        String command = intent.getStringExtra("command");
        if (command == null)
            return;

        new CommandExecutor(context).execute(command, intent.getStringExtra("task_id"), intent.getStringExtra("seconds_to_wakeup"));
    }
}
