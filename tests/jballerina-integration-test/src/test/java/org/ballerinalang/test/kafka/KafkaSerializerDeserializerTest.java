/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.ballerinalang.test.kafka;

import io.netty.handler.codec.http.HttpHeaderNames;
import org.ballerinalang.messaging.kafka.utils.KafkaCluster;
import org.ballerinalang.test.BaseTest;
import org.ballerinalang.test.context.BServerInstance;
import org.ballerinalang.test.util.HttpClientRequest;
import org.ballerinalang.test.util.HttpResponse;
import org.ballerinalang.test.util.TestConstant;
import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static java.io.File.separator;
import static org.ballerinalang.messaging.kafka.utils.TestUtils.PROTOCOL_PLAINTEXT;
import static org.ballerinalang.messaging.kafka.utils.TestUtils.getZookeeperTimeoutProperty;

/**
 * Test class for test the functionality of Ballerina Kafka serializers and deserializers.
 */
@Test(enabled = false)
public class KafkaSerializerDeserializerTest extends BaseTest {

    protected static BServerInstance serverInstance;
    private static final String resourceLocation = "src" + separator + "test" + separator + "resources" + separator
            + "messaging" + separator + "kafka" + separator;
    private static KafkaCluster kafkaCluster;
    private static final String NAME = "Thisaru Guruge";
    private static final String AGE = "29";
    private static final String PATH = separator + "sendData";

    @BeforeTest(alwaysRun = true)
    public void start() throws Throwable {
        int[] requiredPorts = new int[]{14001, 14002, 14003};
        String sourcePath = new File(resourceLocation).getAbsolutePath();
        serverInstance = new BServerInstance(balServer);
        serverInstance.startServer(sourcePath, requiredPorts);
        String dataDir = "cluster-kafka-serdes-test";
        kafkaCluster = new KafkaCluster(dataDir)
                .withZookeeper(14002)
                .withBroker(PROTOCOL_PLAINTEXT, 14102, getZookeeperTimeoutProperty())
                .start();
    }

    @Test(description = "Tests Kafka custom serializer / deserializer functionality")
    public void testPublishToKafkaCluster() throws IOException {
        Map<String, String> headers = new HashMap<>();
        headers.put(HttpHeaderNames.CONTENT_TYPE.toString(), TestConstant.CONTENT_TYPE_JSON);
        String message = "{\"name\":\"" + NAME + "\",\"age:\"" + AGE + "\"}";
        HttpResponse response = HttpClientRequest.doPost(serverInstance.getServiceURLHttp(14001, PATH), message,
                                                         headers);
        Assert.assertEquals(response.getResponseCode(), 200, "Response code mismatched");
        Assert.assertEquals(response.getData(), "Successfully received", "Message content mismatched");
    }

    @AfterTest(alwaysRun = true)
    private void cleanup() {
        if (kafkaCluster != null) {
            kafkaCluster.stop();
        }
    }
}
