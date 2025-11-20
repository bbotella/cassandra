package org.apache.cassandra.profiler;

public interface AsyncProfilerMBean
{
    void start(String event, String outputFormat, int timeout, String outputPath);

    void stop();

    void execute(String command);

    boolean isAvailable();
}
