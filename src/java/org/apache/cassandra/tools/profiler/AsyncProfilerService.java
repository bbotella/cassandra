/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.cassandra.tools.profiler;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import one.profiler.AsyncProfiler;
import org.apache.cassandra.config.CassandraRelevantProperties;
import org.apache.cassandra.exceptions.ConfigurationException;
import org.apache.cassandra.io.util.File;

import static java.lang.String.format;
import static org.apache.cassandra.config.CassandraRelevantProperties.ASYNC_PROFILER_ENABLED;
import static org.apache.cassandra.config.CassandraRelevantProperties.ASYNC_PROFILER_LOG_DIR;

public class AsyncProfilerService
{
    private static final Logger logger = LoggerFactory.getLogger(AsyncProfilerService.class);

    private static final EnumSet<AsyncProfilerEvent> VALID_EVENTS = EnumSet.allOf(AsyncProfilerEvent.class);
    private static final EnumSet<AsyncProfilerFormat> VALID_FORMATS = EnumSet.allOf(AsyncProfilerFormat.class);
    private static final Pattern VALID_FILENAME_REGEX_PATTERN = Pattern.compile("^[a-zA-Z0-9-]*\\.?[a-zA-Z0-9-]*$");

    public enum AsyncProfilerEvent
    {
        cpu("cpu"),
        alloc("alloc"),
        lock("lock"),
        wall("wall"),
        nativemem("nativemem"),
        cache_misses("cache-misses");

        private final String name;

        AsyncProfilerEvent(String name)
        {
            this.name = name;
        }

        public String getEvent()
        {
            return name;
        }

        public static String parseEvents(String rawString)
        {
            if (rawString == null || rawString.isBlank())
                throw new IllegalArgumentException("Event can not be null nor blank string.");

            try
            {
                List<String> processedEvents = new ArrayList<>();
                for (String rawEvent : rawString.split(","))
                    processedEvents.add(AsyncProfilerEvent.valueOf(rawEvent).getEvent());

                return String.join(",", processedEvents);
            }
            catch (IllegalArgumentException ex)
            {
                throw new IllegalArgumentException(format("Event must be one or a combination of %s", VALID_EVENTS));
            }
        }
    }

    public enum AsyncProfilerFormat
    {
        flat, traces, collapsed, flamegraph, tree, jfr, otlp;

        public static String parseFormat(String rawFormat)
        {
            if (rawFormat == null || rawFormat.isBlank())
                throw new IllegalArgumentException("Event can not be null nor blank string.");

            try
            {
                return AsyncProfilerFormat.valueOf(rawFormat).name();
            }
            catch (IllegalArgumentException ex)
            {
                throw new IllegalArgumentException(format("Format must be one of %s", VALID_FORMATS));
            }
        }
    }

    private AsyncProfiler profilerInstance;

    private final String logDir;

    /**
     * @throws ConfigurationException in case it is not possible to configure directory for logs.
     */
    public AsyncProfilerService() throws ConfigurationException
    {
        String logDirPropertyValue = ASYNC_PROFILER_LOG_DIR.getString();
        if (logDirPropertyValue == null)
        {
            String globalLogDir = CassandraRelevantProperties.LOG_DIR.getString();
            logDir = Paths.get(globalLogDir, "async-profiler").toString();
        }
        else
        {
            logDir = logDirPropertyValue;
        }

        try
        {
            new File(logDir).createDirectoriesIfNotExists();
        }
        catch (Throwable t)
        {
            throw new ConfigurationException("Unable to create directory " + logDir);
        }
    }

    public synchronized void enable()
    {
        if (isEnabled())
            return;

        ASYNC_PROFILER_ENABLED.setBoolean(true);
        getProfilerInstance();
    }

    public synchronized void disable()
    {
        if (!isEnabled())
            return;

        ASYNC_PROFILER_ENABLED.setBoolean(false);
        profilerInstance = null;
    }

    public synchronized AsyncProfiler getProfilerInstance()
    {
        if (!ASYNC_PROFILER_ENABLED.getBoolean())
            throw new IllegalStateException("Async-Profiler is not enabled.");

        if (profilerInstance == null)
        {
            try
            {
                profilerInstance = one.profiler.AsyncProfiler.getInstance();
            }
            catch (Throwable t)
            {
                throw new IllegalStateException("Unable to get an instance of Async-Profiler", t);
            }
        }

        return profilerInstance;
    }

    public void start(String events, String outputFormat, int timeout, String outputFileName)
    {
        try
        {
            String cmd = format("start,%s,event=%s,timeout=%s,file=%s",
                                AsyncProfilerFormat.parseFormat(outputFormat),
                                AsyncProfilerEvent.parseEvents(events),
                                validateTimeout(timeout),
                                Path.of(logDir, validateOutputFileName(outputFileName)));

            getProfilerInstance().execute(cmd);
            logger.info("Started Async-Profiler: cmd={}", cmd);
        }
        catch (IOException e)
        {
            logger.error("Failed to start Async-Profiler", e);
            throw new RuntimeException(e);
        }
    }

    public void stop(String outputFileName)
    {
        String cmd = "stop,file=" + Path.of(logDir, validateOutputFileName(outputFileName));

        try
        {
            getProfilerInstance().execute(cmd);
            logger.info("Stopped Async-Profiler: cmd={}", cmd);
        } catch (IOException e)
        {
            logger.error("Failed to stop Async-Profiler", e);
            throw new RuntimeException(e);
        }
    }

    public void execute(String command)
    {
        try
        {
            getProfilerInstance().execute(validateCommand(command));
            logger.info("Executed raw Async-Profiler command {}", command);
        }
        catch (IOException e)
        {
            logger.error("Failed to execute raw Async-Profiler command {}", command, e);
            throw new RuntimeException(e);
        }
    }

    public boolean isEnabled()
    {
        return profilerInstance != null;
    }

    public static String validateOutputFileName(String outputFile)
    {
        if (outputFile == null || outputFile.trim().isEmpty())
            throw new IllegalArgumentException("Output file name must not be null or empty.");

        if (!VALID_FILENAME_REGEX_PATTERN.matcher(outputFile).matches())
            throw new IllegalArgumentException(format("Output file name must match pattern %s", VALID_FILENAME_REGEX_PATTERN));

        return outputFile;
    }

    public static String validateCommand(String command)
    {
        if (command == null || command.isBlank())
            throw new IllegalArgumentException("Command can not be null or blank string");

        return command;
    }

    public static int validateTimeout(int timeout)
    {
        if (timeout <= 0)
            throw new IllegalArgumentException("Timeout can not be negative or zero.");

        return timeout;
    }
}
