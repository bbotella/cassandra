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

package org.apache.cassandra.service;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Pattern;

import javax.management.StandardMBean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import one.profiler.AsyncProfiler;
import org.apache.cassandra.config.CassandraRelevantProperties;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.config.DurationSpec;
import org.apache.cassandra.exceptions.ConfigurationException;
import org.apache.cassandra.io.util.File;
import org.apache.cassandra.profiler.AsyncProfilerMBean;
import org.apache.cassandra.profiler.AsyncProfilerSafe;
import org.apache.cassandra.profiler.AsyncProfilerUnsafe;
import org.apache.cassandra.utils.MBeanWrapper;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.apache.cassandra.config.CassandraRelevantProperties.ASYNC_PROFILER_ENABLED;
import static org.apache.cassandra.config.CassandraRelevantProperties.ASYNC_PROFILER_LOG_DIR;
import static org.apache.cassandra.config.CassandraRelevantProperties.ASYNC_PROFILER_UNSAFE_MODE;

public class AsyncProfilerService implements AsyncProfilerMBean
{
    private static final Logger logger = LoggerFactory.getLogger(AsyncProfilerService.class);

    private static final EnumSet<AsyncProfilerEvent> VALID_EVENTS = EnumSet.allOf(AsyncProfilerEvent.class);
    private static final EnumSet<AsyncProfilerFormat> VALID_FORMATS = EnumSet.allOf(AsyncProfilerFormat.class);
    private static final Pattern VALID_FILENAME_REGEX_PATTERN = Pattern.compile("^[a-zA-Z0-9-]*\\.?[a-zA-Z0-9-]*$");
    private static final int MAX_SAFE_PROFILING_DURATION = 43200; // 12 hours

    private static AsyncProfilerService instance;

    public static synchronized AsyncProfilerService instance()
    {
        if (AsyncProfilerService.instance == null)
        {
            try
            {
                AsyncProfilerService.instance = ASYNC_PROFILER_UNSAFE_MODE.getBoolean() ? new AsyncProfilerUnsafe() : new AsyncProfilerSafe();

                // register mbean first, before initialisation, which might fail (e.g. profiler functionality is disabled)
                MBeanWrapper.instance.registerMBean(new StandardMBean(AsyncProfilerService.instance, AsyncProfilerMBean.class),
                                                    AsyncProfilerService.MBEAN_NAME,
                                                    MBeanWrapper.OnException.LOG);

                instance.maybeInitialize();
            }
            catch (AsyncProfilerService.AsyncProfilerNotEnabled ex)
            {
                // Ignore to allow methods that do not require the profiler to be enabled such as list, fetch, purge
            }
            catch (Throwable t)
            {
                throw new RuntimeException(t);
            }
        }

        return AsyncProfilerService.instance;
    }

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
        flat, traces, collapsed, flamegraph, tree, jfr;

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

    private volatile AsyncProfiler profilerInstance;

    private String logDir;

    @Override
    public synchronized void enable()
    {
        if (isEnabled())
            return;

        ASYNC_PROFILER_ENABLED.setBoolean(true);
        maybeInitialize();
    }

    @Override
    public synchronized void disable()
    {
        if (!isEnabled())
            return;

        if (isRunning())
            stop(null);

        ASYNC_PROFILER_ENABLED.setBoolean(false);
        profilerInstance = null;
    }

    public synchronized AsyncProfiler maybeInitialize()
    {
        if (!ASYNC_PROFILER_ENABLED.getBoolean())
            throw new AsyncProfilerNotEnabled("Async-Profiler is not enabled.");

        // if somebody removes dir while a node runs, just recreate it
        createLogDir();

        if (profilerInstance == null)
        {
            try
            {
                profilerInstance = one.profiler.AsyncProfiler.getInstance();
            }
            catch (ConfigurationException ex)
            {
                throw ex;
            }
            catch (Throwable t)
            {
                throw new IllegalStateException("Unable to get an instance of Async-Profiler", t);
            }
        }

        return profilerInstance;
    }

    @Override
    public synchronized boolean start(String events, String outputFormat, String duration, String outputFileName)
    {
        if (isRunning())
            return false;

        try
        {
            String cmd = format("start,%s,event=%s,timeout=%s,file=%s",
                                AsyncProfilerFormat.parseFormat(outputFormat),
                                AsyncProfilerEvent.parseEvents(events),
                                parseDuration(duration),
                                new File(logDir, validateOutputFileName(outputFileName)));

            String result = maybeInitialize().execute(cmd);
            logger.debug("Started Async-Profiler: result={}, cmd={}", result, cmd);
            return true;
        }
        catch (IllegalStateException | IllegalArgumentException ex)
        {
            throw ex;
        }
        catch (Throwable t)
        {
            logger.error("Failed to start Async-Profiler", t);
            return false;
        }
    }

    @Override
    public synchronized boolean stop(String outputFileName)
    {
        if (!isRunning())
            return false;

        try
        {
            String cmd = "stop";
            if (outputFileName != null)
            {
                File outputFile = new File(logDir, validateOutputFileName(outputFileName));
                cmd += ",file=" + outputFile.absolutePath();
            }

            String result = maybeInitialize().execute(cmd);
            logger.debug("Stopped Async-Profiler: result={}, cmd={}", result, cmd);
            return true;
        }
        catch (IllegalStateException | IllegalArgumentException e)
        {
            throw e;
        }
        catch (Throwable e)
        {
            logger.error("Failed to stop Async-Profiler", e);
            return false;
        }
    }

    @Override
    public String execute(String command)
    {
        try
        {
            String result = maybeInitialize().execute(validateCommand(command));
            logger.debug("Executed raw command in Async-Profiler: result={}, cmd={}", result, command);
            return result;
        }
        catch (Throwable e)
        {
            logger.error("Failed to execute raw Async-Profiler command {}", command, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<String> list()
    {
        try
        {
            createLogDir();
            return Arrays.stream(new File(logDir).list()).map(File::name).sorted().collect(toList());
        }
        catch (Throwable t)
        {
            return List.of();
        }
    }

    @Override
    public byte[] fetch(String resultFile)
    {
        try
        {
            createLogDir();
            return Files.readAllBytes(new File(logDir, resultFile).toPath());
        }
        catch (Throwable t)
        {
            logger.error("Result file " + resultFile + " not found or error occurred while returning it.", t);
            throw new RuntimeException(t);
        }
    }

    @Override
    public void purge()
    {
        createLogDir();
        new File(logDir).deleteRecursive();
    }

    @Override
    public String status()
    {
        try
        {
            return maybeInitialize().execute("status");
        }
        catch (Throwable t)
        {
            logger.error("There was an error trying to execute status", t);
            return t.getMessage();
        }
    }

    @Override
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

    /**
     * @param duration duration of profiling
     * @return converted string representation of duration to seconds
     */
    public static int parseDuration(String duration)
    {
        int durationSeconds = new DurationSpec.IntSecondsBound(duration).toSeconds();
        if (durationSeconds > MAX_SAFE_PROFILING_DURATION)
            throw new IllegalArgumentException(format("Max profiling duration is %s seconds. If you need longer profiling, use execute command instead",
                                                      MAX_SAFE_PROFILING_DURATION));
        return new DurationSpec.IntSecondsBound(duration).toSeconds();
    }

    /**
     * @throws ConfigurationException in case it is not possible to configure directory for logs.
     */
    private void createLogDir() throws ConfigurationException
    {
        String logDirPropertyValue = ASYNC_PROFILER_LOG_DIR.getString();
        if (logDirPropertyValue == null)
        {
            String globalLogDir = CassandraRelevantProperties.LOG_DIR.getString();
            logDir = File.getPath(globalLogDir, "async-profiler").toAbsolutePath().toString();
        }
        else
        {
            logDir = logDirPropertyValue;
        }

        String dir = new File(logDir).toAbsolute().toString();

        if ((DatabaseDescriptor.getCommitLogLocation() != null && dir.startsWith(DatabaseDescriptor.getCommitLogLocation())) ||
            (DatabaseDescriptor.getAccordJournalDirectory() != null && dir.startsWith(DatabaseDescriptor.getAccordJournalDirectory())) ||
            dir.startsWith(DatabaseDescriptor.getHintsDirectory().absolutePath()) ||
            (DatabaseDescriptor.getCDCLogLocation() != null && dir.startsWith(DatabaseDescriptor.getCDCLogLocation())) ||
            (DatabaseDescriptor.getSavedCachesLocation() != null && dir.startsWith(DatabaseDescriptor.getSavedCachesLocation())))
        {
            throw new ConfigurationException("You can not store Async-Profiler results into system Cassandra directory.");
        }

        for (String location : StorageService.instance.getAllDataFileLocations())
        {
            if (dir.startsWith(location))
            {
                throw new ConfigurationException("You can not store Async-Profiler results into a data directory of Cassandra.");
            }
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

    private boolean isRunning()
    {
        if (!isEnabled())
            return false;

        try
        {
            String status = maybeInitialize().execute("status");
            return status != null && status.contains("Profiling is running");
        }
        catch (Throwable t)
        {
            throw new RuntimeException(t);
        }
    }

    public static class AsyncProfilerNotEnabled extends IllegalStateException
    {
        public AsyncProfilerNotEnabled(String s)
        {
            super(s);
        }
    }
}
