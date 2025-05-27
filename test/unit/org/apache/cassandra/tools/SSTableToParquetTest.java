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

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import org.apache.avro.generic.GenericRecord;
import org.apache.cassandra.cql3.CQLTester;
import org.apache.cassandra.db.Keyspace;
import org.apache.cassandra.db.SystemKeyspace;
import org.apache.cassandra.io.sstable.Descriptor;
import org.apache.cassandra.io.sstable.format.SSTableReader;
import org.apache.cassandra.io.util.File;
import org.apache.cassandra.locator.InetAddressAndPort;
import org.apache.cassandra.schema.TableMetadata;
import org.apache.cassandra.utils.FBUtilities;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.avro.AvroParquetReader;
import org.apache.parquet.hadoop.ParquetReader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SSTableToParquetTest extends CQLTester
{
    private java.nio.file.Path tempParquetFileDir;

    @BeforeClass
    public static void defineSchema()
    {
        // No specific schema needed at class level for CQLTester if tables are created per test
        // However, ensure DatabaseDescriptor is initialized if not already by CQLTester
        requireNetwork(); // Or other CQLTester setup if needed, like schema version agreement
    }

    @After
    public void tearDown() throws IOException
    {
        if (tempParquetFileDir != null)
        {
            // Simple cleanup, might need to be more robust if files are locked
            Files.list(tempParquetFileDir).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException e) { e.printStackTrace(); }
            });
            Files.deleteIfExists(tempParquetFileDir);
            tempParquetFileDir = null;
        }
    }
    
    private File findSSTable(String ks, String cf)
    {
        Keyspace keyspace = Keyspace.open(ks);
        org.apache.cassandra.db.ColumnFamilyStore cfs = keyspace.getColumnFamilyStore(cf);
        if (cfs == null)
            throw new IllegalArgumentException("CFS not found: " + ks + "." + cf);
        cfs.forceBlockingFlush(SystemKeyspace.FlushReason.UNIT_TESTS); // Ensure data is flushed
        Set<SSTableReader> sstables = cfs.getLiveSSTables();
        if (sstables.isEmpty())
            throw new IllegalStateException("No SSTables found for " + ks + "." + cf);
        
        // Return the first Data.db file found. In a real scenario, might need to be more specific.
        for (SSTableReader sstable : sstables)
        {
            File sstableFile = sstable.descriptor.fileFor(Descriptor.Component.DATA);
            if (sstableFile.exists())
            {
                return sstableFile;
            }
        }
        throw new IllegalStateException("No Data.db SSTable file found for " + ks + "." + cf);
    }


    @Test
    public void testSimpleTypesConversion() throws Throwable
    {
        String keyspace = createKeyspace("CREATE KEYSPACE %s WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 }");
        String table = createTable(keyspace, "CREATE TABLE %s (pk int PRIMARY KEY, val_text text, val_int int, val_double double, val_bool boolean, val_list list<text>)");

        execute("INSERT INTO " + table + " (pk, val_text, val_int, val_double, val_bool, val_list) VALUES (?, ?, ?, ?, ?, ?)", 1, "hello", 100, 1.23, true, List.of("a","b"));
        execute("INSERT INTO " + table + " (pk, val_text, val_int, val_double, val_bool, val_list) VALUES (?, ?, ?, ?, ?, ?)", 2, "world", 200, 4.56, false, List.of("c","d","e"));
        
        // Flush is implicitly called by findSSTable helper
        File sstableToConvert = findSSTable(keyspace, currentTable());
        assertNotNull("SSTable Data.db file not found", sstableToConvert);

        tempParquetFileDir = Files.createTempDirectory("parquet_test_output");
        java.nio.file.Path tempParquetFile = tempParquetFileDir.resolve("test_simple_types_" + UUID.randomUUID() + ".parquet");

        String[] args = {
            "-i", sstableToConvert.absolutePath(),
            "-o", tempParquetFile.toString()
        };
        SSTableToParquet.main(args);

        assertTrue("Parquet file was not created: " + tempParquetFile.toString(), Files.exists(tempParquetFile));
        assertTrue("Parquet file is empty: " + tempParquetFile.toString(), Files.size(tempParquetFile) > 0);

        int rowCount = 0;
        // Use try-with-resources for ParquetReader
        try (ParquetReader<GenericRecord> reader = AvroParquetReader.<GenericRecord>builder(new Path(tempParquetFile.toUri())).build())
        {
            GenericRecord record;
            while ((record = reader.read()) != null)
            {
                rowCount++;
                int pk = (Integer) record.get("pk");
                if (pk == 1)
                {
                    assertEquals("hello", record.get("val_text").toString());
                    assertEquals(100, ((Integer) record.get("val_int")).intValue());
                    assertEquals(1.23, ((Double) record.get("val_double")).doubleValue(), 0.001);
                    assertEquals(true, record.get("val_bool"));
                    @SuppressWarnings("unchecked")
                    List<CharSequence> listVal = (List<CharSequence>) record.get("val_list");
                    assertNotNull(listVal);
                    assertEquals(2, listVal.size());
                    assertEquals("a", listVal.get(0).toString());
                    assertEquals("b", listVal.get(1).toString());
                }
                else if (pk == 2)
                {
                    assertEquals("world", record.get("val_text").toString());
                    assertEquals(200, ((Integer) record.get("val_int")).intValue());
                    assertEquals(4.56, ((Double) record.get("val_double")).doubleValue(), 0.001);
                    assertEquals(false, record.get("val_bool"));
                    @SuppressWarnings("unchecked")
                    List<CharSequence> listVal = (List<CharSequence>) record.get("val_list");
                    assertNotNull(listVal);
                    assertEquals(3, listVal.size());
                    assertEquals("c", listVal.get(0).toString());
                    assertEquals("d", listVal.get(1).toString());
                    assertEquals("e", listVal.get(2).toString());
                } else {
                    throw new AssertionError("Unexpected pk value: " + pk);
                }
            }
        }
        assertEquals("Number of rows in Parquet file does not match inserted rows", 2, rowCount);
        // Temp file cleanup is handled by @After method
    }
}
