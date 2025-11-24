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
    private final AsyncProfilerService service = new AsyncProfilerService();

    public void start(String events, String outputFormat, int timeout, String outputFileName)
    {
        getService().start(events, outputFormat, timeout, outputFileName);
    }

    public void stop(String outputFileName)
    {
        getService().stop(outputFileName);
    }

    public boolean isEnabled()
    {
        return getService().isEnabled();
    }

    public AsyncProfilerService getService()
    {
        return service;
    }

    @Override
    public void disable()
    {
        if (!isEnabled())
            return;

        getService().disable();
    }

    @Override
    public void enable()
    {
        if (isEnabled())
            return;

        getService().enable();
    }
}
