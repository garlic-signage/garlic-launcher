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
    // the streams can be read only once, so execute() keeps what it read
    String output_text = "";
    String stderr_text = "";

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
            return error_text + "\n" + stderr_text;
    }

    public String getOutputText()
    {
        if (MyProcess != null)
            return output_text;
        else
            return "No OutputText cause MyProcess is Null";
    }

    /**
     * stdout and stderr are read while the process runs, stderr in its own
     * thread. Otherwise a process with more output than the pipe buffer
     * blocks, and waitFor() never returns.
     */
    private boolean execute(String cmd)
    {
        boolean succeed = false;
        error_text      = "";
        output_text     = "";
        stderr_text     = "";
        try
        {
            MyProcess = MyRuntime.exec(cmd);
            writeCommand();

            StreamCollector stderr = new StreamCollector(MyProcess.getErrorStream());
            Thread stderr_thread   = new Thread(stderr, "ShellExecute-stderr");
            stderr_thread.start();

            output_text = readAll(MyProcess.getInputStream());
            stderr_thread.join();
            stderr_text = stderr.getText();

            int exitCode = MyProcess.waitFor();
            succeed      = (exitCode == 0);
            if (succeed) {
                System.out.println("Output: " + output_text);
            } else {
                System.out.println("Error: " + stderr_text);
            }
        }
        catch (IOException | InterruptedException e)
        {
            error_text = e.getMessage();
        }
        return succeed;
    }

    private void writeCommand() throws IOException
    {
        DataOutputStream MyDataOutputStream = new DataOutputStream(MyProcess.getOutputStream());
        MyDataOutputStream.writeBytes(command + "\n");
        MyDataOutputStream.writeBytes("exit\n");
        MyDataOutputStream.flush();
        MyDataOutputStream.close();
    }

    private static String readAll(InputStream in) throws IOException
    {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        StringBuilder text    = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null)
        {
            text.append(line).append("\n");
        }
        return text.toString();
    }

    private static class StreamCollector implements Runnable
    {
        private final InputStream in;
        private volatile String   text = "";

        StreamCollector(InputStream in)
        {
            this.in = in;
        }

        @Override
        public void run()
        {
            try
            {
                text = readAll(in);
            }
            catch (IOException e)
            {
                text = e.getMessage() + "\n";
            }
        }

        String getText()
        {
            return text;
        }
    }
}
