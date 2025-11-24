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

package org.apache.cassandra.tools;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import org.apache.cassandra.io.util.FileUtils;
import org.apache.cassandra.profiler.AsyncProfiler;
import org.apache.cassandra.profiler.AsyncProfilerSafe;
import org.apache.cassandra.profiler.AsyncProfilerUnsafe;
import org.apache.cassandra.tools.profiler.AsyncProfilerService;

import static org.apache.cassandra.config.CassandraRelevantProperties.ASYNC_PROFILER_UNSAFE_MODE;
import static org.apache.cassandra.config.CassandraRelevantProperties.ASYNC_PROFILER_ENABLED;
import static org.apache.cassandra.config.CassandraRelevantProperties.ASYNC_PROFILER_LOG_DIR;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AsyncProfilerServiceTest
{
    private static final String testOutputPath = FileUtils.getTempDir().path();

    private AsyncProfilerService profilerService;
    private String testOutputFile;

    @BeforeClass
    public static void setUpClass()
    {
        ASYNC_PROFILER_LOG_DIR.setString(testOutputPath);
    }

    @Before
    public void setUp()
    {
        ASYNC_PROFILER_ENABLED.setBoolean(true);
        testOutputFile = UUID.randomUUID().toString();
    }

    @After
    public void tearDown()
    {
        try
        {
            profilerService.stop(testOutputFile);
            profilerService.disable();
            File outputFile = new File(testOutputFile);
            if (outputFile.exists())
            {
                outputFile.delete();
            }
        }
        catch (Exception e)
        {
            // The only meaningful exception that can surface here is if profiler.start
            // was not called prior to profiler.stop, we can safely ignore this.
        }

        profilerService = null;
    }

    private AsyncProfilerService getProfilerService()
    {
        AsyncProfiler profiler = ASYNC_PROFILER_UNSAFE_MODE.getBoolean() ? new AsyncProfilerUnsafe() : new AsyncProfilerSafe();
        profilerService = profiler.getService();
        profilerService.getProfilerInstance();

        if (!profilerService.isEnabled())
        {
            fail("AsyncProfilerService could not initialize (native lib not found or invalid).");
        }

        return profilerService;
    }

    @Test
    public void testStartAndStopProfiling() throws Throwable
    {
        AsyncProfilerService service = getProfilerService();
        service.start("cpu", "flamegraph", 10, testOutputFile + ".html");
        Thread.sleep(2000);
        service.stop(testOutputFile + ".html");

        File file = new File(Path.of(testOutputPath, testOutputFile + ".html").toString());

        assertTrue("Output profile file should exist", file.exists());
        assertTrue("Output profile file should not be empty", file.length() > 0);
    }

    @Test
    public void testInvalidEventThrowsException()
    {
        AsyncProfilerService service = getProfilerService();
        assertThatThrownBy(() -> service.start("not_a_real_event", "flamegraph", 60, testOutputFile))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Event must be one or a combination of [cpu, alloc, lock, wall, nativemem, cache_misses]");
    }

    @Test
    public void testInvalidFormatThrowsException()
    {
        AsyncProfilerService service = getProfilerService();

        assertThatThrownBy(() -> service.start("cpu", "not_a_real_format", 60, testOutputFile))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Format must be one of [flat, traces, collapsed, flamegraph, tree, jfr, otlp]");
    }

    @Test
    public void testInvalidOutputFileNameThrowsException()
    {
        AsyncProfilerService service = getProfilerService();

        assertThatThrownBy(() -> service.start("cpu",
                                               "flamegraph",
                                               60,
                                               "| grep test"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Output file name must match pattern ^[a-zA-Z0-9-]*\\.?[a-zA-Z0-9-]*$");
    }

    @Test
    public void testMultipleStartCallsThrowsException()
    {
        AsyncProfilerService service = getProfilerService();

        assertThatThrownBy(() -> {
            service.start("cpu", "flamegraph", 60, testOutputFile);
            service.start("cpu", "flamegraph", 60, testOutputFile);
        }).isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Profiler already started");
    }

    @Test
    public void testProfilerDisabledThrowsException()
    {
        AsyncProfilerService service = getProfilerService();

        assertThatThrownBy(() -> {
            service.disable();
            service.execute("start,event=cpu");
        }).isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Async-Profiler is not enabled.");
    }

    @Test
    public void testAdvancedModeEnabledSuccess() throws Throwable
    {
        ASYNC_PROFILER_UNSAFE_MODE.setBoolean(true);
        AsyncProfilerService asyncProfiler = getProfilerService();

        asyncProfiler.execute("start,event=cpu");
        Thread.sleep(5000);
        asyncProfiler.execute(String.format("stop,file=%s", testOutputFile));

        File file = new File(testOutputFile);
        assertTrue("Output profile file for advanced mode should exist", file.exists());
        assertTrue("Output profile file for advanced mode should not be empty", file.length() > 0);
    }
}
