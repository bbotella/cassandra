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

package org.apache.cassandra.profiler;

import org.apache.cassandra.tools.profiler.AsyncProfilerService.AsyncProfilerEvent;
import org.apache.cassandra.tools.profiler.AsyncProfilerService.AsyncProfilerFormat;

public interface AsyncProfilerMBean
{
    /**
     * Starts profiling.
     *
     * @param events         events, can be joined by a comma, each event has to be one of enum names of
     *                       {@link AsyncProfilerEvent}
     * @param outputFormat   output format, has to be one of enum names of {@link AsyncProfilerFormat}
     * @param timeout        timeout, has to be strictly positive
     * @param outputFileName file name to save results to
     */
    void start(String events, String outputFormat, int timeout, String outputFileName);

    /**
     * Stops profiling.
     *
     * @param outputFileName file name to save results to
     */
    void stop(String outputFileName);

    /**
     * Executes a command.
     *
     * @param command command to execute.
     */
    void execute(String command);

    /**
     * Checks if a profiler is available.
     *
     * @return true if async profiling is enabled and profiler is initialized, false otherwise.
     */
    boolean isEnabled();

    /**
     * Disables Async-Profiler, if not already disabled.
     */
    void disable();

    /**
     * Enables Async-Profiler, if not already enabled.
     */
    void enable();
}
