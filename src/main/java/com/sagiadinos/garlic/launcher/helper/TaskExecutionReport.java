package com.sagiadinos.garlic.launcher.helper;

import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class TaskExecutionReport
{
    private static File LogFile = null;

    public TaskExecutionReport(String path)
    {
        try
        {
            createDir(path);
            LogFile = createFile(path + "/task_execution_log.xml");
        }
        catch (IOException | GarlicLauncherException e)
        {
            Log.wtf("TaskExecutionReport", e.getMessage());
        }
    }

    public static void append(String task_id, String state)
    {
        try
        {
            String text = "<task id=\""+escapeXml(task_id)+"\">" +
                "<lastUpdateTime>"+getCurrentIsoDateTime()+"</lastUpdateTime>" +
                "<state>"+escapeXml(state)+"</state></task>";

            // this can happens when Launcher is updated and class is not constructed
            // then we get a crash
            if (LogFile == null)
                return;

            BufferedWriter buf = new BufferedWriter(new FileWriter(LogFile, true));
            buf.append(text);
            buf.newLine();
            buf.close();
        }
        catch (IOException e)
        {
            Log.wtf("TaskExecutionReport", e.getMessage());
        }
    }

    /**
     * task_id comes from outside (player, broadcast) and must not break
     * the XML. Characters not allowed in XML 1.0 are dropped, line breaks
     * are encoded so that every task stays on one line.
     */
    static String escapeXml(String value)
    {
        if (value == null)
            return "";

        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++)
        {
            char c = value.charAt(i);
            switch (c)
            {
                case '&':  escaped.append("&amp;");  break;
                case '<':  escaped.append("&lt;");   break;
                case '>':  escaped.append("&gt;");   break;
                case '"':  escaped.append("&quot;"); break;
                case '\'': escaped.append("&apos;"); break;
                case '\t': escaped.append("&#9;");   break;
                case '\n': escaped.append("&#10;");  break;
                case '\r': escaped.append("&#13;");  break;
                default:
                    if (isAllowedXmlChar(value, i))
                        escaped.append(c);
                    break;
            }
        }
        return escaped.toString();
    }

    private static boolean isAllowedXmlChar(String value, int i)
    {
        char c = value.charAt(i);
        if (c < 0x20 || c == 0xFFFE || c == 0xFFFF)
            return false;

        // keep only complete surrogate pairs
        if (Character.isHighSurrogate(c))
            return i + 1 < value.length() && Character.isLowSurrogate(value.charAt(i + 1));
        if (Character.isLowSurrogate(c))
            return i > 0 && Character.isHighSurrogate(value.charAt(i - 1));

        return true;
    }

    private static String getCurrentIsoDateTime()
    {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mmXXX", Locale.US);
        df.setTimeZone(TimeZone.getDefault());
        return df.format(new Date());
    }

    private void createDir(String path) throws GarlicLauncherException
    {
        File dir = new File(path);
        if (!dir.exists() && !dir.mkdirs())
        {
            throw new GarlicLauncherException(path + " could not be created");
        }
    }

    private File createFile(String path) throws GarlicLauncherException, IOException
    {
        File file = new File(path);
        if (!file.exists() && !file.createNewFile())
        {
            throw new GarlicLauncherException(path + " could not be created");
        }
        return file;
    }
}
