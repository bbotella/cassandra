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

import org.apache.cassandra.tools.profiler.AsyncProfilerService;

public abstract class AsyncProfiler implements AsyncProfilerMBean
{
    public static final String MBEAN_NAME = "org.apache.cassandra.profiler:type=AsyncProfiler";
    protected final AsyncProfilerService service = new AsyncProfilerService();

    @Override
    public boolean start(String events, String outputFormat, int timeout, String outputFileName)
    {
        return service.start(events, outputFormat, timeout, outputFileName);
    }

    @Override
    public boolean stop(String outputFileName)
    {
        return service.stop(outputFileName);
    }

    @Override
    public boolean isEnabled()
    {
        return service.isEnabled();
    }

    @Override
    public void disable()
    {
        service.disable();
    }

    @Override
    public void enable()
    {
        service.enable();
    }

    public void initialize()
    {
        service.maybeInitialize();
    }
}
