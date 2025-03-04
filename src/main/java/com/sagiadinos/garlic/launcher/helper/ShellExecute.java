package com.sagiadinos.garlic.launcher.helper;


import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class ShellExecute
{
    Process MyProcess;
    Runtime MyRuntime;
    String error_text  = "";
    String command     = "";

    public ShellExecute(Runtime myRuntime)
    {
        MyRuntime = myRuntime;
    }

    public boolean executeAsRoot(String cmd)
    {
        command = cmd;
        return execute("su\n");
    }

    public boolean executeAsUser(String cmd)
    {
        command = cmd;
        return execute( "sh\n");
    }

    public String getErrorText()
    {
        if (MyProcess == null)
            return error_text;
        else
            return error_text + "\n" + formatOutput(MyProcess.getErrorStream());
    }

    public String getOutputText()
    {
        if (MyProcess != null)
            return formatOutput(MyProcess.getInputStream());
        else
            return "No OutputText cause MyProcess is Null";
    }

    private boolean execute(String cmd)
    {
        boolean succeed = false;
        try
        {
            MyProcess = MyRuntime.exec(cmd);
            succeed   = (executeCommand() == 0);

            // Lese die Standardausgabe des Befehls
            BufferedReader stdInput = new BufferedReader(new InputStreamReader(MyProcess.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = stdInput.readLine()) != null) {
                output.append(line).append("\n");
            }

            // Lese die Fehlerausgabe des Befehls
            BufferedReader stdError = new BufferedReader(new InputStreamReader(MyProcess.getErrorStream()));
            StringBuilder error = new StringBuilder();
            while ((line = stdError.readLine()) != null) {
                error.append(line).append("\n");
            }

            int exitCode = MyProcess.waitFor();
            if (exitCode == 0) {
                System.out.println("Output: " + output.toString());
            } else {
                System.out.println("Error: " + error.toString());
            }
        }
        catch (IOException | InterruptedException e)
        {
            error_text = e.getMessage();
        }
        return succeed;
    }

    private int executeCommand() throws IOException, InterruptedException
    {
        DataOutputStream MyDataOutputStream = new DataOutputStream(MyProcess.getOutputStream());
        MyDataOutputStream.writeBytes(command + "\n");
        MyDataOutputStream.writeBytes("exit\n");
        MyDataOutputStream.flush();
        return MyProcess.waitFor();
    }
    private String formatOutput(InputStream in)
    {
        String s;
        StringBuilder ret     = new StringBuilder();
        BufferedReader buffer = new BufferedReader(new InputStreamReader(in));
        try
        {
            while ((s = buffer.readLine()) != null)
            {
                ret.append(s).append("\n");
            }
        }
        catch (IOException e)
        {
            ret.append(e.getMessage()).append("\n");
        }
        return ret.toString();
    }
}
