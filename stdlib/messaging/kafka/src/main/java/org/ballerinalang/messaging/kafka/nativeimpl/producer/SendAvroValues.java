/*
 *  Copyright (c) 2020 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.ballerinalang.messaging.kafka.nativeimpl.producer;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.ballerinalang.jvm.values.MapValue;
import org.ballerinalang.jvm.values.ObjectValue;
import org.ballerinalang.jvm.values.api.BArray;
import org.ballerinalang.messaging.kafka.utils.KafkaConstants;
import org.ballerinalang.messaging.kafka.utils.KafkaUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.ballerinalang.messaging.kafka.nativeimpl.producer.Send.sendKafkaRecord;
import static org.ballerinalang.messaging.kafka.utils.KafkaConstants.ALIAS_PARTITION;
import static org.ballerinalang.messaging.kafka.utils.KafkaConstants.UNCHECKED;
import static org.ballerinalang.messaging.kafka.utils.KafkaUtils.getIntValue;
import static org.ballerinalang.messaging.kafka.utils.KafkaUtils.getLongValue;

/**
 * Sends Avro values from Ballerina Kafka producers.
 */
public class SendAvroValues {
    private static final Logger logger = LoggerFactory.getLogger(SendAvroValues.class);

    // ballerina avro
    @SuppressWarnings(UNCHECKED)
    public static Object send(ObjectValue producer, MapValue value, String topic, Object partition,
                              Object timestamp) {
        GenericRecord genericRecord = createGenericRecord(value);
        Integer partitionValue = getIntValue(partition, ALIAS_PARTITION, logger);
        Long timestampValue = getLongValue(timestamp);
        ProducerRecord<?, Object> kafkaRecord = new ProducerRecord<>(topic, partitionValue, timestampValue, null,
                                                                     genericRecord);
        return sendKafkaRecord(kafkaRecord, producer);
    }

    // ballerina avro and String
    public static Object send(ObjectValue producer, MapValue value, String topic, String key, Object partition,
                              Object timestamp) {
        GenericRecord genericRecord = createGenericRecord(value);
        Integer partitionValue = getIntValue(partition, ALIAS_PARTITION, logger);
        Long timestampValue = getLongValue(timestamp);
        ProducerRecord<String, GenericRecord> kafkaRecord = new ProducerRecord<>(topic, partitionValue, timestampValue,
                                                                                 key, genericRecord);
        return sendKafkaRecord(kafkaRecord, producer);
    }

    // ballerina avro and ballerina int
    public static Object send(ObjectValue producer, MapValue value, String topic, long key, Object partition,
                              Object timestamp) {
        GenericRecord genericRecord = createGenericRecord(value);
        Integer partitionValue = getIntValue(partition, ALIAS_PARTITION, logger);
        Long timestampValue = getLongValue(timestamp);
        ProducerRecord<Long, GenericRecord> kafkaRecord = new ProducerRecord<>(topic, partitionValue, timestampValue,
                                                                               key, genericRecord);
        return sendKafkaRecord(kafkaRecord, producer);
    }

    // ballerina avro and ballerina float
    public static Object send(ObjectValue producer, MapValue value, String topic, double key, Object partition,
                              Object timestamp) {
        GenericRecord genericRecord = createGenericRecord(value);
        Integer partitionValue = getIntValue(partition, ALIAS_PARTITION, logger);
        Long timestampValue = getLongValue(timestamp);
        ProducerRecord<Double, GenericRecord> kafkaRecord = new ProducerRecord<>(topic, partitionValue, timestampValue,
                                                                                 key, genericRecord);
        return sendKafkaRecord(kafkaRecord, producer);
    }

    // ballerina avro and ballerina byte[]
    public static Object send(ObjectValue producer, MapValue value, String topic, BArray key, Object partition,
                              Object timestamp) {
        GenericRecord genericRecord = createGenericRecord(value);
        Integer partitionValue = getIntValue(partition, ALIAS_PARTITION, logger);
        Long timestampValue = getLongValue(timestamp);
        ProducerRecord<byte[], GenericRecord> kafkaRecord = new ProducerRecord<>(topic, partitionValue, timestampValue,
                                                                                 key.getBytes(), genericRecord);
        return sendKafkaRecord(kafkaRecord, producer);
    }

    // ballerina avro and ballerina any
    public static Object sendAvroAny(ObjectValue producer, MapValue value, String topic, Object key, Object partition,
                                     Object timestamp) {
        GenericRecord genericRecord = createGenericRecord(value);
        Integer partitionValue = getIntValue(partition, ALIAS_PARTITION, logger);
        Long timestampValue = getLongValue(timestamp);
        ProducerRecord<Object, GenericRecord> kafkaRecord = new ProducerRecord<>(topic, partitionValue, timestampValue,
                                                                                 key, genericRecord);
        return sendKafkaRecord(kafkaRecord, producer);
    }

    private static GenericRecord createGenericRecord(MapValue<String, Object> value) {
        GenericRecord genericRecord = createRecord(value);
        MapValue data = value.getMapValue(KafkaConstants.AVRO_DATA_RECORD_NAME);
        populateAvroRecord(genericRecord, data);
        return genericRecord;
    }

    private static void populateAvroRecord(GenericRecord record, MapValue<String, Object> data) {
        String[] keys = data.getKeys();
        for (String key : keys) {
            Object value = data.get(key);
            if (value instanceof String || value instanceof Number || value == null) {
                record.put(key, value);
            } else if (value instanceof MapValue) {
                Schema childSchema = record.getSchema().getField(key).schema();
                GenericRecord subRecord = new GenericData.Record(childSchema);
                populateAvroRecord(subRecord, (MapValue<String, Object>) value);
                record.put(key, subRecord);
            } else if (value instanceof BArray) {
                Schema childSchema = record.getSchema().getField(key).schema().getElementType();
                GenericRecord subRecord = new GenericData.Record(childSchema);
                populateAvroRecordArray(subRecord, (BArray) value);
                record.put(key, subRecord);
            } else {
                throw KafkaUtils.createKafkaError("Invalid data type received for avro data",
                                                  KafkaConstants.AVRO_ERROR);
            }
        }
    }

    private static void populateAvroRecordArray(GenericRecord record, BArray bArray) {
        for (int i = 0; i < bArray.size(); i++) {
            record.put(i, bArray.get(i));
        }
    }

    private static GenericRecord createRecord(MapValue value) {
        String schemaString = value.getStringValue(KafkaConstants.AVRO_SCHEMA_STRING_NAME);
        Schema avroSchema = new Schema.Parser().parse(schemaString);
        return new GenericData.Record(avroSchema);
    }
}
