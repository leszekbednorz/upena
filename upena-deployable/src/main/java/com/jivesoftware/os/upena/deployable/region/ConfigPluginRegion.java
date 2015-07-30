package com.jivesoftware.os.upena.deployable.region;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.jivesoftware.os.mlogger.core.MetricLogger;
import com.jivesoftware.os.mlogger.core.MetricLoggerFactory;
import com.jivesoftware.os.upena.config.UpenaConfigStore;
import com.jivesoftware.os.upena.deployable.soy.SoyRenderer;
import com.jivesoftware.os.upena.service.UpenaStore;
import com.jivesoftware.os.upena.shared.Cluster;
import com.jivesoftware.os.upena.shared.ClusterKey;
import com.jivesoftware.os.upena.shared.Host;
import com.jivesoftware.os.upena.shared.HostKey;
import com.jivesoftware.os.upena.shared.Instance;
import com.jivesoftware.os.upena.shared.InstanceFilter;
import com.jivesoftware.os.upena.shared.InstanceKey;
import com.jivesoftware.os.upena.shared.ReleaseGroup;
import com.jivesoftware.os.upena.shared.ReleaseGroupKey;
import com.jivesoftware.os.upena.shared.Service;
import com.jivesoftware.os.upena.shared.ServiceKey;
import com.jivesoftware.os.upena.shared.TimestampedValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 *
 */
// soy.page.configPluginRegion
public class ConfigPluginRegion implements PageRegion<Optional<ConfigPluginRegion.ConfigPluginRegionInput>> {

    private static final MetricLogger log = MetricLoggerFactory.getLogger();
    private static final ObjectMapper mapper = new ObjectMapper();

    private final String template;
    private final SoyRenderer renderer;
    private final UpenaStore upenaStore;
    private final UpenaConfigStore configStore;

    public ConfigPluginRegion(String template,
        SoyRenderer renderer,
        UpenaStore upenaStore,
        UpenaConfigStore configStore) {
        this.template = template;
        this.renderer = renderer;
        this.upenaStore = upenaStore;
        this.configStore = configStore;
    }

    public void modified(String user, Map<String, Map<String, String>> property_InstanceKey_Values) throws Exception {

        Set<String> instanceKeys = new HashSet<>();
        for (Map.Entry<String, Map<String, String>> property_InstanceKey_Value : property_InstanceKey_Values.entrySet()) {
            for (Map.Entry<String, String> instanceKey_value : property_InstanceKey_Value.getValue().entrySet()) {
                instanceKeys.add(instanceKey_value.getKey());
            }
        }

        for (String instanceKey : instanceKeys) {
            Instance instance = upenaStore.instances.get(new InstanceKey(instanceKey));
            if (instance != null) {
                Map<String, String> serviceDefaults = configStore.get(instanceKey, "default", null);
                Map<String, String> serviceOverrides = configStore.get(instanceKey, "override", null);
                Map<String, String> healthDefaults = configStore.get(instanceKey, "default-health", null);
                Map<String, String> healthOverrides = configStore.get(instanceKey, "override-health", null);

                boolean modifiedServiceOverrides = false;
                boolean modifiedHealthOverrides = false;

                Set<String> removeServiceDefaults = Sets.newHashSet();
                Set<String> removeServiceOverrides = Sets.newHashSet();
                Set<String> removeHealthDefaults = Sets.newHashSet();
                Set<String> removeHealthOverrides = Sets.newHashSet();

                for (Map.Entry<String, Map<String, String>> propEntry : property_InstanceKey_Values.entrySet()) {
                    if (propEntry.getValue().containsKey(instanceKey)) {
                        String property = propEntry.getKey();
                        String value = propEntry.getValue().get(instanceKey);
                        if (value != null && value.equals("OBSOLETE")) {
                            if (serviceDefaults.containsKey(property)) {
                                removeServiceDefaults.add(property);
                            }
                            if (serviceOverrides.containsKey(property)) {
                                removeServiceOverrides.add(property);
                            }
                            if (healthDefaults.containsKey(property)) {
                                removeHealthDefaults.add(property);
                            }
                            if (healthOverrides.containsKey(property)) {
                                removeHealthOverrides.add(property);
                            }
                        } else {
                            if (value == null || value.isEmpty() || value.equals(serviceDefaults.get(property))) {
                                removeServiceOverrides.add(property);
                                log.info("Reverting to default for property:" + property + " for instance:" + instanceKey);
                            } else {
                                serviceOverrides.put(property, value);
                                modifiedServiceOverrides = true;
                                log.info("Setting property:" + property + "=" + value + " for instance:" + instanceKey);
                            }

                            if (value == null || value.isEmpty() || value.equals(healthDefaults.get(property))) {
                                removeHealthOverrides.add(property);
                                log.info("Reverting to default for property:" + property + " for instance:" + instanceKey);
                            } else {
                                healthOverrides.put(property, value);
                                modifiedHealthOverrides = true;
                                log.info("Setting property:" + property + "=" + value + " for instance:" + instanceKey);
                            }
                        }
                    }
                }

                if (modifiedServiceOverrides) {
                    configStore.putAll(instanceKey, "override", serviceOverrides);
                }

                if (modifiedHealthOverrides) {
                    configStore.putAll(instanceKey, "override-health", healthOverrides);
                }

                if (!removeServiceDefaults.isEmpty()) {
                    configStore.remove(instanceKey, "default", removeServiceDefaults);
                }
                if (!removeServiceOverrides.isEmpty()) {
                    configStore.remove(instanceKey, "override", removeServiceOverrides);
                }
                if (!removeHealthDefaults.isEmpty()) {
                    configStore.remove(instanceKey, "default-health", removeHealthDefaults);
                }
                if (!removeHealthOverrides.isEmpty()) {
                    configStore.remove(instanceKey, "override-health", removeHealthOverrides);
                }

            } else {
                log.warn("Failed to load instance for key:" + instanceKey + " when trying to modify properties.");
            }
        }

        upenaStore.record(user, "modified", System.currentTimeMillis(), "", "config", property_InstanceKey_Values.toString());

    }

    private static class ExportImportCluster {

        public Map<ClusterKey, Cluster> clusters = new HashMap<>();
        public Map<HostKey, Host> hosts = new HashMap<>();
        public Map<ServiceKey, Service> services = new HashMap<>();
        public Map<ReleaseGroupKey, ReleaseGroup> release = new HashMap<>();
        public Map<InstanceKey, Instance> instances = new HashMap<>();

        public List<String> config = new ArrayList<>();
        public List<String> healthConfig = new ArrayList<>();

    }

    public String export(ConfigPluginRegionInput input) throws Exception {

        ConcurrentSkipListMap<String, List<Map<String, String>>> properties = new ConcurrentSkipListMap<>();
        InstanceFilter filter = new InstanceFilter(
            input.aClusterKey.isEmpty() ? null : new ClusterKey(input.aClusterKey),
            input.aHostKey.isEmpty() ? null : new HostKey(input.aHostKey),
            input.aServiceKey.isEmpty() ? null : new ServiceKey(input.aServiceKey),
            input.aReleaseKey.isEmpty() ? null : new ReleaseGroupKey(input.aReleaseKey),
            input.aInstance.isEmpty() ? null : Integer.parseInt(input.aInstance),
            0,
            10000);

        ExportImportCluster exportImportCluster = new ExportImportCluster();
        if (filter.clusterKey != null
            || filter.hostKey != null
            || filter.serviceKey != null
            || filter.releaseGroupKey != null
            || filter.logicalInstanceId != null) {

            Map<InstanceKey, TimestampedValue<Instance>> found = upenaStore.instances.find(filter);
            if (found != null) {
                for (Map.Entry<InstanceKey, TimestampedValue<Instance>> entrySet : found.entrySet()) {
                    if (!entrySet.getValue().getTombstoned()) {
                        InstanceKey key = entrySet.getKey();
                        Instance instance = entrySet.getValue().getValue();

                        exportImportCluster.instances.put(key, instance);

                        if (!exportImportCluster.clusters.containsKey(instance.clusterKey)) {
                            Cluster got = upenaStore.clusters.get(instance.clusterKey);
                            if (got == null) {
                                return "Export failed no cluster for clusterKey:" + instance.clusterKey;
                            }
                            exportImportCluster.clusters.put(instance.clusterKey, got);
                        }

                        if (!exportImportCluster.hosts.containsKey(instance.hostKey)) {
                            Host got = upenaStore.hosts.get(instance.hostKey);
                            if (got == null) {
                                return "Export failed no host for hostKey:" + instance.hostKey;
                            }
                            exportImportCluster.hosts.put(instance.hostKey, got);
                        }

                        if (!exportImportCluster.services.containsKey(instance.serviceKey)) {
                            Service got = upenaStore.services.get(instance.serviceKey);
                            if (got == null) {
                                return "Export failed no serivce for serviceKey:" + instance.serviceKey;
                            }
                            exportImportCluster.services.put(instance.serviceKey, got);
                        }

                        if (!exportImportCluster.release.containsKey(instance.releaseGroupKey)) {
                            ReleaseGroup got = upenaStore.releaseGroups.get(instance.releaseGroupKey);
                            if (got == null) {
                                return "Export failed no release group for releaseGroupKey:" + instance.releaseGroupKey;
                            }
                            exportImportCluster.release.put(instance.releaseGroupKey, got);
                        }
                    }
                }

                for (Map.Entry<InstanceKey, TimestampedValue<Instance>> entrySet : found.entrySet()) {
                    InstanceKey key = entrySet.getKey();
                    TimestampedValue<Instance> timestampedValue = entrySet.getValue();
                    Instance i = timestampedValue.getValue();
                    Cluster cluster = upenaStore.clusters.get(i.clusterKey);
                    Host host = upenaStore.hosts.get(i.hostKey);
                    Service service = upenaStore.services.get(i.serviceKey);
                    ReleaseGroup release = upenaStore.releaseGroups.get(i.releaseGroupKey);

                    if (input.service) {
                        Map<String, String> overriddenServiceMap = configStore.get(key.getKey(), "override", null);
                        append(exportImportCluster.config, key, overriddenServiceMap);

                    }
                    if (input.health) {
                        Map<String, String> overriddenHealtheMap = configStore.get(key.getKey(), "override-health", null);
                        append(exportImportCluster.healthConfig, key, overriddenHealtheMap);
                    }
                }
            }
        }

        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(exportImportCluster);

    }

    public void append(List<String> config, InstanceKey instanceKey, Map<String, String> properties) {

        for (Entry<String, String> p : properties.entrySet()) {
            StringBuilder sb = new StringBuilder();
            sb.append(instanceKey).append(", ")
                .append(p.getKey()).append(", ")
                .append(p.getValue()).append("\n");
            config.add(sb.toString());
        }
    }

    public static class ConfigPluginRegionInput {

        final String aClusterKey;
        final String aCluster;
        final String aHostKey;
        final String aHost;
        final String aServiceKey;
        final String aService;
        final String aInstance;
        final String aReleaseKey;
        final String aRelease;

        final String bClusterKey;
        final String bCluster;
        final String bHostKey;
        final String bHost;
        final String bServiceKey;
        final String bService;
        final String bInstance;
        final String bReleaseKey;
        final String bRelease;

        final String property;
        final String value;
        final boolean overridden;
        final boolean service;
        final boolean health;
        final String action;

        public ConfigPluginRegionInput(
            String aClusterKey, String aCluster, String aHostKey, String aHost, String aServiceKey, String aService, String aInstance,
            String aReleaseKey, String aRelease,
            String bClusterKey, String bCluster, String bHostKey, String bHost, String bServiceKey, String bService,
            String bInstance, String bReleaseKey, String bRelease,
            String property, String value,
            boolean overridden, boolean service, boolean health, String action) {
            this.aClusterKey = aClusterKey;
            this.aCluster = aCluster;
            this.aHostKey = aHostKey;
            this.aHost = aHost;
            this.aServiceKey = aServiceKey;
            this.aService = aService;
            this.aInstance = aInstance;
            this.aReleaseKey = aReleaseKey;
            this.aRelease = aRelease;
            this.bClusterKey = bClusterKey;
            this.bCluster = bCluster;
            this.bHostKey = bHostKey;
            this.bHost = bHost;
            this.bServiceKey = bServiceKey;
            this.bService = bService;
            this.bInstance = bInstance;
            this.bReleaseKey = bReleaseKey;
            this.bRelease = bRelease;
            this.property = property;
            this.value = value;
            this.overridden = overridden;
            this.service = service;
            this.health = health;
            this.action = action;
        }

    }

    @Override
    public String render(String user, Optional<ConfigPluginRegionInput> optionalInput) {
        Map<String, Object> data = Maps.newHashMap();

        try {
            if (optionalInput.isPresent()) {
                ConfigPluginRegionInput input = optionalInput.get();

                Map<String, String> aFilters = new HashMap<>();
                aFilters.put("clusterKey", input.aClusterKey);
                aFilters.put("cluster", input.aCluster);
                aFilters.put("hostKey", input.aHostKey);
                aFilters.put("host", input.aHost);
                aFilters.put("serviceKey", input.aServiceKey);
                aFilters.put("service", input.aService);
                aFilters.put("instance", input.aInstance);
                aFilters.put("releaseKey", input.aReleaseKey);
                aFilters.put("release", input.aRelease);
                data.put("aFilters", aFilters);

                Map<String, String> bFilters = new HashMap<>();
                bFilters.put("clusterKey", input.bClusterKey);
                bFilters.put("cluster", input.bCluster);
                bFilters.put("hostKey", input.bHostKey);
                bFilters.put("host", input.bHost);
                bFilters.put("serviceKey", input.bServiceKey);
                bFilters.put("service", input.bService);
                bFilters.put("instance", input.bInstance);
                bFilters.put("releaseKey", input.bReleaseKey);
                bFilters.put("release", input.bRelease);
                data.put("bFilters", bFilters);

                data.put("property", input.property);
                data.put("value", input.value);
                data.put("overridden", input.overridden);
                data.put("service", input.service);
                data.put("health", input.health);

                ConcurrentSkipListMap<String, List<Map<String, String>>> as = packProperties(input.aClusterKey,
                    input.aHostKey, input.aServiceKey, input.aInstance, input.aReleaseKey, input.property, input.value,
                    input.overridden, input.service, input.health);

                if (input.action.equals("revert")) {
                    Map<String, Map<String, String>> property_instanceKey_revert = new HashMap<>();
                    for (String property : as.keySet()) {
                        for (Map<String, String> occurence : as.get(property)) {
                            String instanceKey = occurence.get("instanceKey");
                            Map<String, String> revert = property_instanceKey_revert.get(property);
                            if (revert == null) {
                                revert = new HashMap<>();
                                property_instanceKey_revert.put(property, revert);
                            }
                            revert.put(instanceKey, "OBSOLETE");
                        }
                    }
                    if (!property_instanceKey_revert.isEmpty()) {
                        modified(user, property_instanceKey_revert);
                    }

                    as = packProperties(input.aClusterKey,
                        input.aHostKey, input.aServiceKey, input.aInstance, input.aReleaseKey, input.property, input.value,
                        input.overridden, input.service, input.health);
                }

                ConcurrentSkipListMap<String, List<Map<String, String>>> bs = packProperties(input.bClusterKey,
                    input.bHostKey, input.bServiceKey, input.bInstance, input.bReleaseKey, input.property, input.value,
                    input.overridden, input.service, input.health);

                Set<String> allProperties = Collections.newSetFromMap(new ConcurrentSkipListMap<String, Boolean>());
                allProperties.addAll(as.keySet());
                allProperties.addAll(bs.keySet());

                String[] ks = new String[]{"instanceKey", "cluster", "host", "service", "instance", "override", "default"};
                List<Map<String, Object>> rows = new ArrayList<>();
                for (String property : allProperties) {

                    Map<String, Object> propertyAndOccurrences = new HashMap<>();
                    propertyAndOccurrences.put("property", property);

                    List<Map<String, String>> al = as.get(property);
                    List<Map<String, String>> bl = bs.get(property);

                    List<Map<String, String>> hasProperty = new ArrayList<>();
                    int s = Math.max((al == null) ? 0 : al.size(), (bl == null) ? 0 : bl.size());
                    for (int i = 0; i < s; i++) {
                        Map<String, String> has = new HashMap<>();

                        if (al != null && i < al.size()) {
                            Map<String, String> a = al.get(i);
                            for (String k : ks) {
                                has.put("a" + k, a.get(k));
                            }
                        } else {
                            for (String k : ks) {
                                has.put("a" + k, null);
                            }
                        }
                        if (bl != null && i < bl.size()) {
                            Map<String, String> b = bl.get(i);
                            for (String k : ks) {
                                has.put("b" + k, b.get(k));
                            }
                        } else {
                            for (String k : ks) {
                                has.put("b" + k, null);
                            }
                        }
                        hasProperty.add(has);
                    }
                    propertyAndOccurrences.put("has", hasProperty);
                    if (!hasProperty.isEmpty()) {
                        rows.add(propertyAndOccurrences);
                    }
                }

                data.put("properties", rows);

            }
        } catch (Exception e) {
            log.error("Unable to retrieve data", e);
        }

        return renderer.render(template, data);
    }

    private ConcurrentSkipListMap<String, List<Map<String, String>>> packProperties(String clusterKey,
        String hostKey, String serviceKey, String instance, String releaseKey, String propertyContains,
        String valueContains, boolean overridden, boolean service, boolean health) throws Exception {

        ConcurrentSkipListMap<String, List<Map<String, String>>> properties = new ConcurrentSkipListMap<>();
        InstanceFilter filter = new InstanceFilter(
            clusterKey.isEmpty() ? null : new ClusterKey(clusterKey),
            hostKey.isEmpty() ? null : new HostKey(hostKey),
            serviceKey.isEmpty() ? null : new ServiceKey(serviceKey),
            releaseKey.isEmpty() ? null : new ReleaseGroupKey(releaseKey),
            instance.isEmpty() ? null : Integer.parseInt(instance),
            0,
            10000);
        if (filter.clusterKey != null
            || filter.hostKey != null
            || filter.serviceKey != null
            || filter.releaseGroupKey != null
            || filter.logicalInstanceId != null) {

            Map<InstanceKey, TimestampedValue<Instance>> found = upenaStore.instances.find(filter);
            for (Map.Entry<InstanceKey, TimestampedValue<Instance>> entrySet : found.entrySet()) {
                InstanceKey key = entrySet.getKey();
                TimestampedValue<Instance> timestampedValue = entrySet.getValue();
                Instance i = timestampedValue.getValue();

                if (service) {
                    Map<String, String> defaultServiceMaps = configStore.get(key.getKey(), "default", null);
                    Map<String, String> overriddenServiceMap = configStore.get(key.getKey(), "override", null);

                    filterProperties(key, i, properties, defaultServiceMaps, overriddenServiceMap, propertyContains, valueContains, overridden);
                }
                if (health) {

                    Map<String, String> defaultHealthMaps = configStore.get(key.getKey(), "default-health", null);
                    Map<String, String> overriddenHealtheMap = configStore.get(key.getKey(), "override-health", null);
                    filterProperties(key, i, properties, defaultHealthMaps, overriddenHealtheMap, propertyContains, valueContains, overridden);
                }

            }
        }
        return properties;
    }

    private void filterProperties(InstanceKey key,
        Instance instance,
        ConcurrentSkipListMap<String, List<Map<String, String>>> properties,
        Map<String, String> defaultServiceMaps,
        Map<String, String> overriddenServiceMap,
        String propertyContains,
        String valueContains,
        boolean isOverridden) throws Exception {

        for (String property : defaultServiceMaps.keySet()) {
            if (!propertyContains.isEmpty() && !property.contains(propertyContains)) {
                continue;
            }

            List<Map<String, String>> occurences = properties.get(property);
            if (occurences == null) {
                occurences = new ArrayList<>();
                properties.put(property, occurences);
            }

            String defaultValue = defaultServiceMaps.get(property);
            String overiddenValue = overriddenServiceMap.get(property);

            if (!valueContains.isEmpty()) {
                if (overiddenValue == null) {
                    if (!defaultValue.contains(valueContains)) {
                        continue;
                    }
                } else {
                    if (!overiddenValue.contains(valueContains)) {
                        continue;
                    }
                }
            }
            if (isOverridden && overiddenValue == null) {
                continue;
            }

            Map<String, String> occurence = new HashMap<>();
            occurence.put("instanceKey", key.getKey());
            occurence.put("clusterKey", instance.clusterKey.getKey());
            occurence.put("cluster", upenaStore.clusters.get(instance.clusterKey).name);
            occurence.put("hostKey", instance.hostKey.getKey());
            occurence.put("host", upenaStore.hosts.get(instance.hostKey).name);
            occurence.put("serviceKey", instance.serviceKey.getKey());
            occurence.put("service", upenaStore.services.get(instance.serviceKey).name);
            occurence.put("instance", String.valueOf(instance.instanceId));
            occurence.put("override", overriddenServiceMap.get(property));
            occurence.put("default", defaultServiceMaps.get(property));

            occurences.add(occurence);
        }
    }

    @Override
    public String getTitle() {
        return "Instance Config";
    }

}
