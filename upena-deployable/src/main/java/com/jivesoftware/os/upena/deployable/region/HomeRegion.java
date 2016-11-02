package com.jivesoftware.os.upena.deployable.region;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.jivesoftware.os.mlogger.core.MetricLogger;
import com.jivesoftware.os.mlogger.core.MetricLoggerFactory;
import com.jivesoftware.os.upena.deployable.region.HomeRegion.HomeInput;
import com.jivesoftware.os.upena.deployable.soy.SoyRenderer;
import com.jivesoftware.os.upena.service.UpenaStore;
import com.jivesoftware.os.upena.shared.Host;
import com.jivesoftware.os.upena.shared.HostKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.CentralProcessor.TickType;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HWDiskStore;
import oshi.hardware.HWPartition;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.hardware.PowerSource;
import oshi.hardware.Sensors;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;
import oshi.software.os.OperatingSystem.ProcessSort;
import oshi.util.FormatUtil;
import oshi.util.Util;

/**
 *
 */
public class HomeRegion implements PageRegion<HomeInput> {

    private static final MetricLogger LOG = MetricLoggerFactory.getLogger();

    private final String template;
    private final SoyRenderer renderer;
    private final HostKey hostKey;
    private final UpenaStore upenaStore;

    public HomeRegion(String template,
        SoyRenderer renderer,
        HostKey hostKey,
        UpenaStore upenaStore) {

        this.template = template;
        this.renderer = renderer;
        this.hostKey = hostKey;
        this.upenaStore = upenaStore;
    }

    @Override
    public String getRootPath() {
        return "/";
    }

    public static class HomeInput implements PluginInput {

        public HomeInput() {
        }

        @Override
        public String name() {
            return "Upena";
        }

    }

    @Override
    public String render(String user, HomeInput input) {

        Subject s;
        try {
            s = SecurityUtils.getSubject();
        } catch (Exception x) {
            s = null;
            LOG.error("Failure.", x);
        }
        Subject subject = s;
        Map<String, Object> data = Maps.newHashMap();
        
        List<Map<String, String>> instances = new ArrayList<>();
        try {
            upenaStore.hosts.scan((HostKey key, Host value) -> {
                instances.add(ImmutableMap.of("host", value.name,
                    "name", value.name,
                    "port", String.valueOf(value.port),
                    "path", "/ui"));
                return true;
            });
        } catch (Exception x) {
            LOG.error("Failure.", x);
        }

        if (!instances.isEmpty() && subject != null && subject.isAuthenticated()) {
            data.put("instances", instances);
        }

        SystemInfo si = null;
        try {
            si = new SystemInfo();
        } catch (Exception x) {
            LOG.warn("oshi SystemInfo failed.", x);
        }
        if (si != null) {
            HardwareAbstractionLayer hal = null;
            try {
                hal = si.getHardware();
                data.put("procs", printProcessor(hal));
                data.put("memory", printMemory(hal));
                data.put("cpu", printCpu(hal));
                data.put("sensor", printSensors(hal));
                data.put("power", printPowerSources(hal));
                data.put("nic", printNetworkInterfaces(hal));
                data.put("disk", printDisks(hal));

            } catch (Exception x) {
                LOG.warn("oshi HardwareAbstractionLayer failed.", x);
            }

            try {
                OperatingSystem os = si.getOperatingSystem();
                data.put("fs", printFileSystem(os));
                data.put("os", Collections.singletonList(os.toString()));
                if (hal != null) {
                    data.put("process", printProcesses(os, hal));
                } else {
                    data.put("process", Collections.singletonList("ERROR"));
                }

            } catch (Exception x) {
                LOG.warn("oshi OperatingSystem failed.", x);
            }
        }

        try {
            return renderer.render(template, data);
        } catch (Exception x) {
            LOG.warn("soy render failed.", x);
            return "Woop :(";
        }
    }

    private final AtomicReference<List<String>> procs = new AtomicReference<>();
    private final AtomicReference<List<String>> memory = new AtomicReference<>();
    private final AtomicReference<List<String>> cpu = new AtomicReference<>();
    private final AtomicReference<List<String>> sensor = new AtomicReference<>();
    private final AtomicReference<List<String>> power = new AtomicReference<>();
    private final AtomicReference<List<String>> nic = new AtomicReference<>();
    private final AtomicReference<List<String>> disk = new AtomicReference<>();

    @Override
    public String getTitle() {
        return "Upena";
    }

    private static List<String> printProcessor(HardwareAbstractionLayer hal) {
        List<String> l = new ArrayList<>();
        try {
            CentralProcessor processor = hal.getProcessor();
            l.add(processor.toString());
            l.add(" " + processor.getPhysicalProcessorCount() + " physical CPU(s)");
            l.add(" " + processor.getLogicalProcessorCount() + " logical CPU(s)");

            l.add("Identifier: " + processor.getIdentifier());
            l.add("Serial Num: " + processor.getSystemSerialNumber());
        } catch (Exception x) {
            l.add("ERROR");
            LOG.warn("Failure", x);
        }
        return l;
    }

    private static List<String> printMemory(HardwareAbstractionLayer hal) {
        List<String> l = new ArrayList<>();
        try {
            GlobalMemory memory = hal.getMemory();
            l.add("Memory: " + FormatUtil.formatBytes(memory.getAvailable()) + "/" + FormatUtil.formatBytes(memory.getTotal()));
            l.add("Swap used: " + FormatUtil.formatBytes(memory.getSwapUsed()) + "/" + FormatUtil.formatBytes(memory.getSwapTotal()));
        } catch (Exception x) {
            l.add("ERROR");
            LOG.warn("Failure", x);
        }
        return l;
    }

    private static List<String> printCpu(HardwareAbstractionLayer hal) {
        List<String> l = new ArrayList<>();
        try {
            CentralProcessor processor = hal.getProcessor();
            l.add("Uptime: " + FormatUtil.formatElapsedSecs(processor.getSystemUptime()));

            long[] prevTicks = processor.getSystemCpuLoadTicks();
            l.add("CPU, IOWait, and IRQ ticks @ 0 sec:" + Arrays.toString(prevTicks));
            // Wait a second...
            Util.sleep(1000);
            long[] ticks = processor.getSystemCpuLoadTicks();
            l.add("CPU, IOWait, and IRQ ticks @ 1 sec:" + Arrays.toString(ticks));
            long user = ticks[TickType.USER.getIndex()] - prevTicks[TickType.USER.getIndex()];
            long nice = ticks[TickType.NICE.getIndex()] - prevTicks[TickType.NICE.getIndex()];
            long sys = ticks[TickType.SYSTEM.getIndex()] - prevTicks[TickType.SYSTEM.getIndex()];
            long idle = ticks[TickType.IDLE.getIndex()] - prevTicks[TickType.IDLE.getIndex()];
            long iowait = ticks[TickType.IOWAIT.getIndex()] - prevTicks[TickType.IOWAIT.getIndex()];
            long irq = ticks[TickType.IRQ.getIndex()] - prevTicks[TickType.IRQ.getIndex()];
            long softirq = ticks[TickType.SOFTIRQ.getIndex()] - prevTicks[TickType.SOFTIRQ.getIndex()];
            long totalCpu = user + nice + sys + idle + iowait + irq + softirq;

            l.add(String.format(
                "User: %.1f%% Nice: %.1f%% System: %.1f%% Idle: %.1f%% IOwait: %.1f%% IRQ: %.1f%% SoftIRQ: %.1f%%%n",
                100d * user / totalCpu, 100d * nice / totalCpu, 100d * sys / totalCpu, 100d * idle / totalCpu,
                100d * iowait / totalCpu, 100d * irq / totalCpu, 100d * softirq / totalCpu));
            l.add(String.format("CPU load: %.1f%% (counting ticks)%n", processor.getSystemCpuLoadBetweenTicks() * 100));
            l.add(String.format("CPU load: %.1f%% (OS MXBean)%n", processor.getSystemCpuLoad() * 100));
            double[] loadAverage = processor.getSystemLoadAverage(3);
            l.add("CPU load averages:" + (loadAverage[0] < 0 ? " N/A" : String.format(" %.2f", loadAverage[0]))
                + (loadAverage[1] < 0 ? " N/A" : String.format(" %.2f", loadAverage[1]))
                + (loadAverage[2] < 0 ? " N/A" : String.format(" %.2f", loadAverage[2])));
            // per core CPU
            StringBuilder procCpu = new StringBuilder("CPU load per processor:");
            double[] load = processor.getProcessorCpuLoadBetweenTicks();
            for (double avg : load) {
                procCpu.append(String.format(" %.1f%%", avg * 100));
            }
            l.add(procCpu.toString());
        } catch (Exception x) {
            l.add("ERROR");
            LOG.warn("Failure", x);
        }
        return l;
    }

    private static List<String> printProcesses(OperatingSystem os, HardwareAbstractionLayer hal) {
        List<String> l = new ArrayList<>();
        try {
            GlobalMemory memory = hal.getMemory();
            l.add("Processes: " + os.getProcessCount() + ", Threads: " + os.getThreadCount());
            // Sort by highest CPU
            List<OSProcess> procs = Arrays.asList(os.getProcesses(5, ProcessSort.CPU));

            l.add("   PID  %CPU %MEM       VSZ       RSS Name");
            for (int i = 0; i < procs.size() && i < 5; i++) {
                OSProcess p = procs.get(i);
                l.add(String.format(" %5d, %5.1f, %4.1f, %9s, %9s, %s%n", p.getProcessID(),
                    100d * (p.getKernelTime() + p.getUserTime()) / p.getUpTime(),
                    100d * p.getResidentSetSize() / memory.getTotal(), FormatUtil.formatBytes(p.getVirtualSize()),
                    FormatUtil.formatBytes(p.getResidentSetSize()), p.getName()));
            }
        } catch (Exception x) {
            l.add("ERROR");
            LOG.warn("Failure", x);
        }
        return l;
    }

    private static List<String> printSensors(HardwareAbstractionLayer hal) {
        List<String> l = new ArrayList<>();
        try {
            Sensors sensors = hal.getSensors();
            l.add("Sensors:");
            l.add(String.format(" CPU Temperature: %.1f°C%n", sensors.getCpuTemperature()));
            l.add(" Fan Speeds: " + Arrays.toString(sensors.getFanSpeeds()));
            l.add(String.format(" CPU Voltage: %.1fV%n", sensors.getCpuVoltage()));
        } catch (Exception x) {
            l.add("ERROR");
            LOG.warn("Failure", x);
        }
        return l;
    }

    private static List<String> printPowerSources(HardwareAbstractionLayer hal) {
        List<String> l = new ArrayList<>();
        try {
            PowerSource[] powerSources = hal.getPowerSources();
            StringBuilder sb = new StringBuilder("Power: ");
            if (powerSources.length == 0) {
                sb.append("Unknown");
            } else {
                double timeRemaining = powerSources[0].getTimeRemaining();
                if (timeRemaining < -1d) {
                    sb.append("Charging");
                } else if (timeRemaining < 0d) {
                    sb.append("Calculating time remaining");
                } else {
                    sb.append(String.format("%d:%02d remaining", (int) (timeRemaining / 3600),
                        (int) (timeRemaining / 60) % 60));
                }
            }
            for (PowerSource pSource : powerSources) {
                sb.append(String.format("%n %s @ %.1f%%", pSource.getName(), pSource.getRemainingCapacity() * 100d));
            }
            l.add(sb.toString());
        } catch (Exception x) {
            l.add("ERROR");
            LOG.warn("Failure", x);
        }
        return l;
    }

    private static List<String> printDisks(HardwareAbstractionLayer hal) {
        List<String> l = new ArrayList<>();
        try {
            HWDiskStore[] diskStores = hal.getDiskStores();
            l.add("Disks:");
            for (HWDiskStore disk : diskStores) {
                boolean readwrite = disk.getReads() > 0 || disk.getWrites() > 0;
                l.add(String.format(" %s: (model: %s - S/N: %s) size: %s, reads: %s (%s), writes: %s (%s), xfer: %s ms%n",
                    disk.getName(), disk.getModel(), disk.getSerial(),
                    disk.getSize() > 0 ? FormatUtil.formatBytesDecimal(disk.getSize()) : "?",
                    readwrite ? disk.getReads() : "?", readwrite ? FormatUtil.formatBytes(disk.getReadBytes()) : "?",
                    readwrite ? disk.getWrites() : "?", readwrite ? FormatUtil.formatBytes(disk.getWriteBytes()) : "?",
                    readwrite ? disk.getTransferTime() : "?"));
                HWPartition[] partitions = disk.getPartitions();
                if (partitions == null) {
                    // TODO Remove when all OS's implemented
                    continue;
                }
                for (HWPartition part : partitions) {
                    l.add(String.format(" |-- %s: %s (%s) Maj:Min=%d:%d, size: %s%s%n", part.getIdentification(),
                        part.getName(), part.getType(), part.getMajor(), part.getMinor(),
                        FormatUtil.formatBytesDecimal(part.getSize()),
                        part.getMountPoint().isEmpty() ? "" : " @ " + part.getMountPoint()));
                }
            }
        } catch (Exception x) {
            l.add("ERROR");
            LOG.warn("Failure", x);
        }
        return l;
    }

    private static List<String> printFileSystem(OperatingSystem os) {
        List<String> l = new ArrayList<>();
        try {
            FileSystem fileSystem = os.getFileSystem();
            l.add("File System:");

            l.add(String.format(" File Descriptors: %d/%d%n", fileSystem.getOpenFileDescriptors(),
                fileSystem.getMaxFileDescriptors()));

            OSFileStore[] fileStores = fileSystem.getFileStores();
            for (OSFileStore fs : fileStores) {
                long usable = fs.getUsableSpace();
                long total = fs.getTotalSpace();
                l.add(String.format(" %s (%s) [%s] %s of %s free (%.1f%%) is %s and is mounted at %s%n", fs.getName(),
                    fs.getDescription().isEmpty() ? "file system" : fs.getDescription(), fs.getType(),
                    FormatUtil.formatBytes(usable), FormatUtil.formatBytes(fs.getTotalSpace()), 100d * usable / total,
                    fs.getVolume(), fs.getMount()));
            }
        } catch (Exception x) {
            l.add("ERROR");
            LOG.warn("Failure", x);
        }
        return l;
    }

    private static List<String> printNetworkInterfaces(HardwareAbstractionLayer hal) {
        List<String> l = new ArrayList<>();
        try {
            NetworkIF[] networkIFs = hal.getNetworkIFs();
            l.add("Network interfaces:");
            for (NetworkIF net : networkIFs) {
                l.add(String.format(" Name: %s (%s)%n", net.getName(), net.getDisplayName()));
                l.add(String.format("   MAC Address: %s %n", net.getMacaddr()));
                l.add(String.format("   MTU: %s, Speed: %s %n", net.getMTU(), FormatUtil.formatValue(net.getSpeed(), "bps")));
                l.add(String.format("   IPv4: %s %n", Arrays.toString(net.getIPv4addr())));
                l.add(String.format("   IPv6: %s %n", Arrays.toString(net.getIPv6addr())));
                boolean hasData = net.getBytesRecv() > 0 || net.getBytesSent() > 0 || net.getPacketsRecv() > 0
                    || net.getPacketsSent() > 0;
                l.add(String.format("   Traffic: received %s/%s; transmitted %s/%s %n",
                    hasData ? net.getPacketsRecv() + " packets" : "?",
                    hasData ? FormatUtil.formatBytes(net.getBytesRecv()) : "?",
                    hasData ? net.getPacketsSent() + " packets" : "?",
                    hasData ? FormatUtil.formatBytes(net.getBytesSent()) : "?"));
            }
        } catch (Exception x) {
            l.add("ERROR");
            LOG.warn("Failure", x);
        }
        return l;
    }

}
