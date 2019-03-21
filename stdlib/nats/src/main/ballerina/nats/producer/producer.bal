// Copyright (c) 2019 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

# NATS producer would act as a streaming client allowing to stream messages between NATS streaming server.
# Producer would create a new NATS connection if a connection was not provided during the initialization.
public type Producer client object {
    private Connection? connection = ();

    # Creates a new producer. A connection will be created if a refernece to a connection is not provided.
    #
    # + c - An already established connection or configuration to create a new connection.
    public function __init(ConnectionConfig|Connection c) {
        if (c is Connection) {
            self.connection = c;
        } else if (c is ConnectionConfig) {
            self.connection = new Connection(c);
        }
    }

    # Produces a message to a NATS streaming server for the given subject.
    #
    # + subject - Could also be referred as the 'topic/queue' name.
    # + message - Message could be either a string, json representation.
    # + charset - Encoding of the message (by defaults it would be UTF-8)
    # + return -  GUID of acknowledgment or the specific error which might have occurred while publishing the message.
    public remote function send(string subject, string|json message, string charset = "UTF-8") returns string|error {
        if (message is string) {
            return self.sendMsg(subject, message.toByteArray(charset));
        } else {
            return self.sendMsg(subject, message.toString().toByteArray(charset));
        }
    }

    # Produces a message and would await for a response.
    #
    # + subject - Would represent the topic/queue name.
    # + message - Message could be either a string, json representation.
    # + charset - Encoding of the message by defaults it would be UTF-8
    # + return - Response message or an error
    public remote function requestReply(string subject, string|json message,
                                        string charset = "UTF-8") returns Message|error {
        if (message is string) {
            return self.sendRequestReplyMsg(subject, message.toByteArray(charset));
        } else {
            return self.sendRequestReplyMsg(subject, message.toString().toByteArray(charset));
        }
    }

    # Sends a message via a given connection.
    #
    # + subject - Could also be referred as the 'topic/queue' name.
    # + message - Message could be either a string, json representation.
    # + return -  GUID of acknowledgment or the specific error which might have occurred while publishing the message.
    extern function sendMsg(string subject, byte[] message) returns string|error;


    # Produces a message and would await for a response.
    #
    # + subject - Would represent the topic/queue name.
    # + message - Message could be either a string, json representation.
    # + return - Response message or an error
    extern function sendRequestReplyMsg(string subject, byte[] message) returns Message|error;
};
