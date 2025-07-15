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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.distributed.shared.WithProperties;
import org.apache.cassandra.io.util.File;
import org.apache.cassandra.io.util.FileUtils;
import org.apache.cassandra.profiler.AsyncProfiler;
import org.apache.cassandra.profiler.AsyncProfilerSafe;
import org.apache.cassandra.profiler.AsyncProfilerUnsafe;

import static java.lang.String.format;
import static org.apache.cassandra.config.CassandraRelevantProperties.ASYNC_PROFILER_ENABLED;
import static org.apache.cassandra.config.CassandraRelevantProperties.ASYNC_PROFILER_LOG_DIR;
import static org.apache.cassandra.config.CassandraRelevantProperties.ASYNC_PROFILER_UNSAFE_MODE;
import static org.apache.cassandra.service.AsyncProfilerService.AsyncProfilerEvent.cpu;
import static org.apache.cassandra.service.AsyncProfilerService.AsyncProfilerFormat.flamegraph;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AsyncProfilerServiceTest
{
    private static final String testOutputPath = FileUtils.getTempDir().path();

    private AsyncProfiler profiler;
    private File testOutputFile;

    @BeforeClass
    public static void setUpClass()
    {
        ASYNC_PROFILER_LOG_DIR.setString(testOutputPath);
        DatabaseDescriptor.daemonInitialization();
    }

    @Before
    public void setUp()
    {
        ASYNC_PROFILER_ENABLED.setBoolean(true);
        testOutputFile = new File(testOutputPath, UUID.randomUUID().toString());
    }

    @After
    public void tearDown()
    {
        try
        {
            profiler.stop(testOutputFile.absolutePath());
            profiler.disable();
            testOutputFile.deleteIfExists();
        }
        catch (Exception e)
        {
            // The only meaningful exception that can surface here is if profiler.start
            // was not called prior to profiler.stop, we can safely ignore this.
        }

        profiler = null;
    }

    private AsyncProfiler getProfiler()
    {
        AsyncProfiler profiler = ASYNC_PROFILER_UNSAFE_MODE.getBoolean() ? new AsyncProfilerUnsafe() : new AsyncProfilerSafe();
        profiler.initialize();
        assertTrue(profiler.isEnabled());
        return profiler;
    }

    @Test
    public void testStartAndStopProfiling() throws Throwable
    {
        AsyncProfiler profiler = getProfiler();
        profiler.start(cpu.name(), flamegraph.name(), "10s", testOutputFile.name());
        Thread.sleep(5000);
        profiler.stop(testOutputFile.name());

        assertTrue("Output profile file should exist", testOutputFile.exists());
        assertTrue("Output profile file should not be empty", testOutputFile.length() > 0);

        List<String> list = profiler.list();
        assertFalse(list.isEmpty());

        Optional<String> resultFile = list.stream().filter(f -> f.equals(testOutputFile.name())).findFirst();
        assertTrue(resultFile.isPresent());
        byte[] content = profiler.fetch(resultFile.get());
        assertNotNull(content);

        assertEquals("Profiler is not active\n", profiler.status());
    }

    @Test
    public void testInvalidEventThrowsException()
    {
        assertThatThrownBy(() -> getProfiler().start("not_a_real_event", "flamegraph", "60s", testOutputFile.absolutePath()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Event must be one or a combination of [cpu, alloc, lock, wall, nativemem, cache_misses]");
    }

    @Test
    public void testInvalidFormatThrowsException()
    {
        assertThatThrownBy(() -> getProfiler().start(cpu.name(), "not_a_real_format", "60s", testOutputFile.absolutePath()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Format must be one of [flat, traces, collapsed, flamegraph, tree, jfr, otlp]");
    }

    @Test
    public void testInvalidOutputFileNameThrowsException()
    {
        assertThatThrownBy(() -> getProfiler().start(cpu.name(),
                                                     flamegraph.name(),
                                                     "60s",
                                                     "| grep test"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Output file name must match pattern ^[a-zA-Z0-9-]*\\.?[a-zA-Z0-9-]*$");
    }

    @Test
    public void testInvalidTimeoutThrowsException()
    {
        assertThatThrownBy(() -> {
            getProfiler().start(cpu.name(),
                                flamegraph.name(),
                                "10abc",
                                "| grep test");
        })
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid duration: 10abc Accepted units:[SECONDS, MINUTES, HOURS, DAYS] where case matters and only non-negative values.");
    }

    @Test
    public void testSecondStartNotExecuted()
    {
        AsyncProfiler profiler = getProfiler();
        assertTrue(profiler.start(cpu.name(), flamegraph.name(), "60s", testOutputFile.name()));
        assertFalse(profiler.start(cpu.name(), flamegraph.name(), "60s", testOutputFile.name()));
        profiler.stop(testOutputFile.name());
    }

    @Test
    public void testProfilerDisabledThrowsException()
    {
        assertThatThrownBy(() -> {
            AsyncProfiler profiler = getProfiler();
            profiler.disable();
            profiler.execute("start,event=" + cpu.name() + ",file=" + testOutputFile.absolutePath());
        }).hasCauseExactlyInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Async-Profiler is not enabled.");
    }

    @Test
    public void testAdvancedModeEnabledSuccess() throws Throwable
    {
        ASYNC_PROFILER_UNSAFE_MODE.setBoolean(true);
        AsyncProfiler profiler = getProfiler();

        profiler.execute("start,event=" + cpu.name() + ",file=" + testOutputFile.absolutePath());
        Thread.sleep(5000);
        profiler.execute(format("stop,file=%s", testOutputFile));

        assertTrue("Output profile file for unsafe mode should exist", testOutputFile.exists());
        assertTrue("Output profile file for unsafe mode should not be empty", testOutputFile.length() > 0);
    }

    @Test
    public void testUnsafeExecute()
    {
        try (WithProperties properties = new WithProperties().set(ASYNC_PROFILER_UNSAFE_MODE, true))
        {
            getProfiler().execute("foo");
        }
    }

    @Test
    public void testSafeExecute()
    {
        try (WithProperties properties = new WithProperties().set(ASYNC_PROFILER_UNSAFE_MODE, false))
        {
            assertThatThrownBy(() -> getProfiler().execute("foo"))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("The arbitrary command execution is not permitted with org.apache.cassandra.profiler:type=AsyncProfiler " +
                                  "MBean backed by org.apache.cassandra.profiler.AsyncProfilerSafe class. " +
                                  "If unsafe command execution is required, start Cassandra with ASYNC_PROFILER_UNSAFE_MODE " +
                                  "property set to true. Rejected command: foo");
        }
    }
}
