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

import org.apache.cassandra.tools.NodeProbe;
import org.apache.cassandra.profiler.AsyncProfilerMBean;

import org.apache.cassandra.utils.FBUtilities;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "profile", description = "Manage Async-Profiler on the cassandra process",
         subcommands = {
            Profile.Start.class,
            Profile.Stop.class,
            Profile.Raw.class
         })
public class Profile extends AbstractCommand
{
    @Override
    public void execute(NodeProbe probe) {
        AbstractCommand cmd = new Start();
        cmd.probe(probe);
        cmd.logger(output);
        cmd.run();
    }

    @Command(name = "start", description = "Run Async-Profiler on the cassandra process")
    public static class Start extends AbstractCommand
    {

        @Option(names = {"-e", "--event"}, description = "Event to profile (cpu, alloc, lock, wall, etc.)")
        public String event = "cpu";

        @Option(names = {"-o", "--output"}, description = "File Name")
        public String filename = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")
                                                  .withZone(ZoneId.systemDefault()).format(FBUtilities.now()) + ".html";

        @Option(names = {"-t", "--timeout"}, description = "Timeout in seconds")
        public int timeout = 60;

        @Option(names = {"-f", "--format"}, description = "Output format (flamegraph, tree, traces, etc.)")
        public String outputFormat = "flamegraph";

        @Override
        protected void execute(NodeProbe probe)
        {
            AsyncProfilerMBean profiler = probe.getAsyncProfilerProxy();

            if (!profiler.isAvailable()) {
                System.err.println("Async-profiler native library is not loaded or unavailable.");
                return;
            }

            System.out.printf("Starting async-profiler: event=%s, format=%s\n", event, outputFormat);
            profiler.start(event, outputFormat, timeout, filename);
        }
    }

    @Command(name = "stop", description = "Stop Async-Profiler on the cassandra process")
    public static class Stop extends AbstractCommand
    {
        @Option(names = {"-o", "--output"}, description = "File Name")
        public String filename = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")
                                                  .withZone(ZoneId.systemDefault()).format(FBUtilities.now()) + ".html";

        @Override
        protected void execute(NodeProbe probe)
        {
            AsyncProfilerMBean profiler = probe.getAsyncProfilerProxy();

            if (!profiler.isAvailable()) {
                System.err.println("Async-profiler native library is not loaded or unavailable.");
                return;
            }

            System.out.printf("Stopping profiler\n");
            profiler.stop(filename);
        }
    }

    @Command(name = "raw", description = "Execute an arbitrary command on Async-Profiler on the cassandra process")
    public static class Raw extends AbstractCommand
    {

        @Option(names = {"-c", "--command"}, description = "Raw commands to execute")
        public String command;

        @Override
        protected void execute(NodeProbe probe)
        {
            AsyncProfilerMBean profiler = probe.getAsyncProfilerProxy();

            if (!profiler.isAvailable()) {
                System.err.println("Async-profiler native library is not loaded or unavailable.");
                return;
            }

            System.out.printf("Executing raw command: %s\n", command);
            profiler.execute(command);
        }
    }
}
