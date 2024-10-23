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

package org.apache.cassandra.contraints;


import org.junit.Test;

import org.apache.cassandra.cql3.ConstraintInvalidException;
import org.apache.cassandra.cql3.ConstraintViolationException;
import org.apache.cassandra.exceptions.InvalidRequestException;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CreateTableWithColumnCqlConstraintValidationTest extends CqlConstraintValidationTester
{

    @Test
    public void testCreateTableWithColumnNotNamedConstraintDescribeTableNonFunction() throws Throwable
    {
        String table = createTable(KEYSPACE_PER_TEST, "CREATE TABLE %s (pk int, ck1 int CHECK ck1 < 100, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2)) WITH CLUSTERING ORDER BY (ck1 ASC);");

        String tableCreateStatement = "CREATE TABLE " + KEYSPACE_PER_TEST + "." + table + " (\n" +
                                      "    pk int,\n" +
                                      "    ck1 int CHECK ck1 < 100,\n" +
                                      "    ck2 int,\n" +
                                      "    v int,\n" +
                                      "    PRIMARY KEY (pk, ck1, ck2)\n" +
                                      ") WITH CLUSTERING ORDER BY (ck1 ASC, ck2 ASC)\n" +
                                      "    AND " + tableParametersCql();

        assertRowsNet(executeDescribeNet("DESCRIBE TABLE " + KEYSPACE_PER_TEST + "." + table),
                      row(KEYSPACE_PER_TEST,
                          "table",
                          table,
                          tableCreateStatement));
    }

    @Test
    public void testCreateTableWithColumnNotNamedConstraintDescribeTableFunction() throws Throwable
    {
        String table = createTable(KEYSPACE_PER_TEST, "CREATE TABLE %s (pk int, ck1 text CHECK LENGTH(ck1) = 4, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2)) WITH CLUSTERING ORDER BY (ck1 ASC);");

        String tableCreateStatement = "CREATE TABLE " + KEYSPACE_PER_TEST + "." + table + " (\n" +
                                      "    pk int,\n" +
                                      "    ck1 text CHECK LENGTH(ck1) = 4,\n" +
                                      "    ck2 int,\n" +
                                      "    v int,\n" +
                                      "    PRIMARY KEY (pk, ck1, ck2)\n" +
                                      ") WITH CLUSTERING ORDER BY (ck1 ASC, ck2 ASC)\n" +
                                      "    AND " + tableParametersCql();

        assertRowsNet(executeDescribeNet("DESCRIBE TABLE " + KEYSPACE_PER_TEST + "." + table),
                      row(KEYSPACE_PER_TEST,
                          "table",
                          table,
                          tableCreateStatement));
    }


    // SCALAR
    @Test
    public void testCreateTableWithColumnWithClusteringColumnLessThanScalarConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 int CHECK ck1 < 4, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2)) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 2, 2, 3)");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 4, 2, 3)");
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 5, 2, 3)");
    }

    @Test
    public void testCreateTableWithColumnWithClusteringColumnLessThanScalarDecimalConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 decimal CHECK ck1 < 4.2, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2)) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 2, 2, 3)");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 4.2, 2, 3)");
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 5, 2, 3)");
    }

    @Test
    public void testCreateTableWithColumnWithClusteringColumnBiggerThanScalarConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 int CHECK ck1 > 4, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2)) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 5, 2, 3)");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 1, 2, 3)");
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 4, 2, 3)");
    }

    @Test
    public void testCreateTableWithColumnWithClusteringColumnBiggerThanScalarDecimalConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 double CHECK ck1 > 4.2, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2)) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 5, 2, 3)");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 1, 2, 3)");
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 4.2, 2, 3)");
    }

    @Test
    public void testCreateTableWithColumnWithClusteringColumnBiggerOrEqualThanScalarConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 int CHECK ck1 >= 4, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2)) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 5, 2, 3)");
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 4, 2, 3)");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 1, 2, 3)");
    }

    @Test
    public void testCreateTableWithColumnWithClusteringColumnBiggerOrEqualThanScalarDecimalConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 decimal CHECK ck1 >= 4.2, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2)) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 5, 2, 3)");
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 4.2, 2, 3)");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 1, 2, 3)");
    }

    @Test
    public void testCreateTableWithColumnWithClusteringColumnLessOrEqualThanScalarConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 int CHECK ck1 <= 4, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2)) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 3, 2, 3)");
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 4, 2, 3)");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 5, 2, 3)");
    }

    @Test
    public void testCreateTableWithColumnWithClusteringColumnLessOrEqualThanScalarDecimalConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 decimal CHECK ck1 <= 4.2, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2)) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 3, 2, 3)");
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 4.2, 2, 3)");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 5, 2, 3)");
    }

    @Test
    public void testCreateTableWithColumnWithClusteringColumnDifferentThanScalarConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 int CHECK ck1 != 4, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2)) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 3, 2, 3)");
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 5, 2, 3)");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 4, 2, 3)");
    }

    @Test
    public void testCreateTableWithColumnWithClusteringColumnDifferentThanScalarDecimalConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 decimal CHECK ck1 != 4.2, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2)) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 3, 2, 3)");
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 5, 2, 3)");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 4.2, 2, 3)");
    }

    // FUNCTION
    @Test
    public void testCreateTableWithColumnWithClusteringColumnLengthEqualToConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 text CHECK LENGTH(ck1) = 4, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2)) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 'fooo', 2, 3)");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 'foo', 2, 3)");
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 'foooo', 2, 3)");
    }

    @Test
    public void testCreateTableWithColumnWithClusteringColumnLengthDifferentThanConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 text CHECK LENGTH(ck1) != 4, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2)) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 'foo', 2, 3)");
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 'foooo', 2, 3)");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 'fooo', 2, 3)");
    }

    @Test
    public void testCreateTableWithColumnWithClusteringColumnLengthBiggerThanConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 text CHECK LENGTH(ck1) > 4, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2)) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 'foooo', 2, 3)");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 'foo', 2, 3)");
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 'fooo', 2, 3)");
    }

    @Test
    public void testCreateTableWithColumnWithClusteringColumnLengthBiggerOrEqualThanConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 text CHECK LENGTH(ck1) >= 4, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2)) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 'foooo', 2, 3)");
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 'fooo', 2, 3)");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 'foo', 2, 3)");
    }

    @Test
    public void testCreateTableWithColumnWithClusteringColumnLengthSmallerThanConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 text CHECK LENGTH(ck1) < 4, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2)) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 'foo', 2, 3)");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 'fooo', 2, 3)");
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 'foooo', 2, 3)");
    }

    @Test
    public void testCreateTableWithColumnWithClusteringColumnLengthSmallerOrEqualThanConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 text CHECK LENGTH(ck1) <= 4, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2)) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 'foo', 2, 3)");
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 'fooo', 2, 3)");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 'foooo', 2, 3)");
    }


    @Test
    public void testCreateTableWithColumnWithPkColumnLengthEqualToConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk text CHECK LENGTH(pk) = 4, ck1 int, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2)) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES ('fooo', 1, 2, 3)");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES ('foo', 1, 2, 3)");
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES ('foooo', 1, 2, 3)");
    }

    @Test
    public void testCreateTableWithColumnWithPkColumnLengthDifferentThanConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk text CHECK LENGTH(pk) != 4, ck1 int, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2)) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES ('foo', 1, 2, 3)");
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES ('foooo', 1, 2, 3)");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES ('fooo', 1, 2, 3)");
    }

    @Test
    public void testCreateTableWithColumnWithPkColumnLengthBiggerThanConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk text CHECK LENGTH(pk) > 4, ck1 int, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2)) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES ('foooo', 1, 2, 3)");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES ('foo', 1, 2, 3)");
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES ('fooo', 1, 2, 3)");
    }

    @Test
    public void testCreateTableWithColumnWithPkColumnLengthBiggerOrEqualThanConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk text CHECK LENGTH(pk) >= 4, ck1 int, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2)) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES ('foooo', 1, 2, 3)");
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES ('fooo', 1, 2, 3)");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES ('foo', 1, 2, 3)");
    }

    @Test
    public void testCreateTableWithColumnWithPkColumnLengthSmallerThanConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk text CHECK LENGTH(pk) < 4, ck1 int, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2)) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES ('foo', 1, 2, 3)");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES ('fooo', 1, 2, 3)");
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES ('foooo', 1, 2, 3)");
    }

    @Test
    public void testCreateTableWithColumnWithPkColumnLengthSmallerOrEqualThanConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk text CHECK LENGTH(pk) <= 4, ck1 int, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2)) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES ('foo', 1, 2, 3)");
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES ('fooo', 1, 2, 3)");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES ('foooo', 1, 2, 3)");
    }


    @Test
    public void testCreateTableWithColumnWithRegularColumnLengthEqualToConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 int, ck2 int, v text CHECK LENGTH(v) = 4, PRIMARY KEY ((pk),ck1, ck2)) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 2, 3, 'fooo')");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 2, 3, 'foo')");
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 2, 3, 'foooo')");
    }

    @Test
    public void testCreateTableWithColumnWithRegularColumnLengthDifferentThanConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 int, ck2 int, v text CHECK LENGTH(v) != 4, PRIMARY KEY ((pk),ck1, ck2)) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 2, 3, 'foo')");
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 2, 3, 'foooo')");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 2, 3, 'fooo')");
    }

    @Test
    public void testCreateTableWithColumnWithRegularColumnLengthBiggerThanConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 int, ck2 int, v text CHECK LENGTH(v) > 4, PRIMARY KEY ((pk),ck1, ck2)) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 2, 3, 'foooo')");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 2, 3, 'foo')");
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 2, 3, 'fooo')");
    }

    @Test
    public void testCreateTableWithColumnWithRegularColumnLengthBiggerOrEqualThanConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 int, ck2 int, v text CHECK LENGTH(v) >= 4, PRIMARY KEY ((pk),ck1, ck2)) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 2, 3, 'foooo')");
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 2, 3, 'fooo')");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 2, 3, 'foo')");
    }

    @Test
    public void testCreateTableWithColumnWithRegularColumnLengthSmallerThanConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 int, ck2 int, v text CHECK LENGTH(v) < 4, PRIMARY KEY ((pk),ck1, ck2)) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 2, 3, 'foo')");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 2, 3, 'fooo')");
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 2, 3, 'foooo')");
    }

    @Test
    public void testCreateTableWithColumnWithRegularColumnLengthSmallerOrEqualThanConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 int, ck2 int, v text CHECK LENGTH(v) <= 4, PRIMARY KEY ((pk),ck1, ck2)) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 2, 3, 'foo')");
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 2, 3, 'fooo')");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 2, 3, 'foooo')");
    }


    @Test
    public void testCreateTableWithColumnMixedColumnsLengthConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk text CHECK LENGTH(pk) = 4, ck1 int, ck2 int, v text CHECK LENGTH(v) = 4, PRIMARY KEY ((pk),ck1, ck2)) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES ('fooo', 2, 3, 'fooo')");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES ('foo', 2, 3, 'foo')");
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES ('fooo', 2, 3, 'foo')");
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES ('foo', 2, 3, 'fooo')");
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES ('foooo', 2, 3, 'fooo')");
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES ('fooo', 2, 3, 'foooo')");
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES ('foooo', 2, 3, 'foooo')");
    }

    @Test
    public void testCreateTableWithWrongColumnConstraint() throws Throwable
    {
        try
        {
            createTable("CREATE TABLE %s (pk text, ck1 int CHECK LENGTH(pk) = 4, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2)) WITH CLUSTERING ORDER BY (ck1 ASC);");
            fail();
        }
        catch (InvalidRequestException e)
        {
            assertTrue(e.getCause() instanceof ConstraintInvalidException);
            assertTrue(e.getMessage().contains("Error setting schema for test"));
        }
    }

    @Test
    public void testCreateTableWithColumnWithClusteringColumnInvalidTypeConstraint() throws Throwable
    {
        try
        {
            createTable("CREATE TABLE %s (pk int, ck1 int CHECK LENGTH(ck1) = 4, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2)) WITH CLUSTERING ORDER BY (ck1 ASC);");
            fail();
        }
        catch (InvalidRequestException e)
        {
            assertTrue(e.getCause() instanceof ConstraintInvalidException);
            assertTrue(e.getMessage().contains("Error setting schema for test"));
        }
    }
}