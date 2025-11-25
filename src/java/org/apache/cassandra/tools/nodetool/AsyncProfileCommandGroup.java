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

package org.apache.cassandra.tools.nodetool;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

import org.apache.cassandra.io.util.File;
import org.apache.cassandra.io.util.FileUtils;
import org.apache.cassandra.profiler.AsyncProfilerMBean;
import org.apache.cassandra.tools.NodeProbe;
import org.apache.cassandra.tools.profiler.AsyncProfilerService.AsyncProfilerEvent;
import org.apache.cassandra.tools.profiler.AsyncProfilerService.AsyncProfilerFormat;
import org.apache.cassandra.utils.FBUtilities;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.stream.Collectors.joining;
import static org.apache.cassandra.tools.profiler.AsyncProfilerService.validateCommand;
import static org.apache.cassandra.tools.profiler.AsyncProfilerService.validateOutputFileName;
import static org.apache.cassandra.tools.profiler.AsyncProfilerService.validateTimeout;

@Command(name = "profile", description = "Manage Async-Profiler on a Cassandra process",
subcommands = {
AsyncProfileCommandGroup.AsyncProfileStartCommand.class,
AsyncProfileCommandGroup.AsyncProfileStopCommand.class,
AsyncProfileCommandGroup.AsyncProfileRawCommand.class,
AsyncProfileCommandGroup.AsyncProfilePurgeCommand.class,
AsyncProfileCommandGroup.AsyncProfileListCommand.class,
AsyncProfileCommandGroup.AsyncProfileFetchCommand.class
})
public class AsyncProfileCommandGroup extends AbstractCommand
{
    @Override
    public void execute(NodeProbe probe)
    {
        AbstractCommand cmd = new AsyncProfileStartCommand();
        cmd.probe(probe);
        cmd.logger(output);
        cmd.run();
    }

    public static void doWithProfiler(NodeProbe probe, Consumer<AsyncProfilerMBean> consumer)
    {
        AsyncProfilerMBean profiler = probe.getAsyncProfilerProxy();

        if (!profiler.isEnabled())
        {
            probe.output().err.println("Async-profiler native library is not loaded or unavailable.");
            System.exit(1);
        }

        consumer.accept(profiler);
    }

    @Command(name = "start", description = "Run Async-Profiler on a Cassandra process")
    public static class AsyncProfileStartCommand extends AbstractCommand
    {
        @Option(names = { "-e", "--event" },
        description = "Event(s) to profile, one of or combination of 'cpu', 'alloc', " +
                      "'lock', 'wall', 'nativemem', 'cache_misses', delimited by comma.")
        public List<AsyncProfilerEvent> event = List.of(AsyncProfilerEvent.cpu);

        @Option(names = { "-o", "--output" }, description = "File Name")
        public String filename = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")
                                                  .withZone(ZoneId.systemDefault()).format(FBUtilities.now()) + ".html";

        @Option(names = { "-t", "--timeout" }, description = "Timeout in seconds")
        public int timeout = 60;

        @Option(names = { "-f", "--format" },
        description = "Output format, one of 'flat', 'traces', 'collapsed', 'flamegraph', 'tree', 'jfr', 'otlp'")
        public AsyncProfilerFormat outputFormat = AsyncProfilerFormat.flamegraph;

        @Override
        public void execute(NodeProbe probe)
        {
            doWithProfiler(probe, profiler -> {
                if (!profiler.start(event.stream().map(Enum::name).collect(joining(",")),
                                    outputFormat.name(),
                                    validateTimeout(timeout),
                                    validateOutputFileName(filename)))
                {
                    output.err.println("Profiler has already started or there was a failure to start it.");
                    System.exit(1);
                }
            });
        }
    }

    @Command(name = "stop", description = "Stop Async-Profiler on a Cassandra process")
    public static class AsyncProfileStopCommand extends AbstractCommand
    {
        @Option(names = { "-o", "--output" }, description = "File Name")
        public String filename = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")
                                                  .withZone(ZoneId.systemDefault()).format(FBUtilities.now()) + ".html";

        @Override
        public void execute(NodeProbe probe)
        {
            doWithProfiler(probe, profiler -> {
                if (!profiler.stop(validateOutputFileName(filename)))
                {
                    output.err.println("Profiler has already stopped or there was a failure to stop it.");
                    System.exit(1);
                }
            });
        }
    }

    @Command(name = "raw", description = "Execute an arbitrary command on Async-Profiler on a Cassandra process")
    public static class AsyncProfileRawCommand extends AbstractCommand
    {
        @Option(names = { "-c", "--command" }, description = "Raw commands to execute")
        public String command;

        @Override
        public void execute(NodeProbe probe)
        {
            doWithProfiler(probe, profiler -> {
                output.out.println(profiler.execute(validateCommand(command)));
            });
        }
    }

    @Command(name = "purge", description = "Remove all profiling results from node's disk")
    public static class AsyncProfilePurgeCommand extends AbstractCommand
    {
        @Override
        protected void execute(NodeProbe probe)
        {
            doWithProfiler(probe, AsyncProfilerMBean::purge);
        }
    }

    @Command(name = "list", description = "List profiling result files of a node")
    public static class AsyncProfileListCommand extends AbstractCommand
    {
        @Override
        protected void execute(NodeProbe probe)
        {
            doWithProfiler(probe, profiler -> {
                for (String resultFile : profiler.list())
                    output.out.println(resultFile);
            });
        }
    }

    @Command(name = "fetch", description = "Copy profiler result file from node to a local file")
    public static class AsyncProfileFetchCommand extends AbstractCommand
    {
        @Parameters(index = "0", description = "Remote profiler file name", arity = "1")
        private String remoteFile;

        @Parameters(index = "1", description = "Local file name", arity = "1")
        private String localFile;

        @Override
        protected void execute(NodeProbe probe)
        {
            doWithProfiler(probe, profiler -> {
                String content = profiler.fetch(remoteFile);
                if (content != null)
                    FileUtils.write(new File(localFile), List.of(content), CREATE, TRUNCATE_EXISTING, WRITE);
                else
                {
                    output.out.println("File " + remoteFile + " does not exist.");
                    System.exit(1);
                }
            });
        }
    }
}
