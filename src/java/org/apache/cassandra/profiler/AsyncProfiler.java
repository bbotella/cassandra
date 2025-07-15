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

import java.util.List;
import javax.management.StandardMBean;

import org.apache.cassandra.exceptions.ConfigurationException;
import org.apache.cassandra.service.AsyncProfilerService;
import org.apache.cassandra.utils.MBeanWrapper;

import static org.apache.cassandra.config.CassandraRelevantProperties.ASYNC_PROFILER_UNSAFE_MODE;

public abstract class AsyncProfiler implements AsyncProfilerMBean
{
    protected final AsyncProfilerService service = new AsyncProfilerService();

    private static AsyncProfiler instance;

    public static synchronized AsyncProfiler instance()
    {
        if (AsyncProfiler.instance == null)
        {
            try
            {
                AsyncProfiler.instance = ASYNC_PROFILER_UNSAFE_MODE.getBoolean() ? new AsyncProfilerUnsafe() : new AsyncProfilerSafe();

                // register mbean first, before initialisation, which might fail (e.g. profiler functionality is disabled)
                MBeanWrapper.instance.registerMBean(new StandardMBean(AsyncProfiler.instance, AsyncProfilerMBean.class),
                                                    AsyncProfiler.MBEAN_NAME,
                                                    MBeanWrapper.OnException.LOG);

                instance.initialize();
            }
            catch (ConfigurationException ex)
            {
                throw ex;
            }
            catch (IllegalStateException ex)
            {
                if (!"Async-Profiler is not enabled.".equals(ex.getMessage()))
                    throw ex;
            }
            catch (Throwable t)
            {
                throw new RuntimeException(t);
            }
        }

        return AsyncProfiler.instance;
    }

    @Override
    public boolean start(String events, String outputFormat, String duration, String outputFileName)
    {
        return service.start(events, outputFormat, duration, outputFileName);
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

    @Override
    public void purge()
    {
        service.purge();
    }

    @Override
    public List<String> list()
    {
        return service.list();
    }

    @Override
    public byte[] fetch(String resultFile)
    {
        return service.fetch(resultFile);
    }

    @Override
    public String status()
    {
        return service.status();
    }

    public void initialize()
    {
        service.maybeInitialize();
    }
}
