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

import org.apache.cassandra.config.CassandraRelevantProperties;

/**
 * Safe version is unable to execute any command.
 */
public class AsyncProfilerSafe extends AsyncProfiler
{
    @Override
    public String execute(String command)
    {
        throw new SecurityException(String.format("The arbitrary command execution is not permitted " +
                                                  "with %s MBean backed by %s class. If unsafe command execution is required, " +
                                                  "start Cassandra with %s property set to true. " +
                                                  "Rejected command: %s%n",
                                                  AsyncProfiler.MBEAN_NAME,
                                                  AsyncProfilerSafe.class.getName(),
                                                  CassandraRelevantProperties.ASYNC_PROFILER_UNSAFE_MODE.name(), command));
    }
}
