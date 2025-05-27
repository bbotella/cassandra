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

import org.apache.commons.cli.*;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.utils.FBUtilities;
import org.apache.commons.cli.*;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.utils.FBUtilities;
import org.apache.cassandra.io.sstable.Descriptor;
import org.apache.cassandra.io.sstable.format.SSTableReader;
import org.apache.cassandra.io.sstable.ISSTableScanner;
import org.apache.commons.cli.*;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.utils.FBUtilities;
import org.apache.cassandra.io.sstable.Descriptor;
import org.apache.cassandra.io.sstable.format.SSTableReader;
import org.apache.cassandra.io.sstable.ISSTableScanner;
import org.apache.cassandra.schema.TableMetadata;
import org.apache.cassandra.schema.TableMetadataRef;
import org.apache.cassandra.io.util.File; // Use Cassandra's File wrapper
import java.io.IOException; // Added for specific exception handling

import org.apache.avro.Schema;
// import org.apache.avro.SchemaBuilder; // SchemaBuilder might not be needed if constructing manually
import org.apache.cassandra.db.marshal.*;
import org.apache.cassandra.db.marshal.UserType; // Already there
import org.apache.cassandra.db.marshal.TupleType; // Already there
import org.apache.cassandra.schema.ColumnMetadata;

import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.cassandra.db.rows.Row;
import org.apache.cassandra.db.rows.UnfilteredRowIterator;
import org.apache.cassandra.db.rows.Cell;
// import org.apache.cassandra.db.rows.CellPath; // May not be needed for initial frozen collection handling
import org.apache.cassandra.db.marshal.UTF8Type;
import org.apache.cassandra.db.marshal.AsciiType;
import org.apache.cassandra.db.marshal.VarcharType;
import org.apache.cassandra.db.Clustering;
// import org.apache.cassandra.db.LivenessInfo; // May not be directly needed for value conversion
import org.apache.cassandra.cql3.functions.types.ProtocolVersion; // For collection deserialization

import java.nio.ByteBuffer;
import java.util.ArrayList; // Already there
import java.util.HashMap;
import java.util.List; // Already there
import java.util.Map;
import java.util.stream.Collectors; // Already there


import static org.apache.cassandra.config.CassandraRelevantProperties.TEST_UTIL_ALLOW_TOOL_REINIT_FOR_TEST;

public class SSTableToParquet
{
    static
    {
        FBUtilities.preventIllegalAccessWarnings();
    }

    private static final Options options = new Options();
    private static CommandLine cmd;

    private static final String INPUT_SSTABLE_OPTION = "i";
    private static final String OUTPUT_PARQUET_FILE_OPTION = "o";
    private static final String HELP_OPTION = "h";

    static
    {
        // Initialize DatabaseDescriptor for offline tool usage
        // Essential for schema loading and other Cassandra internal operations
        DatabaseDescriptor.toolInitialization(!TEST_UTIL_ALLOW_TOOL_REINIT_FOR_TEST.getBoolean());

        Option inputSSTable = new Option(INPUT_SSTABLE_OPTION, true, "Input SSTable Data.db file path (required)");
        inputSSTable.setRequired(true);
        options.addOption(inputSSTable);

        Option outputParquetFile = new Option(OUTPUT_PARQUET_FILE_OPTION, true, "Output Parquet file path (required)");
        outputParquetFile.setRequired(true);
        options.addOption(outputParquetFile);
        
        Option help = new Option(HELP_OPTION, false, "Display help information");
        options.addOption(help);
    }

    public static void main(String[] args)
    {
        CommandLineParser parser = new PosixParser();
        try
        {
            cmd = parser.parse(options, args);

            if (cmd.hasOption(HELP_OPTION) || args.length == 0)
            {
                printUsage();
                System.exit(0);
            }

            String inputFile = cmd.getOptionValue(INPUT_SSTABLE_OPTION);
            String outputFile = cmd.getOptionValue(OUTPUT_PARQUET_FILE_OPTION);

            System.out.println("Input SSTable: " + inputFile);
            System.out.println("Output Parquet File: " + outputFile);

            File ssTableFile = new File(inputFile);
            if (!ssTableFile.exists())
            {
                System.err.println("Cannot find SSTable file: " + ssTableFile.absolutePath());
                System.exit(1);
            }

            Descriptor desc = Descriptor.fromFileWithComponent(ssTableFile, false).left;
            TableMetadata metadata = Util.metadataFromSSTable(desc); // This can throw IOException

            System.out.println("Successfully loaded schema for table: " + metadata.keyspace + "." + metadata.name);
            System.out.println("Partition Key Columns: " + metadata.partitionKeyColumns());
            System.out.println("Clustering Columns: " + metadata.clusteringColumns());
            System.out.println("Regular Columns: " + metadata.regularColumns());

            Schema avroSchema = convertCassandraToAvroSchema(metadata, metadata.name);
            System.out.println("Generated Avro Schema:");
            System.out.println(avroSchema.toString(true)); // true for pretty print

            // SSTableReader and ISSTableScanner should be declared here to be accessible in finally
            SSTableReader sstable = null;
            ISSTableScanner scanner = null;
            ParquetWriter<GenericRecord> parquetWriter = null;

            try {
                sstable = SSTableReader.openNoValidation(null, desc, TableMetadataRef.forOfflineTools(metadata));
                scanner = sstable.getScanner();

                // Initialize ParquetWriter
                Path outputPath = new Path(outputFile);
                Configuration conf = new Configuration(); // Use default Hadoop configuration
                parquetWriter = AvroParquetWriter.<GenericRecord>builder(outputPath)
                        .withSchema(avroSchema)
                        .withConf(conf)
                        .withCompressionCodec(CompressionCodecName.SNAPPY) // Or GZIP, etc.
                        .build();

                System.out.println("SSTable opened: " + sstable.getFilename());
                System.out.println("Parquet writer initialized. Starting data conversion...");

                long rowCount = 0;
                while (scanner.hasNext()) {
                    UnfilteredRowIterator partition = scanner.next();
                    // We are interested in live rows, not tombstones for Parquet typically
                    if (partition.staticRow() != Row.EMPTY_ROW && !partition.staticRow().isEmpty(metadata)) {
                         if (!partition.staticRow().deletion().isLive()) continue; // Skip deleted static row
                         GenericRecord staticRecord = convertRowToAvroRecord(partition.staticRow(), partition, metadata, avroSchema, true);
                         if (staticRecord != null) {
                            parquetWriter.write(staticRecord);
                            rowCount++; // Note: This might overcount if static data is part of every row in Parquet
                         }
                    }

                    while (partition.hasNext()) {
                        org.apache.cassandra.db.rows.Unfiltered unfiltered = partition.next();
                        if (unfiltered instanceof Row) {
                            Row row = (Row) unfiltered;
                            if (row.deletion().isLive()) { // Process only live rows
                                GenericRecord record = convertRowToAvroRecord(row, partition, metadata, avroSchema, false);
                                if (record != null) {
                                    parquetWriter.write(record);
                                    rowCount++;
                                }
                            }
                        }
                        // Not processing range tombstones for Parquet output in this version
                    }
                }
                System.out.println("Conversion complete. Total rows/records written: " + rowCount);

            } catch (IOException e) {
                System.err.println("IOException during SSTable processing or Parquet writing: " + e.getMessage());
                e.printStackTrace(System.err);
                System.exit(1); // Ensure exit on error
            } finally {
                try {
                    if (scanner != null) scanner.close();
                    if (sstable != null) sstable.close();
                    if (parquetWriter != null) parquetWriter.close();
                } catch (IOException e) {
                    System.err.println("Error closing resources: " + e.getMessage());
                    e.printStackTrace(System.err);
                }
            }
        }
        catch (ParseException e)
        {
            System.err.println("Error parsing command line arguments: " + e.getMessage());
            printUsage();
            System.exit(1);
        }
        catch (IOException e) // Catch for Util.metadataFromSSTable
        {
            System.err.println("Error during schema loading or initial SSTable access: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
        catch (Exception e)
        {
            System.err.println("An unexpected error occurred: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
        System.exit(0);
    }

    private static void printUsage()
    {
        String usage = String.format("sstable2parquet -i <sstable_path> -o <parquet_path> [options]%n" +
                                     "Version: %s%n", FBUtilities.getReleaseVersionString());
        String header = String.format("%nDump contents of a Cassandra SSTable to Apache Parquet format.%n%nOptions:");
        new HelpFormatter().printHelp(usage, header, options, "");
    }

    private static Schema convertCassandraToAvroSchema(TableMetadata cassandraSchema, String recordName) {
        List<Schema.Field> fields = new ArrayList<>();

        // Add partition key columns
        for (ColumnMetadata column : cassandraSchema.partitionKeyColumns()) {
            fields.add(new Schema.Field(column.name.toString(), getAvroSchemaForType(column.type), "Partition key column", null));
        }

        // Add clustering columns
        for (ColumnMetadata column : cassandraSchema.clusteringColumns()) {
            fields.add(new Schema.Field(column.name.toString(), getAvroSchemaForType(column.type), "Clustering column", null));
        }

        // Add regular columns (static and non-static)
        for (ColumnMetadata column : cassandraSchema.regularColumns()) {
            // Parquet/Avro typically doesn't differentiate static, handle all as regular fields
            fields.add(new Schema.Field(column.name.toString(), getAvroSchemaForType(column.type), "Regular column", null));
        }
        
        // Create a combined "row" record for all columns if no specific record name is given
        // For UDTs, recordName would be the UDT name. For the main table, it could be the table name.
        String avroRecordName = recordName == null || recordName.isEmpty() ? cassandraSchema.name : recordName;
        // Sanitize record name for Avro (must start with [A-Za-z_] and contain only [A-Za-z0-9_])
        String sanitizedRecordName = avroRecordName.replaceAll("[^A-Za-z0-9_]", "_");
        if (sanitizedRecordName.isEmpty()) { // handle case where name becomes empty after sanitization
            sanitizedRecordName = "default_record_name";
        } else if (Character.isDigit(sanitizedRecordName.charAt(0))) {
            sanitizedRecordName = "_" + sanitizedRecordName;
        }


        return Schema.createRecord(sanitizedRecordName, "Schema for Cassandra table " + cassandraSchema.keyspace + "." + cassandraSchema.name, cassandraSchema.keyspace, false, fields);
    }

    private static Schema getAvroSchemaForType(AbstractType<?> cassandraType) {
        // Handle nulls by making types nullable (union with null)
        Schema baseSchema;
        if (cassandraType instanceof ReversedType<?>) {
            cassandraType = ((ReversedType<?>) cassandraType).baseType;
        }

        if (cassandraType instanceof AsciiType) {
            baseSchema = Schema.create(Schema.Type.STRING);
        } else if (cassandraType instanceof LongType || cassandraType instanceof CounterColumnType) {
            baseSchema = Schema.create(Schema.Type.LONG);
        } else if (cassandraType instanceof BytesType) {
            baseSchema = Schema.create(Schema.Type.BYTES);
        } else if (cassandraType instanceof BooleanType) {
            baseSchema = Schema.create(Schema.Type.BOOLEAN);
        } else if (cassandraType instanceof DateType || cassandraType instanceof SimpleDateType) { // SimpleDateType for C* 2.x Date
            baseSchema = Schema.create(Schema.Type.INT); // Avro logical type 'date'
            // LogicalTypes.date().addToSchema(baseSchema); // Requires Avro 1.8+
        } else if (cassandraType instanceof DecimalType) {
            // Avro doesn't have a direct decimal type without logical type.
            // Representing as bytes with precision/scale in properties is one way, or string.
            // For simplicity, using string for now. Or use Bytes and store precision/scale elsewhere.
            baseSchema = Schema.create(Schema.Type.STRING); // Or Schema.create(Schema.Type.BYTES) with logical type
        } else if (cassandraType instanceof DoubleType) {
            baseSchema = Schema.create(Schema.Type.DOUBLE);
        } else if (cassandraType instanceof FloatType) {
            baseSchema = Schema.create(Schema.Type.FLOAT);
        } else if (cassandraType instanceof InetAddressType) {
            baseSchema = Schema.create(Schema.Type.STRING);
        } else if (cassandraType instanceof Int32Type) {
            baseSchema = Schema.create(Schema.Type.INT);
        } else if (cassandraType instanceof LexicalUUIDType || cassandraType instanceof TimeUUIDType || cassandraType instanceof UUIDType) {
            baseSchema = Schema.create(Schema.Type.STRING); // Avro logical type 'uuid'
            // LogicalTypes.uuid().addToSchema(baseSchema); // Requires Avro 1.8+
        } else if (cassandraType instanceof ShortType) {
            baseSchema = Schema.create(Schema.Type.INT); // Avro has no short, map to int
        } else if (cassandraType instanceof ByteType) {
            baseSchema = Schema.create(Schema.Type.INT); // Avro has no byte, map to int (representing ubyte)
        } else if (cassandraType instanceof TimestampType) {
            baseSchema = Schema.create(Schema.Type.LONG); // Avro logical type 'timestamp-micros' or 'timestamp-millis'
            // LogicalTypes.timestampMicros().addToSchema(baseSchema); // Requires Avro 1.8+
        } else if (cassandraType instanceof TimeType) {
            baseSchema = Schema.create(Schema.Type.LONG); // Avro logical type 'time-micros'
             // LogicalTypes.timeMicros().addToSchema(baseSchema); // Requires Avro 1.8+
        } else if (cassandraType instanceof UTF8Type || cassandraType instanceof VarcharType) {
            baseSchema = Schema.create(Schema.Type.STRING);
        } else if (cassandraType instanceof ListType) {
            AbstractType<?> elementsType = ((ListType<?>) cassandraType).getElementsType();
            baseSchema = Schema.createArray(getAvroSchemaForType(elementsType));
        } else if (cassandraType instanceof MapType) {
            AbstractType<?> keysType = ((MapType<?, ?>) cassandraType).getKeysType();
            // Avro map keys must be strings. If Cassandra map key is not string, this needs careful handling.
            // For simplicity, assuming string keys or types that can be clearly converted to string for Avro map keys.
            // If keysType is not string-compatible, this will error or need a different Avro structure (e.g., array of key-value pairs).
            if (!(keysType instanceof UTF8Type || keysType instanceof AsciiType || keysType instanceof VarcharType)) {
                 // Fallback: array of records for non-string map keys
                Schema keySchema = getAvroSchemaForType(keysType);
                Schema valueSchema = getAvroSchemaForType(((MapType<?, ?>) cassandraType).getValuesType());
                String recordName = "MapEntry_" + keySchema.getName() + "_" + valueSchema.getName();
                recordName = recordName.replaceAll("[^A-Za-z0-9_]", "_");
                 if (recordName.isEmpty()) { recordName = "default_map_entry"; } 
                 else if (Character.isDigit(recordName.charAt(0))) {
                    recordName = "_" + recordName;
                }
                baseSchema = Schema.createArray(
                    Schema.createRecord(recordName, "Map entry", null, false, List.of(
                        new Schema.Field("key", keySchema, null, null),
                        new Schema.Field("value", valueSchema, null, null)
                    ))
                );
            } else {
                 baseSchema = Schema.createMap(getAvroSchemaForType(((MapType<?, ?>) cassandraType).getValuesType()));
            }
        } else if (cassandraType instanceof SetType) {
            AbstractType<?> elementsType = ((SetType<?>) cassandraType).getElementsType();
            baseSchema = Schema.createArray(getAvroSchemaForType(elementsType));
            // Optionally, add a property to distinguish from lists: baseSchema.addProp("cassandraType", "set");
        } else if (cassandraType instanceof UserType) {
            UserType userType = (UserType) cassandraType;
            List<Schema.Field> udtFields = new ArrayList<>();
            for (int i = 0; i < userType.size(); i++) {
                udtFields.add(new Schema.Field(userType.fieldName(i).toString(), getAvroSchemaForType(userType.fieldType(i)), null, null));
            }
            String recordName = userType.getNameAsString().replaceAll("[^A-Za-z0-9_]", "_");
            if (recordName.isEmpty()) { recordName = "default_udt_name"; }
            else if (Character.isDigit(recordName.charAt(0))) {
                recordName = "_" + recordName;
            }
            baseSchema = Schema.createRecord(recordName, "UDT " + userType.getNameAsString(), userType.keyspace(), false, udtFields);
        } else if (cassandraType instanceof TupleType) {
            TupleType tupleType = (TupleType) cassandraType;
            List<Schema.Field> tupleFields = new ArrayList<>();
            for (int i = 0; i < tupleType.size(); i++) {
                // Avro field names must start with [A-Za-z_] and contain only [A-Za-z0-9_]
                String fieldName = "field" + i; 
                tupleFields.add(new Schema.Field(fieldName, getAvroSchemaForType(tupleType.type(i)), null, null));
            }
            // Tuple names can be tricky; generate a unique one if possible or a generic one.
            String recordName = "Tuple_" + tupleFields.stream().map(Schema.Field::name).collect(Collectors.joining("_"));
             recordName = recordName.replaceAll("[^A-Za-z0-9_]", "_");
            if (recordName.isEmpty()) { recordName = "default_tuple_name"; }
            else if (Character.isDigit(recordName.charAt(0))) {
                recordName = "_" + recordName;
            }
            baseSchema = Schema.createRecord(recordName, "Tuple", null, false, tupleFields);
        }
        // DurationType, EmptyType etc. are not handled yet for simplicity
        else {
            System.err.println("Warning: Unsupported Cassandra type: " + cassandraType.asCQL3Type() + ". Mapping to Avro string.");
            baseSchema = Schema.create(Schema.Type.STRING); // Fallback for unhandled types
        }
        // All fields are nullable in Parquet by default when using AvroParquetWriter if Avro schema is a union with null
        return Schema.createUnion(Schema.create(Schema.Type.NULL), baseSchema);
    }

    private static GenericRecord convertRowToAvroRecord(Row cassandraRow, UnfilteredRowIterator partitionIterator, TableMetadata tableMetadata, Schema avroSchema, boolean isStaticRow) {
        GenericRecord avroRecord = new GenericData.Record(avroSchema);

        // 1. Handle Partition Keys
        for (ColumnMetadata pkCol : tableMetadata.partitionKeyColumns()) {
            String colName = pkCol.name.toString();
            ByteBuffer pkValue = partitionIterator.partitionKey().getKey().getComponent(pkCol.position());
            if (pkValue != null) {
                 Schema.Field field = avroSchema.getField(colName);
                 if (field != null) {
                    avroRecord.put(colName, getAvroValue(pkValue, pkCol.type, field.schema()));
                 }
            }
        }

        // 2. Handle Clustering Columns (only for non-static rows)
        if (!isStaticRow) {
            Clustering clustering = cassandraRow.clustering();
            for (ColumnMetadata clCol : tableMetadata.clusteringColumns()) {
                String colName = clCol.name.toString();
                if (clustering.size() > clCol.position()) { // Check if clustering has this column
                     ByteBuffer clValue = clustering.bufferAt(clCol.position());
                     if (clValue != null) {
                        Schema.Field field = avroSchema.getField(colName);
                        if (field != null) {
                           avroRecord.put(colName, getAvroValue(clValue, clCol.type, field.schema()));
                        }
                     }
                }
            }
        }
        
        // 3. Handle Regular Columns (and Static columns if isStaticRow is true)
        // If it's a static row, we only care about static columns.
        // If it's a non-static row, we only care about non-static regular columns.
        for (ColumnMetadata regCol : tableMetadata.regularColumns()) {
            if (isStaticRow && !regCol.isStatic()) continue; 
            if (!isStaticRow && regCol.isStatic()) continue;

            String colName = regCol.name.toString();
            Cell<?> cell = cassandraRow.getCell(regCol);
            Schema.Field field = avroSchema.getField(colName);

            if (field != null) { 
                if (cell != null && cell.isLive(FBUtilities.nowInSeconds())) { 
                    avroRecord.put(colName, getAvroValue(cell.buffer(), regCol.type, field.schema()));
                } else {
                    avroRecord.put(colName, null); 
                }
            }
        }
        return avroRecord;
    }

    private static Object getAvroValue(ByteBuffer cassandraValue, AbstractType<?> cassandraType, Schema avroSchema) {
        if (cassandraValue == null) {
            return null;
        }

        Schema actualAvroSchema = avroSchema;
        if (avroSchema.getType() == Schema.Type.UNION) {
            actualAvroSchema = null;
            for (Schema branch : avroSchema.getTypes()) {
                if (branch.getType() != Schema.Type.NULL) {
                    actualAvroSchema = branch;
                    break;
                }
            }
            if (actualAvroSchema == null) return null; 
        }
        
        if (cassandraType instanceof ReversedType<?>) {
            cassandraType = ((ReversedType<?>) cassandraType).baseType;
        }

        switch (actualAvroSchema.getType()) {
            case STRING:
                return cassandraType.getString(cassandraValue);
            case INT:
                if (cassandraType instanceof DateType || cassandraType instanceof SimpleDateType) {
                     Integer days = ((DateType) cassandraType).getSerializer().deserialize(cassandraValue);
                     return days; // Avro 'date' logical type expects int for days since epoch
                } else if (cassandraType instanceof ShortType) {
                    return (int) ShortType.instance.compose(cassandraValue);
                } else if (cassandraType instanceof ByteType) {
                    return (int) ByteType.instance.compose(cassandraValue);
                }
                return Int32Type.instance.compose(cassandraValue); // For Int32Type
            case LONG:
                 if (cassandraType instanceof TimestampType) {
                    return TimestampType.instance.compose(cassandraValue); // Avro 'timestamp-millis' or 'timestamp-micros'
                 } else if (cassandraType instanceof TimeType) {
                    return TimeType.instance.compose(cassandraValue); // Avro 'time-micros'
                 }
                return LongType.instance.compose(cassandraValue); // For LongType, CounterColumnType
            case FLOAT:
                return FloatType.instance.compose(cassandraValue);
            case DOUBLE:
                return DoubleType.instance.compose(cassandraValue);
            case BOOLEAN:
                return BooleanType.instance.compose(cassandraValue);
            case BYTES:
                return cassandraValue.duplicate(); 
            case ARRAY:
                CollectionType<?> listOrSetType = (CollectionType<?>) cassandraType;
                AbstractType<?> elementType = listOrSetType.getElementsType();
                Schema elementSchema = actualAvroSchema.getElementType();
                List<Object> list = new ArrayList<>();
                if (!listOrSetType.isMultiCell()) { // Frozen list/set
                    ByteBuffer packedCells = cassandraValue;
                    // For frozen collections, the value is a single ByteBuffer containing all elements
                    // We need to use ProtocolVersion for deserializing collection elements
                    List<ByteBuffer> elements = listOrSetType.getSerializer().deserializeForNativeProtocol(packedCells, ProtocolVersion.V5);
                    for (ByteBuffer elementValue : elements) {
                         list.add(getAvroValue(elementValue, elementType, elementSchema));
                    }
                } else {
                     System.err.println("Warning: Non-frozen lists/sets are not fully supported in getAvroValue for cell: " + cassandraType.getString(cassandraValue));
                }
                return list;
            case MAP:
                MapType<?, ?> mapType = (MapType<?, ?>) cassandraType;
                AbstractType<?> keyType = mapType.getKeysType();
                AbstractType<?> valueType = mapType.getValuesType();
                
                Map<Object, Object> map = new HashMap<>();

                if (!mapType.isMultiCell()) { // Frozen map
                    ByteBuffer packedMapCells = cassandraValue;
                    // For frozen maps, value is one blob. Need to get individual key/value ByteBuffers.
                    // The serializer for MapType handles this.
                    Map<?,?> internalMap = (Map<?,?>) mapType.getSerializer().deserializeForNativeProtocol(packedMapCells, ProtocolVersion.V5);

                    if (actualAvroSchema.getValueType().getType() == Schema.Type.UNION && actualAvroSchema.getValueType().getTypes().get(0).getType() == Schema.Type.NULL) {
                         // This implies the map itself is an array of records (key-value pairs) due to non-string keys
                         List<GenericRecord> mapAsList = new ArrayList<>();
                         Schema entryRecordSchema = actualAvroSchema.getElementType(); // Schema for {key, value} record
                         Schema mapKeyAvroSchema = entryRecordSchema.getField("key").schema();
                         Schema mapValueAvroSchema = entryRecordSchema.getField("value").schema();

                         for(Map.Entry<?,?> entry : internalMap.entrySet()){
                            GenericRecord entryRecord = new GenericData.Record(entryRecordSchema);
                            // We need to convert Cassandra SDK map keys/values to ByteBuffers first, then to Avro values
                            entryRecord.put("key", getAvroValue(keyType.decompose(entry.getKey()), keyType, mapKeyAvroSchema));
                            entryRecord.put("value", getAvroValue(valueType.decompose(entry.getValue()), valueType, mapValueAvroSchema));
                            mapAsList.add(entryRecord);
                         }
                         return mapAsList; // Return list of records
                    } else { // Standard Avro map with string keys
                        Schema mapValueAvroSchema = actualAvroSchema.getValueType();
                         for(Map.Entry<?,?> entry : internalMap.entrySet()){
                            String keyString = keyType.getString(keyType.decompose(entry.getKey()));
                            map.put(keyString, getAvroValue(valueType.decompose(entry.getValue()), valueType, mapValueAvroSchema));
                        }
                        return map; // Return standard Avro map
                    }
                } else {
                    System.err.println("Warning: Non-frozen maps are not fully supported yet in getAvroValue for cell: " + cassandraType.getString(cassandraValue));
                }
                return map; // Potentially empty if non-frozen and not handled
            case RECORD:
                if (cassandraType instanceof UserType) {
                    UserType udt = (UserType) cassandraType;
                    GenericRecord udtRecord = new GenericData.Record(actualAvroSchema);
                    ByteBuffer[] udtComponents = udt.split(cassandraValue); // This is for frozen UDTs
                    for (int i = 0; i < udt.size(); i++) {
                        String fieldName = udt.fieldName(i).toString();
                        Schema.Field udtAvroField = actualAvroSchema.getField(fieldName);
                        if (udtAvroField != null && i < udtComponents.length && udtComponents[i] != null) {
                            udtRecord.put(fieldName, getAvroValue(udtComponents[i], udt.fieldType(i), udtAvroField.schema()));
                        } else if (udtAvroField != null) {
                            udtRecord.put(fieldName, null);
                        }
                    }
                    return udtRecord;
                } else if (cassandraType instanceof TupleType) {
                    TupleType tuple = (TupleType) cassandraType;
                    GenericRecord tupleRecord = new GenericData.Record(actualAvroSchema);
                    ByteBuffer[] tupleComponents = tuple.split(cassandraValue); // For frozen tuples
                     for (int i = 0; i < tuple.size(); i++) {
                        String fieldName = "field" + i; 
                        Schema.Field tupleAvroField = actualAvroSchema.getField(fieldName);
                         if (tupleAvroField != null && i < tupleComponents.length && tupleComponents[i] != null) {
                            tupleRecord.put(fieldName, getAvroValue(tupleComponents[i], tuple.type(i), tupleAvroField.schema()));
                        } else if (tupleAvroField != null) {
                             tupleRecord.put(fieldName, null);
                         }
                    }
                    return tupleRecord;
                }
                break; 
            default:
                System.err.println("Warning: Unhandled Avro type in getAvroValue: " + actualAvroSchema.getType() + " for Cassandra type " + cassandraType.asCQL3Type());
                return cassandraType.getString(cassandraValue);
        }
        System.err.println("Warning: Could not convert Cassandra type " + cassandraType.asCQL3Type() + " to Avro type " + actualAvroSchema.getType());
        return cassandraType.getString(cassandraValue); 
    }
}
