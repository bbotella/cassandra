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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import com.datastax.driver.core.ResultSet;
import org.apache.cassandra.cql3.ConstraintInvalidException;
import org.apache.cassandra.cql3.ConstraintViolationException;
import org.apache.cassandra.exceptions.InvalidRequestException;
import org.apache.cassandra.exceptions.SyntaxException;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.quicktheories.QuickTheory.qt;
import static org.quicktheories.generators.SourceDSL.doubles;
import static org.quicktheories.generators.SourceDSL.integers;

public class CreateTableWithTableCqlConstraintValidationTest extends CqlConstraintValidationTester
{

    @Test
    public void testCreateTableWithTableNamedConstraintDescribeTableNonFunction() throws Throwable
    {
        String table = createTable(KEYSPACE_PER_TEST, "CREATE TABLE %s (pk int, ck1 int, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK ck1 < 100) WITH CLUSTERING ORDER BY (ck1 ASC);");

        String tableCreateStatement = "CREATE TABLE " + KEYSPACE_PER_TEST + "." + table + " (\n" +
                                      "    pk int,\n" +
                                      "    ck1 int,\n" +
                                      "    ck2 int,\n" +
                                      "    v int,\n" +
                                      "    PRIMARY KEY (pk, ck1, ck2),\n" +
                                      "    CONSTRAINT cons1 CHECK ck1 < 100\n" +
                                      ") WITH CLUSTERING ORDER BY (ck1 ASC, ck2 ASC)\n" +
                                      "    AND " + tableParametersCql();

        assertRowsNet(executeDescribeNet("DESCRIBE TABLE " + KEYSPACE_PER_TEST + "." + table),
                      row(KEYSPACE_PER_TEST,
                          "table",
                          table,
                          tableCreateStatement));
    }

    @Test
    public void testCreateTableWithTableNotNamedConstraintDescribeTableNonFunction() throws Throwable
    {
        String table = createTable(KEYSPACE_PER_TEST, "CREATE TABLE %s (pk int, ck1 int, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT CHECK ck1 < 100) WITH CLUSTERING ORDER BY (ck1 ASC);");

        Pattern p = Pattern.compile("(.*)CONSTRAINT (.+) CHECK(.*)");

        ResultSet describeResultSet = executeDescribeNet("DESCRIBE TABLE " + KEYSPACE_PER_TEST + "." + table);
        Matcher m = p.matcher(describeResultSet.one().toString());
        m.find();
        String text = m.group(2);

        String tableCreateStatement = String.format("CREATE TABLE " + KEYSPACE_PER_TEST + "." + table + " (\n" +
                                      "    pk int,\n" +
                                      "    ck1 int,\n" + "    ck2 int,\n" +
                                      "    v int,\n" +
                                      "    PRIMARY KEY (pk, ck1, ck2),\n" +
                                      "    CONSTRAINT %s CHECK ck1 < 100\n" +
                                      ") WITH CLUSTERING ORDER BY (ck1 ASC, ck2 ASC)\n" +
                                      "    AND " + tableParametersCql(), text);

        assertRowsContains(executeDescribeNet("DESCRIBE TABLE " + KEYSPACE_PER_TEST + "." + table),
                      row(KEYSPACE_PER_TEST,
                          "table",
                          table,
                          tableCreateStatement));
    }

    @Test
    public void testCreateTableWithColumnNotNamedConstraintDescribeTableFunction() throws Throwable
    {
        String table = createTable(KEYSPACE_PER_TEST, "CREATE TABLE %s (pk int, ck1 text, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK LENGTH(ck1) = 4) WITH CLUSTERING ORDER BY (ck1 ASC);");

        String tableCreateStatement = "CREATE TABLE " + KEYSPACE_PER_TEST + "." + table + " (\n" +
                                      "    pk int,\n" +
                                      "    ck1 text,\n" +
                                      "    ck2 int,\n" +
                                      "    v int,\n" +
                                      "    PRIMARY KEY (pk, ck1, ck2),\n" +
                                      "    CONSTRAINT cons1 CHECK LENGTH(ck1) = 4\n" +
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
    public void testCreateTableWithColumnWithClusteringColumnLessThanScalarConstraintDouble() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 double, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK ck1 < 4) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        qt().forAll(doubles().between(0, 3.99))
            .checkAssert(d -> execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, " + d + ", 3, 4)"));

        // Invalid
        qt().forAll(doubles().between(4.01, 100))
            .checkAssert(d -> {
                try
                {
                    assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, " + d + ", 3, 4)");
                }
                catch (Throwable e)
                {
                    // Wrapping throwable, needed by qt
                    throw new RuntimeException(e);
                }
            });
    }

    @Test
    public void testCreateTableWithColumnWithClusteringColumnLessThanScalarConstraintFloat() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 float, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK ck1 < 4) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        qt().forAll(doubles().between(0, 3.99))
            .checkAssert(d -> execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, " + d + ", 3, 4)"));

        // Invalid
        qt().forAll(doubles().between(4.01, 100))
            .checkAssert(d -> {
                try
                {
                    assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, " + d + ", 3, 4)");
                }
                catch (Throwable e)
                {
                    // Wrapping throwable, needed by qt
                    throw new RuntimeException(e);
                }
            });
    }

    @Test
    public void testCreateTableWithColumnWithClusteringColumnLessThanScalarConstraintDecimal() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 decimal, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK ck1 < 4) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        qt().forAll(doubles().between(0, 3.99))
            .checkAssert(d -> execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, " + d + ", 3, 4)"));

        // Invalid
        qt().forAll(doubles().between(4.01, 100))
            .checkAssert(d -> {
                try
                {
                    assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, " + d + ", 3, 4)");
                }
                catch (Throwable e)
                {
                    // Wrapping throwable, needed by qt
                    throw new RuntimeException(e);
                }
            });
    }

    @Test
    public void testCreateTableWithColumnWithClusteringColumnLessThanScalarConstraintInt() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 int, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK ck1 < 4) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        qt().forAll(integers().between(0, 3))
            .checkAssert(d -> execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, " + d + ", 3, 4)"));

        // Invalid
        qt().forAll(integers().between(4, 100))
            .checkAssert(d -> {
                try
                {
                    assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, " + d + ", 3, 4)");
                }
                catch (Throwable e)
                {
                    // Wrapping throwable, needed by qt
                    throw new RuntimeException(e);
                }
            });
    }

    @Test
    public void testCreateTableWithColumnWithClusteringColumnLessThanScalarConstraintSmallInt() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 smallint, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK ck1 < 4) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        qt().forAll(integers().between(0, 3))
            .checkAssert(d -> execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, " + d + ", 3, 4)"));

        // Invalid
        qt().forAll(integers().between(4, 100))
            .checkAssert(d -> {
                try
                {
                    assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, " + d + ", 3, 4)");
                }
                catch (Throwable e)
                {
                    // Wrapping throwable, needed by qt
                    throw new RuntimeException(e);
                }
            });
    }

    @Test
    public void testCreateTableWithColumnWithClusteringColumnBiggerThanScalarConstraintDouble() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 double, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK ck1 > 4) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        qt().forAll(doubles().between(4.01, 100))
            .checkAssert(d -> execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, " + d + ", 3, 4)"));

        // Invalid
        qt().forAll(doubles().between(0, 4))
            .checkAssert(d -> {
                try
                {
                    assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, " + d + ", 3, 4)");
                }
                catch (Throwable e)
                {
                    // Wrapping throwable, needed by qt
                    throw new RuntimeException(e);
                }
            });
    }

    @Test
    public void testCreateTableWithColumnWithClusteringColumnBiggerThanScalarConstraintFloat() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 float, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK ck1 > 4) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        qt().forAll(doubles().between(4.01, 100))
            .checkAssert(d -> execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, " + d + ", 3, 4)"));

        // Invalid
        qt().forAll(doubles().between(0, 4))
            .checkAssert(d -> {
                try
                {
                    assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, " + d + ", 3, 4)");
                }
                catch (Throwable e)
                {
                    // Wrapping throwable, needed by qt
                    throw new RuntimeException(e);
                }
            });
    }

    @Test
    public void testCreateTableWithColumnWithClusteringColumnBiggerThanScalarConstraintDecimal() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 decimal, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK ck1 > 4) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        qt().forAll(doubles().between(4.01, 100))
            .checkAssert(d -> execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, " + d + ", 3, 4)"));

        // Invalid
        qt().forAll(doubles().between(0, 4))
            .checkAssert(d -> {
                try
                {
                    assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, " + d + ", 3, 4)");
                }
                catch (Throwable e)
                {
                    // Wrapping throwable, needed by qt
                    throw new RuntimeException(e);
                }
            });
    }

    @Test
    public void testCreateTableWithColumnWithClusteringColumnBiggerThanScalarConstraintInt() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 int, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK ck1 > 4) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        qt().forAll(integers().between(5, 100))
            .checkAssert(d -> execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, " + d + ", 3, 4)"));

        // Invalid
        qt().forAll(integers().between(0, 4))
            .checkAssert(d -> {
                try
                {
                    assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, " + d + ", 3, 4)");
                }
                catch (Throwable e)
                {
                    // Wrapping throwable, needed by qt
                    throw new RuntimeException(e);
                }
            });
    }

    @Test
    public void testCreateTableWithColumnWithClusteringColumnBiggerThanScalarConstraintSmallInt() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 smallint, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK ck1 > 4) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        qt().forAll(integers().between(5, 100))
            .checkAssert(d -> execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, " + d + ", 3, 4)"));

        // Invalid
        qt().forAll(integers().between(0, 4))
            .checkAssert(d -> {
                try
                {
                    assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, " + d + ", 3, 4)");
                }
                catch (Throwable e)
                {
                    // Wrapping throwable, needed by qt
                    throw new RuntimeException(e);
                }
            });
    }

    @Test
    public void testCreateTableWithColumnWithClusteringColumnBiggerOrEqualThanScalarConstraintDouble() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 double, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK ck1 >= 4) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        qt().forAll(doubles().between(4, 100))
            .checkAssert(d -> execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, " + d + ", 3, 4)"));

        // Invalid
        qt().forAll(doubles().between(0, 3.99))
            .checkAssert(d -> {
                try
                {
                    assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, " + d + ", 3, 4)");
                }
                catch (Throwable e)
                {
                    // Wrapping throwable, needed by qt
                    throw new RuntimeException(e);
                }
            });
    }

    @Test
    public void testCreateTableWithColumnWithClusteringColumnBiggerOrEqualThanScalarConstraintFloat() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 float, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK ck1 >= 4) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        qt().forAll(doubles().between(4, 100))
            .checkAssert(d -> execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, " + d + ", 3, 4)"));

        // Invalid
        qt().forAll(doubles().between(0, 3.99))
            .checkAssert(d -> {
                try
                {
                    assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, " + d + ", 3, 4)");
                }
                catch (Throwable e)
                {
                    // Wrapping throwable, needed by qt
                    throw new RuntimeException(e);
                }
            });
    }

    @Test
    public void testCreateTableWithColumnWithClusteringColumnBiggerOrEqualThanScalarConstraintDecimal() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 decimal, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK ck1 >= 4) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        qt().forAll(doubles().between(4, 100))
            .checkAssert(d -> execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, " + d + ", 3, 4)"));

        // Invalid
        qt().forAll(doubles().between(0, 3.99))
            .checkAssert(d -> {
                try
                {
                    assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, " + d + ", 3, 4)");
                }
                catch (Throwable e)
                {
                    // Wrapping throwable, needed by qt
                    throw new RuntimeException(e);
                }
            });
    }

    @Test
    public void testCreateTableWithColumnWithClusteringColumnBiggerOrEqualThanScalarConstraintInt() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 int, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK ck1 >= 4) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        qt().forAll(integers().between(4, 100))
            .checkAssert(d -> execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, " + d + ", 3, 4)"));

        // Invalid
        qt().forAll(integers().between(0, 3))
            .checkAssert(d -> {
                try
                {
                    assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, " + d + ", 3, 4)");
                }
                catch (Throwable e)
                {
                    // Wrapping throwable, needed by qt
                    throw new RuntimeException(e);
                }
            });
    }

    @Test
    public void testCreateTableWithColumnWithClusteringColumnBiggerOrEqualThanScalarConstraintSmallInt() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 smallint, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK ck1 >= 4) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        qt().forAll(integers().between(4, 100))
            .checkAssert(d -> execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, " + d + ", 3, 4)"));

        // Invalid
        qt().forAll(integers().between(0, 3))
            .checkAssert(d -> {
                try
                {
                    assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, " + d + ", 3, 4)");
                }
                catch (Throwable e)
                {
                    // Wrapping throwable, needed by qt
                    throw new RuntimeException(e);
                }
            });
    }

    @Test
    public void testCreateTableWithColumnWithClusteringColumnLessOrEqualThanScalarConstraintDouble() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 double, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK ck1 <= 4) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        qt().forAll(doubles().between(0, 4))
            .checkAssert(d -> execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, " + d + ", 3, 4)"));

        // Invalid
        qt().forAll(doubles().between(4.01, 100))
            .checkAssert(d -> {
                try
                {
                    assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, " + d + ", 3, 4)");
                }
                catch (Throwable e)
                {
                    // Wrapping throwable, needed by qt
                    throw new RuntimeException(e);
                }
            });
    }

    @Test
    public void testCreateTableWithColumnWithClusteringColumnLessOrEqualThanScalarConstraintFloat() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 float, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK ck1 <= 4) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        qt().forAll(doubles().between(0, 4))
            .checkAssert(d -> execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, " + d + ", 3, 4)"));

        // Invalid
        qt().forAll(doubles().between(4.01, 100))
            .checkAssert(d -> {
                try
                {
                    assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, " + d + ", 3, 4)");
                }
                catch (Throwable e)
                {
                    // Wrapping throwable, needed by qt
                    throw new RuntimeException(e);
                }
            });
    }

    @Test
    public void testCreateTableWithColumnWithClusteringColumnLessOrEqualThanScalarConstraintDecimal() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 decimal, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK ck1 <= 4) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        qt().forAll(doubles().between(0, 4))
            .checkAssert(d -> execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, " + d + ", 3, 4)"));

        // Invalid
        qt().forAll(doubles().between(4.01, 100))
            .checkAssert(d -> {
                try
                {
                    assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, " + d + ", 3, 4)");
                }
                catch (Throwable e)
                {
                    // Wrapping throwable, needed by qt
                    throw new RuntimeException(e);
                }
            });
    }

    @Test
    public void testCreateTableWithColumnWithClusteringColumnLessOrEqualThanScalarConstraintInt() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 int, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK ck1 <= 4) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        qt().forAll(integers().between(0, 4))
            .checkAssert(d -> execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, " + d + ", 3, 4)"));

        // Invalid
        qt().forAll(integers().between(5, 100))
            .checkAssert(d -> {
                try
                {
                    assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, " + d + ", 3, 4)");
                }
                catch (Throwable e)
                {
                    // Wrapping throwable, needed by qt
                    throw new RuntimeException(e);
                }
            });
    }

    @Test
    public void testCreateTableWithColumnWithClusteringColumnLessOrEqualThanScalarConstraintSmallInt() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 smallint, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK ck1 <= 4) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        qt().forAll(integers().between(0, 4))
            .checkAssert(d -> execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, " + d + ", 3, 4)"));

        // Invalid
        qt().forAll(integers().between(5, 100))
            .checkAssert(d -> {
                try
                {
                    assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, " + d + ", 3, 4)");
                }
                catch (Throwable e)
                {
                    // Wrapping throwable, needed by qt
                    throw new RuntimeException(e);
                }
            });
    }

    @Test
    public void testCreateTableWithColumnWithClusteringColumnDifferentThanScalarConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 int, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK ck1 != 4) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 3, 2, 3)");
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 5, 2, 3)");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 4, 2, 3)");
    }


    // FUNCTION
    @Test
    public void testCreateTableWithTableLengthEqualToConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 text, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK LENGTH(ck1) = 4) WITH CLUSTERING ORDER BY (ck1 ASC) ");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 'fooo', 2, 3)");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 'foo', 2, 3)");
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 'foooo', 2, 3)");
    }

    @Test
    public void testCreateTableWithColumnWithClusteringColumnLengthDifferentThanConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 text, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK LENGTH(ck1) != 4) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 'foo', 2, 3)");
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 'foooo', 2, 3)");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 'fooo', 2, 3)");
    }

    @Test
    public void testCreateTableWithColumnWithClusteringColumnLengthBiggerThanConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 text, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK LENGTH(ck1) > 4) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 'foooo', 2, 3)");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 'foo', 2, 3)");
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 'fooo', 2, 3)");
    }

    @Test
    public void testCreateTableWithColumnWithClusteringColumnLengthBiggerOrEqualThanConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 text, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK LENGTH(ck1) >= 4) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 'foooo', 2, 3)");
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 'fooo', 2, 3)");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 'foo', 2, 3)");
    }

    @Test
    public void testCreateTableWithColumnWithClusteringColumnLengthSmallerThanConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 text, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK LENGTH(ck1) < 4) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 'foo', 2, 3)");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 'fooo', 2, 3)");
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 'foooo', 2, 3)");
    }

    @Test
    public void testCreateTableWithColumnWithClusteringColumnLengthSmallerOrEqualThanConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 text, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK LENGTH(ck1) <= 4) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 'foo', 2, 3)");
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 'fooo', 2, 3)");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 'foooo', 2, 3)");
    }


    @Test
    public void testCreateTableWithColumnWithPkColumnLengthEqualToConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk text, ck1 int, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK LENGTH(pk) = 4) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES ('fooo', 1, 2, 3)");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES ('foo', 1, 2, 3)");
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES ('foooo', 1, 2, 3)");
    }

    @Test
    public void testCreateTableWithColumnWithPkColumnLengthDifferentThanConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk text, ck1 int, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK LENGTH(pk) != 4) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES ('foo', 1, 2, 3)");
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES ('foooo', 1, 2, 3)");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES ('fooo', 1, 2, 3)");
    }

    @Test
    public void testCreateTableWithColumnWithPkColumnLengthBiggerThanConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk text, ck1 int, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK LENGTH(pk) > 4) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES ('foooo', 1, 2, 3)");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES ('foo', 1, 2, 3)");
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES ('fooo', 1, 2, 3)");
    }

    @Test
    public void testCreateTableWithColumnWithPkColumnLengthBiggerOrEqualThanConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk text, ck1 int, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK LENGTH(pk) >= 4) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES ('foooo', 1, 2, 3)");
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES ('fooo', 1, 2, 3)");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES ('foo', 1, 2, 3)");
    }

    @Test
    public void testCreateTableWithColumnWithPkColumnLengthSmallerThanConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk text, ck1 int, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK LENGTH(pk) < 4) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES ('foo', 1, 2, 3)");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES ('fooo', 1, 2, 3)");
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES ('foooo', 1, 2, 3)");
    }

    @Test
    public void testCreateTableWithColumnWithPkColumnLengthSmallerOrEqualThanConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk text, ck1 int, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK LENGTH(pk) <= 4) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES ('foo', 1, 2, 3)");
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES ('fooo', 1, 2, 3)");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES ('foooo', 1, 2, 3)");
    }


    @Test
    public void testCreateTableWithColumnWithRegularColumnLengthEqualToConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 int, ck2 int, v text, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK LENGTH(v) = 4) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 2, 3, 'fooo')");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 2, 3, 'foo')");
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 2, 3, 'foooo')");
    }

    @Test
    public void testCreateTableWithColumnWithRegularColumnLengthDifferentThanConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 int, ck2 int, v text, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK LENGTH(v) != 4) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 2, 3, 'foo')");
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 2, 3, 'foooo')");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 2, 3, 'fooo')");
    }

    @Test
    public void testCreateTableWithColumnWithRegularColumnLengthBiggerThanConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 int, ck2 int, v text, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK LENGTH(v) > 4) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 2, 3, 'foooo')");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 2, 3, 'foo')");
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 2, 3, 'fooo')");
    }

    @Test
    public void testCreateTableWithColumnWithRegularColumnLengthBiggerOrEqualThanConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 int, ck2 int, v text, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK LENGTH(v) >= 4) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 2, 3, 'foooo')");
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 2, 3, 'fooo')");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 2, 3, 'foo')");
    }

    @Test
    public void testCreateTableWithColumnWithRegularColumnLengthSmallerThanConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 int, ck2 int, v text, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK LENGTH(v) < 4) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 2, 3, 'foo')");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 2, 3, 'fooo')");
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 2, 3, 'foooo')");
    }

    @Test
    public void testCreateTableWithColumnWithRegularColumnLengthSmallerOrEqualThanConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 int, ck2 int, v text, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK LENGTH(v) <= 4) WITH CLUSTERING ORDER BY (ck1 ASC);");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 2, 3, 'foo')");
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 2, 3, 'fooo')");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 2, 3, 'foooo')");
    }

    @Test
    public void testCreateTableWithColumnWithDuplicatedConstraintError() throws Throwable
    {
        // Invalid
        assertInvalidThrow(SyntaxException.class, "CREATE TABLE %s (pk int, ck1 int, ck2 int, v text, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK LENGTH(v) <= 4,  CONSTRAINT cons1 CHECK ck1 <= 4) WITH CLUSTERING ORDER BY (ck1 ASC);");
    }


    @Test
    public void testCreateTableWithColumnMixedColumnsLengthConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk text CHECK LENGTH(pk) = 4, ck1 int, ck2 int, v text, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK LENGTH(v) = 4) WITH CLUSTERING ORDER BY (ck1 ASC);");

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
            createTable("CREATE TABLE %s (pk int, ck1 int, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK LENGTH(pk) = 4) WITH CLUSTERING ORDER BY (ck1 ASC);");
            fail();
        }
        catch (InvalidRequestException e)
        {
            assertTrue(e.getCause() instanceof ConstraintInvalidException);
            assertTrue(e.getMessage().contains("Error setting schema for test"));
        }
    }

    @Test
    public void testCreateTableWithWrongTextColumnConstraint() throws Throwable
    {
        try
        {
            createTable("CREATE TABLE %s (pk text, ck1 int, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK pk < 4) WITH CLUSTERING ORDER BY (ck1 ASC);");
            fail();
        }
        catch (InvalidRequestException e)
        {
            assertTrue(e.getCause() instanceof ConstraintInvalidException);
            assertTrue(e.getMessage().contains("Error setting schema for test"));
        }
    }

    // Mixed
    @Test
    public void testCreateTableWithTableLengthDifferentConstraint() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int, ck1 text, ck2 int, v int, PRIMARY KEY ((pk),ck1, ck2), CONSTRAINT cons1 CHECK LENGTH(ck1) = 4, CONSTRAINT cons2 CHECK ck2 > 4) WITH CLUSTERING ORDER BY (ck1 ASC) ");

        // Valid
        execute("INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 'fooo', 5, 3)");

        // Invalid
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 'fooo', 2, 3)");
        assertInvalidThrow(ConstraintViolationException.class, "INSERT INTO %s (pk, ck1, ck2, v) VALUES (1, 'foooo', 5, 3)");
    }

    @Test
    public void testCreateTableWithUnreservedNameColumns() throws Throwable
    {
        createTable("CREATE TABLE %s (pk int PRIMARY KEY, length int, CONSTRAINT cons1 CHECK length < 100);");
        createTable("CREATE TABLE %s (pk int PRIMARY KEY, length text, CONSTRAINT cons1 CHECK LENGTH(length) < 100);");
    }
}