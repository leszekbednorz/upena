/*
 * Copyright 2013 Jive Software, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.jivesoftware.os.upena.routing.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class InstanceDescriptor {

    public final String clusterKey;
    public final String clusterName;
    public final String serviceKey;
    public final String serviceName;
    public final String releaseGroupKey;
    public final String releaseGroupName;
    public final String instanceKey;
    public final int instanceName;
    public final String versionName;
    public final String repository;
    public final Map<String, InstanceDescriptorPort> ports = new ConcurrentHashMap<>();
    public final long restartTimestampGMTMillis; // deliberately not part of hash or equals.

    @JsonCreator
    public InstanceDescriptor(@JsonProperty(value = "clusterKey") String clusterKey,
        @JsonProperty("clusterName") String clusterName,
        @JsonProperty("serviceKey") String serviceKey,
        @JsonProperty("serviceName") String serviceName,
        @JsonProperty("releaseGroupKey") String releaseGroupKey,
        @JsonProperty("releaseGroupName") String releaseGroupName,
        @JsonProperty("instanceKey") String instanceKey,
        @JsonProperty("instanceName") int instanceName,
        @JsonProperty("versionName") String versionName,
        @JsonProperty("repository") String repository,
        @JsonProperty("restartTimestampGMTMillis") long restartTimestampGMTMillis) {
        this.clusterKey = clusterKey;
        this.clusterName = clusterName.replaceAll("[^a-zA-Z0-9-_\\.]", "_");
        this.serviceKey = serviceKey;
        this.serviceName = serviceName.replaceAll("[^a-zA-Z0-9-_\\.]", "_");
        this.releaseGroupKey = releaseGroupKey;
        this.releaseGroupName = releaseGroupName.replaceAll("[^a-zA-Z0-9-_\\.]", "_");
        this.instanceKey = instanceKey;
        this.instanceName = instanceName;
        this.versionName = versionName;
        this.repository = repository;
        this.restartTimestampGMTMillis = restartTimestampGMTMillis;
    }

    public static class InstanceDescriptorPort {

        public final int port;

        @JsonCreator
        public InstanceDescriptorPort(@JsonProperty(value = "port") int port) {
            this.port = port;
        }

        @Override
        public String toString() {
            return "InstanceDescriptorPort{" + "port=" + port + '}';
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 79 * hash + this.port;
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final InstanceDescriptorPort other = (InstanceDescriptorPort) obj;
            if (this.port != other.port) {
                return false;
            }
            return true;
        }

    }

    @Override
    public String toString() {
        return "InstanceDescriptor{"
            + "clusterKey=" + clusterKey
            + ", clusterName=" + clusterName
            + ", serviceKey=" + serviceKey
            + ", serviceName=" + serviceName
            + ", releaseGroupKey=" + releaseGroupKey
            + ", releaseGroupName=" + releaseGroupName
            + ", instanceKey=" + instanceKey
            + ", instanceName=" + instanceName
            + ", versionName=" + versionName
            + ", repository=" + repository
            + ", ports=" + ports
            + ", restartTimestampGMTMillis=" + restartTimestampGMTMillis
            + '}';
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 73 * hash + Objects.hashCode(this.clusterKey);
        hash = 73 * hash + Objects.hashCode(this.clusterName);
        hash = 73 * hash + Objects.hashCode(this.serviceKey);
        hash = 73 * hash + Objects.hashCode(this.serviceName);
        hash = 73 * hash + Objects.hashCode(this.releaseGroupKey);
        hash = 73 * hash + Objects.hashCode(this.releaseGroupName);
        hash = 73 * hash + Objects.hashCode(this.instanceKey);
        hash = 73 * hash + this.instanceName;
        hash = 73 * hash + Objects.hashCode(this.versionName);
        hash = 73 * hash + Objects.hashCode(this.repository);
        hash = 73 * hash + Objects.hashCode(this.ports);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final InstanceDescriptor other = (InstanceDescriptor) obj;
        if (!Objects.equals(this.clusterKey, other.clusterKey)) {
            return false;
        }
        if (!Objects.equals(this.clusterName, other.clusterName)) {
            return false;
        }
        if (!Objects.equals(this.serviceKey, other.serviceKey)) {
            return false;
        }
        if (!Objects.equals(this.serviceName, other.serviceName)) {
            return false;
        }
        if (!Objects.equals(this.releaseGroupKey, other.releaseGroupKey)) {
            return false;
        }
        if (!Objects.equals(this.releaseGroupName, other.releaseGroupName)) {
            return false;
        }
        if (!Objects.equals(this.instanceKey, other.instanceKey)) {
            return false;
        }
        if (this.instanceName != other.instanceName) {
            return false;
        }
        if (!Objects.equals(this.versionName, other.versionName)) {
            return false;
        }
        if (!Objects.equals(this.repository, other.repository)) {
            return false;
        }
        if (!Objects.equals(this.ports, other.ports)) {
            return false;
        }
        return true;
    }

}
