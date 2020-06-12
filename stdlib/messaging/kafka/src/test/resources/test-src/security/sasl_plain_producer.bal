// Copyright (c) 2020 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
//
// WSO2 Inc. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

import ballerina/kafka;

kafka:AuthenticationConfiguration authConfigValid = {
    mechanism: kafka:AUTH_SASL_PLAIN,
    username: "ballerina",
    password: "ballerina-secret"
};

kafka:AuthenticationConfiguration authConfigInvalidUsername = {
    mechanism: kafka:AUTH_SASL_PLAIN,
    username: "nonexisting",
    password: "ballerina-secret"
};

kafka:ProducerConfiguration producerConfigsValid = {
    bootstrapServers: "localhost:14121",
    valueSerializerType: kafka:SER_STRING,
    maxBlock: 10000,
    authenticationConfiguration: authConfigValid
};

kafka:ProducerConfiguration producerConfigsInvalidUsername = {
    bootstrapServers: "localhost:14121",
    valueSerializerType: kafka:SER_STRING,
    maxBlock: 10000,
    authenticationConfiguration: authConfigInvalidUsername
};

public function sendFromValidProducer() returns kafka:ProducerError? {
    kafka:Producer kafkaProducer = new (producerConfigsValid);
    return kafkaProducer->send("Hello from Ballerina", "test-1");
}

public function sendFromInvalidUsernameProducer() returns kafka:ProducerError? {
    kafka:Producer kafkaProducer = new (producerConfigsInvalidUsername);
    return kafkaProducer->send("Hello", "test-1");
}
