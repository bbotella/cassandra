package org.apache.cassandra.profiler;
import org.apache.cassandra.tools.profiler.AsyncProfilerService;

public abstract class AsyncProfiler implements AsyncProfilerMBean
{
    public static final String MBEAN_NAME = "org.apache.cassandra.profiler:type=AsyncProfiler";
    private final AsyncProfilerService service = new AsyncProfilerService();

    public void start(String event, String outputFormat, int timeout, String outputPath)
    {
        getService().start(event, outputFormat, timeout, outputPath);
    }

    public void stop()
    {
        getService().stop();
    }

    public boolean isAvailable()
    {
        return getService().isAvailable();
    }

    public AsyncProfilerService getService()
    {
        return service;
    }
}
