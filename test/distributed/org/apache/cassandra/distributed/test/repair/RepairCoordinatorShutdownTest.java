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

package org.apache.cassandra.distributed.test.repair;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableMap;

import org.junit.Test;

import org.apache.cassandra.distributed.Cluster;
import org.apache.cassandra.distributed.test.TestBaseImpl;
import org.apache.cassandra.net.Verb;
import org.apache.cassandra.repair.messages.RepairOption;
import org.apache.cassandra.service.ActiveRepairService;
import org.apache.cassandra.service.StorageService;

import static org.apache.cassandra.distributed.api.Feature.GOSSIP;
import static org.apache.cassandra.distributed.api.Feature.NETWORK;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A repair whose coordinator never completes must not leak its Repair# executor past shutdown (CASSANDRA-21870).
 */
public class RepairCoordinatorShutdownTest extends TestBaseImpl
{
    @Test
    public void shutdownTerminatesExecutorOfIncompleteRepair() throws Exception
    {
        try (Cluster cluster = init(Cluster.build(2)
                                          .withConfig(config -> config.with(GOSSIP).with(NETWORK))
                                          .start()))
        {
            cluster.schemaChange(withKeyspace("create table %s.tbl (id int primary key, t int)"));
            for (int i = 0; i < 10; i++)
                cluster.get(1).executeInternal(withKeyspace("insert into %s.tbl (id, t) values (?, ?)"), i, i);
            cluster.forEach(node -> node.flush(KEYSPACE));

            // node2 never sees the finalize proposal, so the incremental repair on node1 never completes
            cluster.filters().verbs(Verb.FINALIZE_PROPOSE_MSG.id).from(1).to(2).drop();

            cluster.get(1).runOnInstance(() -> StorageService.instance.repairAsync(KEYSPACE, ImmutableMap.of(RepairOption.INCREMENTAL_KEY, "true")));
            cluster.get(1).logs().watchFor("Proposing finalization of repair session");

            cluster.get(1).runOnInstance(() -> {
                assertFalse("expected a live Repair# thread while the repair is in flight", repairThreads().isEmpty());
                try
                {
                    ActiveRepairService.instance().shutdownNowAndWait(1L, TimeUnit.MINUTES);
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
                List<String> leaked = repairThreads();
                assertTrue("Repair# threads still alive after shutdown: " + leaked, leaked.isEmpty());
            });
        }
    }

    private static List<String> repairThreads()
    {
        return Thread.getAllStackTraces().keySet().stream()
                     .filter(Thread::isAlive)
                     .map(Thread::getName)
                     .filter(name -> name.contains("_Repair#"))
                     .collect(Collectors.toList());
    }
}
