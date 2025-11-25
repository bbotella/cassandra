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

import org.apache.cassandra.profiler.AsyncProfilerMBean;
import org.apache.cassandra.tools.NodeProbe;
import org.apache.cassandra.tools.profiler.AsyncProfilerService.AsyncProfilerEvent;
import org.apache.cassandra.tools.profiler.AsyncProfilerService.AsyncProfilerFormat;
import org.apache.cassandra.utils.FBUtilities;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import static java.util.stream.Collectors.joining;
import static org.apache.cassandra.tools.profiler.AsyncProfilerService.validateCommand;
import static org.apache.cassandra.tools.profiler.AsyncProfilerService.validateOutputFileName;
import static org.apache.cassandra.tools.profiler.AsyncProfilerService.validateTimeout;

@Command(name = "profile", description = "Manage Async-Profiler on a Cassandra process",
subcommands = {
AsyncProfileCommandGroup.AsyncProfileStartCommand.class,
AsyncProfileCommandGroup.AsyncProfileStopCommand.class,
AsyncProfileCommandGroup.AsyncProfileRawCommand.class
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
                boolean started = profiler.start(event.stream().map(Enum::name).collect(joining(",")),
                                               outputFormat.name(),
                                               validateTimeout(timeout),
                                               validateOutputFileName(filename));
                if (!started)
                {
                    probe.output().err.println("Profiler has already started or there was a failure to start it.");
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
                boolean stopped = profiler.stop(validateOutputFileName(filename));

                if (!stopped)
                {
                    probe.output().err.println("Profiler has already stopped or there was a failure to stop it.");
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
                String result = profiler.execute(validateCommand(command));
                probe.output().out.println(result);
            });
        }
    }
}
