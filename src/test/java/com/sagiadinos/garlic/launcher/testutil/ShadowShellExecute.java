package com.sagiadinos.garlic.launcher.testutil;

import com.sagiadinos.garlic.launcher.helper.ShellExecute;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Resetter;

import java.util.ArrayList;
import java.util.List;

/**
 * Robolectric tests must never run "su" on the machine that runs the tests.
 * Records the commands instead and answers with a configurable result.
 */
@Implements(ShellExecute.class)
public class ShadowShellExecute
{
    private static final List<String> commands = new ArrayList<>();
    private static boolean result              = false;

    public static List<String> getCommands()
    {
        return commands;
    }

    public static void setResult(boolean value)
    {
        result = value;
    }

    @Implementation
    protected boolean executeAsRoot(String cmd)
    {
        commands.add(cmd);
        return result;
    }

    @Implementation
    protected String getErrorText()
    {
        return "";
    }

    @Resetter
    public static void reset()
    {
        commands.clear();
        result = false;
    }
}
