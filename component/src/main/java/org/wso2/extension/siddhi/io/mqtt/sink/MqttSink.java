/*
 *  Copyright (c) 2017 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
 *
 */
package org.wso2.extension.siddhi.io.mqtt.sink;

import com.google.gson.Gson;
import io.siddhi.annotation.Example;
import io.siddhi.annotation.Extension;
import io.siddhi.annotation.Parameter;
import io.siddhi.annotation.util.DataType;
import io.siddhi.core.config.SiddhiAppContext;
import io.siddhi.core.exception.ConnectionUnavailableException;
import io.siddhi.core.stream.ServiceDeploymentInfo;
import io.siddhi.core.stream.output.sink.Sink;
import io.siddhi.core.util.config.ConfigReader;
import io.siddhi.core.util.snapshot.state.State;
import io.siddhi.core.util.snapshot.state.StateFactory;
import io.siddhi.core.util.transport.DynamicOptions;
import io.siddhi.core.util.transport.Option;
import io.siddhi.core.util.transport.OptionHolder;
import io.siddhi.query.api.definition.StreamDefinition;
import org.apache.log4j.Logger;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;
import org.wso2.extension.siddhi.io.mqtt.sink.exception.MqttSinkRuntimeException;
import org.wso2.extension.siddhi.io.mqtt.util.MqttConstants;

import java.io.UnsupportedEncodingException;
import java.util.Map;

/**
 * {@code MqttSink } Handle the Mqtt publishing tasks.
 */

@Extension(
        name = "mqtt",
        namespace = "sink",
        description = "The MQTT sink publishes the events to an MQTT broker ",
        parameters = {
                @Parameter(
                        name = "url",
                        description = "The URL of the MQTT broker. It is used to connect to the MQTT broker " +
                                "It is required to specify a valid URL here.",
                        type = {DataType.STRING}),
                @Parameter(
                        name = "username",
                        description = "The username to be provided when the MQTT client is authenticated by the " +
                                "broker.",
                        type = {DataType.STRING},
                        optional = true, defaultValue = "null"),
                @Parameter(
                        name = "password",
                        description = "The password to be provided when the MQTT client is authenticated by the " +
                                "broker.",
                        type = {DataType.STRING},
                        optional = true, defaultValue = "empty"),
                @Parameter(
                        name = "client.id",
                        description = "A unique ID for the MQTT client. The server uses this to identify the client " +
                                "when it reconnects. If you do not specify a client ID, the system automatically " +
                                "generates it. ",
                        type = {DataType.STRING},
                        optional = true, defaultValue = "generated by the system"),
                @Parameter(
                        name = "topic",
                        description = "The topic to which the events processed by WSO2 SP are published via MQTT. ",
                        type = {DataType.STRING},
                        dynamic = true,
                        optional = true, defaultValue = "null"),
                @Parameter(
                        name = "topic.attribute",
                        description = "The attribute whose value will be used as the topic when publishing to MQTT. " +
                                "Will fallback to using the `topic` parameter if this attribute is missing.",
                        type = {DataType.STRING},
                        optional = true, defaultValue = "topic"),
                @Parameter(
                        name = "quality.of.service",
                        description = "The quality of service provided by the MQTT client. The possible values are " +
                                "as follows." +
                                "`0`: The MQTT client sends each event only once. It does not receive an " +
                                "acknowledgement when an event is delivered, and the events are not stored. Events " +
                                "may get lost if the MQTT client is disconnected or if the server fails. This is " +
                                "the fastest method in which events are received via MQTT." +
                                "`1`: The MQTT client sends each event at least once. If the MQTT client does not " +
                                "receive an acknowledgement to indicate that the event is delivered, it sends the " +
                                "event again." +
                                "`2`: The MQTT client sends each event only once. The events are stored until the " +
                                "WSO2 SP receives them. This is the safest, but the slowest method of receiving" +
                                " events via MQTT.",
                        type = {DataType.STRING},
                        dynamic = true,
                        optional = true, defaultValue = "1"),
                @Parameter(
                        name = "clean.session",
                        description = "This is an optional paramater. If this parameter is set to `true`, the " +
                                "subscriptions made by the MQTT client during a session expire when the session ends," +
                                "and they need to be recreated for the next session.\n" +
                                "If this parameter is set to `false`, all the information relating to the MQTT " +
                                "client's connection to the broker (e.g., the specific topics to which the client " +
                                "has subscribed) are saved after a session. Thus, when a session ends and restarts," +
                                " the connection is re-established with the same information.\n" +
                                "The default value is `true`.",
                        type = {DataType.BOOL},
                        optional = true, defaultValue = "true"
                ),
                @Parameter(
                        name = "message.retain",
                        description = "If this parameter is set to true, the last message sent from the topic to " +
                                "which WSO2 SP publishes events is retained until the next message is sent.",
                        type = {DataType.STRING},
                        dynamic = true,
                        optional = true, defaultValue = "false"),
                @Parameter(
                        name = "keep.alive",
                        description = "The maximum number of seconds the connection between the MQTT client and " +
                                "the broker should be maintained without any events being transferred. Once this " +
                                "time interval elapses without any event transfers, the connection is dropped. The " +
                                "default value is 60.",
                        type = {DataType.INT},
                        optional = true, defaultValue = "60"),
                @Parameter(
                        name = "connection.timeout",
                        description = "The maximum number of seconds that the MQTT client should spend attempting " +
                                "to connect to the MQTT broker. Once this time interval elapses, a timeout takes " +
                                "place.",
                        type = {DataType.INT},
                        optional = true, defaultValue = "30")

        },
        examples =
                {
                        @Example(
                                syntax = "@sink(type='mqtt', url= 'tcp://localhost:1883', " +
                                        "topic='mqtt_topic', clean.session='true', message.retain='false', " +
                                        "quality.of.service= '1', keep.alive= '60',connection.timeout='30'" +
                                        "@map(type='xml'))" +
                                        "Define stream BarStream (symbol string, price float, volume long);",
                                description = "This query publishes events to a stream named `BarStream` via the " +
                                        "MQTT transport. The events are published to a topic named mqtt_topic " +
                                        "located at tcp://localhost:1883.")
                }
)


public class MqttSink extends Sink {
    private static final Logger log = Logger.getLogger(MqttSink.class);

    private String brokerURL;
    private Option topicOption;
    private String topicAttribute;
    private String clientId;
    private String userName;
    private String userPassword;
    private Option qosOption;
    private boolean cleanSession;
    private int keepAlive;
    private int connectionTimeout;
    private MqttClient client;
    private Option messageRetainOption;
    private StreamDefinition streamDefinition;
    private Gson gson;

    @Override
    protected StateFactory init(StreamDefinition streamDefinition, OptionHolder optionHolder, ConfigReader configReader,
                                SiddhiAppContext siddhiAppContext) {
        this.streamDefinition = streamDefinition;
        this.brokerURL = optionHolder.validateAndGetStaticValue(MqttConstants.MQTT_BROKER_URL);
        this.clientId = optionHolder.validateAndGetStaticValue(MqttConstants.CLIENT_ID,
                MqttConstants.EMPTY_STRING);
        this.topicOption = optionHolder.getOrCreateOption(MqttConstants.MESSAGE_TOPIC,
                MqttConstants.DEFAULT_TOPIC);
        this.topicAttribute = optionHolder.validateAndGetStaticValue(MqttConstants.TOPIC_ATTRIBUTE,
                MqttConstants.DEFAULT_TOPIC_ATTRIBUTE);
        this.userName = optionHolder.validateAndGetStaticValue(MqttConstants.MQTT_BROKER_USERNAME,
                MqttConstants.DEFAULT_USERNAME);
        this.userPassword = optionHolder.validateAndGetStaticValue(MqttConstants.MQTT_BROKER_PASSWORD,
                MqttConstants.EMPTY_STRING);
        this.qosOption = optionHolder.getOrCreateOption(MqttConstants.MESSAGE_QOS, MqttConstants.DEFAULT_QOS);
        this.keepAlive = Integer.parseInt(optionHolder.validateAndGetStaticValue
                (MqttConstants.CONNECTION_KEEP_ALIVE_INTERVAL,
                        MqttConstants.DEFAULT_CONNECTION_KEEP_ALIVE_INTERVAL));
        this.connectionTimeout = Integer.parseInt(optionHolder.validateAndGetStaticValue
                (MqttConstants.CONNECTION_TIMEOUT_INTERVAL,
                        MqttConstants.DEFAULT_CONNECTION_TIMEOUT_INTERVAL));
        this.messageRetainOption = optionHolder.getOrCreateOption(MqttConstants.MQTT_MESSAGE_RETAIN,
                MqttConstants.DEFAULT_MESSAGE_RETAIN);
        this.cleanSession = Boolean.parseBoolean(optionHolder.validateAndGetStaticValue
                    (MqttConstants.CLEAN_SESSION, MqttConstants.DEFAULT_CLEAN_SESSION));
        this.gson = new Gson();
        return null;
    }

    @Override
    public Class[] getSupportedInputEventClasses() {
        return new Class[]{Map.class};
    }

    @Override
    protected ServiceDeploymentInfo exposeServiceDeploymentInfo() {
        return null;
    }

    @Override
    public String[] getSupportedDynamicOptions() {
        return new String[]{MqttConstants.MESSAGE_TOPIC, MqttConstants.MESSAGE_QOS,
                MqttConstants.MQTT_MESSAGE_RETAIN};
    }

    @Override
    public void connect() throws ConnectionUnavailableException {
        try {
            MqttDefaultFilePersistence persistence = new  MqttDefaultFilePersistence();
            if (clientId.isEmpty()) {
                clientId = MqttClient.generateClientId();
            }
            client = new MqttClient(brokerURL, clientId, persistence);
            MqttConnectOptions connectionOptions = new MqttConnectOptions();
            connectionOptions.setUserName(userName);
            connectionOptions.setPassword(userPassword.toCharArray());
            connectionOptions.setCleanSession(cleanSession);
            connectionOptions.setKeepAliveInterval(keepAlive);
            connectionOptions.setConnectionTimeout(connectionTimeout);
            client.connect(connectionOptions);

        } catch (MqttException e) {
            throw new ConnectionUnavailableException(
                    "Error while connecting with the Mqtt server, Check the broker url = " + brokerURL +
                            " defined in " + streamDefinition.getId(), e);
        }
    }

    @Override
    public void disconnect() {
        try {
            if (client != null) {
                client.disconnect();
                log.debug("Disconnected from MQTT broker: " + brokerURL);
            }
        } catch (MqttException e) {
            log.error("Could not disconnect from MQTT broker: " + brokerURL, e);
        } finally {
            try {
                if (client != null) {
                    client.close();
                }
            } catch (MqttException e) {
                log.error("Could not close connection with MQTT broker: " + brokerURL, e);
            }
        }
    }

    @Override
    public void destroy() {

    }

    public void publish(Object payload, DynamicOptions dynamicOptions, State state)
            throws ConnectionUnavailableException {
        try {
            MqttMessage message = new MqttMessage();
            String topic = topicOption.getValue(dynamicOptions);
            Map<String, Object> event = (Map) (payload);
            if (topicAttribute != null) {
              String attributeBasedTopic = (String) event.get(topicAttribute);
              if (attributeBasedTopic != null) {
                topic = attributeBasedTopic;
                event.remove(topicAttribute);
              }
            }
            message.setPayload(gson.toJson(event).getBytes("UTF-8"));

            int qos;
            String qosStr = qosOption.getValue(dynamicOptions);
            try {
                qos = Integer.parseInt(qosStr);
            } catch (NumberFormatException e) {
                throw new MqttSinkRuntimeException("Invalid QOS value received for MQTT Sink associated to stream '"
                        + streamDefinition.getId() + "' . Expected 0, 1 or 2 but received " + qosStr, e);
            }
            if (qos < 0 || qos > 2) {
                throw new MqttSinkRuntimeException("Invalid QOS value received for MQTT Sink associated to stream '"
                        + streamDefinition.getId() + "' . Expected 0, 1 or 2 but received " + qos);
            }
            message.setQos(qos);

            boolean messageRetain = Boolean.parseBoolean(messageRetainOption.getValue(dynamicOptions));
            message.setRetained(messageRetain);

            client.publish(topic, message);
        } catch (MqttException e) {
            log.error("Error occurred when publishing message to the MQTT broker: " + brokerURL + " in "
                    + streamDefinition.getId(), e);
        } catch (UnsupportedEncodingException e) {
            log.error("Event could not be encoded in UTF-8, hence it could not be published to MQTT broker: "
                    + brokerURL + " in " + streamDefinition.getId(), e);
        }
    }
}
