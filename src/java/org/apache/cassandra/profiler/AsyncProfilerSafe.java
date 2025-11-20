package org.apache.cassandra.profiler;

public class AsyncProfilerSafe extends AsyncProfiler
{
    public void execute(String command)
    {
        throw new SecurityException(String.format("Execute commands are not permitted " +
                                                  "with this MBean. Please use unsafe MBean" +
                                                  "if they are needed. Command: %s", command));
    }
}
