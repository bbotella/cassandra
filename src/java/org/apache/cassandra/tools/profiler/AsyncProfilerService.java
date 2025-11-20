package org.apache.cassandra.tools.profiler;

import one.profiler.AsyncProfiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static org.apache.cassandra.config.CassandraRelevantProperties.ASYNC_PROFILER_ENABLED;

public class AsyncProfilerService
{
    private static final Logger logger = LoggerFactory.getLogger(AsyncProfilerService.class);

    private static final Set<String> VALID_EVENTS = Set.of("cpu", "alloc", "lock", "wall", "nativemem", "cache-misses");
    private static final Set<String> VALID_FORMATS = Set.of("flat","traces","collapsed","flamegraph","tree","jfr","otlp");
    private static final Character[] INVALID_OUTPUT_FILENAME_CHARS = {'"', '*', '<', '>', '?', '|'};

    private static AsyncProfiler profilerInstance;

    static
    {
        try
        {
            // Let async-profiler automatically extract and load the native library from the JAR
            profilerInstance = AsyncProfiler.getInstance();
        }
        catch (Throwable t)
        {
            System.out.println("async-profiler initialization ERROR");
            t.printStackTrace();
            profilerInstance = null;
        }
    }

    public void start(String event, String outputFormat, int timeout, String outputPath) {
        checkProfilerInstance();
        validateEvent(event);
        validateFormat(outputFormat);
        validateOutputFileName(outputPath);

        try
        {
            String cmd = String.format("start,%s,event=%s,timeout=%s,file=%s",
                                       outputFormat,
                                       event,
                                       timeout,
                                       outputPath);

            profilerInstance.execute(cmd);
            logger.info("Started async-profiler: cmd={}", cmd);
        }
        catch (IOException e)
        {
            logger.error("Failed to start async-profiler", e);
            throw new RuntimeException(e);
        }
    }

    public void stop()
    {
        checkProfilerInstance();
        String cmd = "stop";

        try
        {
            profilerInstance.execute(cmd);
            logger.info("Stopped async-profiler.");
        } catch (IOException e)
        {
            logger.error("Failed to stop async-profiler", e);
            throw new RuntimeException(e);
        }
    }

    public void execute(String command)
    {
        checkProfilerInstance();

        try
        {
            profilerInstance.execute(command);
            logger.info("Executed raw async-profiler command {}", command);
        }
        catch (IOException e)
        {
            logger.error("Failed to execute raw async-profiler command {}", command, e);
            throw new RuntimeException(e);
        }
    }

    public boolean isAvailable()
    {
        return profilerInstance != null;
    }

    private void checkProfilerInstance()
    {
        if (ASYNC_PROFILER_ENABLED.getBoolean() == false)
        {
            throw new IllegalStateException("async-profiler is not enabled.");
        }
        else if (!isAvailable())
        {
            throw new IllegalStateException("async-profiler is not initialized.");
        }
    }

    private void validateEvent(String event)
    {
        if (!Arrays.stream(event.split(",")).filter(s -> !s.isEmpty()).allMatch(VALID_EVENTS::contains))
        {
            throw new IllegalArgumentException(String.format("Event must be one or a combination of %s", VALID_EVENTS.toString()));
        }
    }

    private void validateFormat(String format)
    {
        if (!VALID_FORMATS.contains(format))
        {
            throw new IllegalArgumentException(String.format("Format must be one or a combination of %s", VALID_FORMATS.toString()));
        }
    }

    private void validateOutputFileName(String outputFile)
    {
        if (outputFile == null || outputFile.trim().isEmpty())
        {
            throw new IllegalArgumentException("Output file name must not be null or empty.");
        }
        if (Arrays.stream(INVALID_OUTPUT_FILENAME_CHARS).anyMatch(ch -> outputFile.contains(ch.toString())))
        {
            throw new IllegalArgumentException(String.format("Output file name must not contain any invalid characters %s", INVALID_OUTPUT_FILENAME_CHARS.toString()));
        }
    }
}
