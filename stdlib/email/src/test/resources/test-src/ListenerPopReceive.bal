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

import ballerina/email;

email:PopConfig popConfig = {
     port: 3995,
     enableSsl: true
};

listener email:Listener emailServer = new ({
    host: "127.0.0.1",
    username: "hascode",
    password: "abcdef123",
    protocol: "POP",
    protocolConfig: popConfig,
    pollingInterval: 2000
});

boolean onMessageInvoked = false;
boolean onErrorInvoked = false;
string receivedMessage = "";
string receivedError = "";

service emailObserver on emailServer {

    resource function onMessage(email:Email emailMessage) {
        onMessageInvoked = true;
        receivedMessage = <@untainted>emailMessage.subject;
    }

    resource function onError(email:Error emailError) {
        onErrorInvoked = true;
        receivedError = <@untainted>emailError.detail().toString();
    }

}

function isOnMessageInvoked() returns boolean {
    return onMessageInvoked;
}

function isOnErrorInvoked() returns boolean {
    return onErrorInvoked;
}

function getReceivedMessage() returns string {
    return <@untainted>receivedMessage;
}

function getReceivedError() returns string {
    return <@untainted>receivedError;
}
