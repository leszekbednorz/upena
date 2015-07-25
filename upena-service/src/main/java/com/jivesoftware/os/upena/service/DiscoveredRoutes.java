package com.jivesoftware.os.upena.service;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.jivesoftware.os.routing.bird.shared.ConnectionDescriptor;
import com.jivesoftware.os.routing.bird.shared.ConnectionDescriptorsRequest;
import com.jivesoftware.os.routing.bird.shared.ConnectionDescriptorsResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author jonathan.colt
 */
public class DiscoveredRoutes {

    public final Map<ConnectionDescriptorsRequest, TimestampedConnectionDescriptorsResponse> discoveredConnection = new ConcurrentHashMap<>();

    public void discovered(ConnectionDescriptorsRequest request, ConnectionDescriptorsResponse response) {
        discoveredConnection.compute(request, (ConnectionDescriptorsRequest t, TimestampedConnectionDescriptorsResponse u) -> {
            if (u == null) {
                return new TimestampedConnectionDescriptorsResponse(System.currentTimeMillis(), response);
            } else {
                long now = System.currentTimeMillis();
                return (u.timestamp > now) ? u : new TimestampedConnectionDescriptorsResponse(now, response);
            }
        });
    }

    private static class TimestampedConnectionDescriptorsResponse {

        private final long timestamp;
        private final ConnectionDescriptorsResponse response;

        public TimestampedConnectionDescriptorsResponse(long timestamp, ConnectionDescriptorsResponse response) {
            this.timestamp = timestamp;
            this.response = response;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public ConnectionDescriptorsResponse getResponse() {
            return response;
        }

    }

    public List<Route> routes() {
        List<Route> routes = new ArrayList<>();
        for (Entry<ConnectionDescriptorsRequest, TimestampedConnectionDescriptorsResponse> e : discoveredConnection.entrySet()) {
            ConnectionDescriptorsRequest key = e.getKey();
            TimestampedConnectionDescriptorsResponse value = e.getValue();
            ConnectionDescriptorsResponse response = value.getResponse();
            long timestamp = value.getTimestamp();
            routes.add(new Route(timestamp,
                key.getTenantId(), key.getInstanceId(), key.getConnectToServiceNamed(), key.getPortName(),
                response.getReturnCode(), response.getMessages(), response.getReleaseGroup(), response.getConnections()));
        }
        return routes;
    }

    static public class Routes {

        private final List<Route> routes;

        @JsonCreator
        public Routes(@JsonProperty("routes") List<Route> routes) {
            this.routes = routes;
        }

        public List<Route> getRoutes() {
            return routes;
        }

        @Override
        public String toString() {
            return "Routes{" + "routes=" + routes + '}';
        }

    }

    static public class Route {

        private final long timestamp;
        private final String tenantId;
        private final String instanceId;
        private final String connectToServiceNamed;
        private final String portName;
        private final int returnCode;
        private final List<String> messages;
        private final String releaseGroup;
        private final List<ConnectionDescriptor> connections;

        @JsonCreator
        public Route(@JsonProperty("timestamp") long timestamp,
            @JsonProperty("tenantId") String tenantId,
            @JsonProperty("instanceId") String instanceId,
            @JsonProperty("connectToServiceNamed") String connectToServiceNamed,
            @JsonProperty("portName") String portName,
            @JsonProperty("returnCode") int returnCode,
            @JsonProperty("messages") List<String> messages,
            @JsonProperty("releaseGroup") String releaseGroup,
            @JsonProperty("connections") List<ConnectionDescriptor> connections) {

            this.timestamp = timestamp;
            this.tenantId = tenantId;
            this.instanceId = instanceId;
            this.connectToServiceNamed = connectToServiceNamed;
            this.portName = portName;
            this.returnCode = returnCode;
            this.messages = messages;
            this.releaseGroup = releaseGroup;
            this.connections = connections;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getTenantId() {
            return tenantId;
        }

        public String getInstanceId() {
            return instanceId;
        }

        public String getConnectToServiceNamed() {
            return connectToServiceNamed;
        }

        public String getPortName() {
            return portName;
        }

        public int getReturnCode() {
            return returnCode;
        }

        public List<String> getMessages() {
            return messages;
        }

        public String getReleaseGroup() {
            return releaseGroup;
        }

        public List<ConnectionDescriptor> getConnections() {
            return connections;
        }

        @Override
        public String toString() {
            return "Route{"
                + "timestamp=" + timestamp
                + ", tenantId=" + tenantId
                + ", instanceId=" + instanceId
                + ", connectToServiceNamed=" + connectToServiceNamed
                + ", portName=" + portName
                + ", returnCode=" + returnCode
                + ", messages=" + messages
                + ", releaseGroup=" + releaseGroup
                + ", connections=" + connections
                + '}';
        }

    }

}
