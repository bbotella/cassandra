package org.apache.cassandra.profiler;

public class AsyncProfilerUnsafe extends AsyncProfiler
{
    public void execute(String command)
    {
        getService().execute(command);
    }
}
