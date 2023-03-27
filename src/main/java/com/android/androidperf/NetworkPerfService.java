package com.android.androidperf;

import javafx.application.Platform;
import javafx.scene.chart.XYChart;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class NetworkPerfService extends BasePerfService {
    private static final Logger LOGGER = LogManager.getLogger(NetworkPerfService.class);

    public static final String SERVICE_NAME = "Network";
    public static final String APP_STRING = "App";

    @Override
    public String getServiceName() {
        return "Network";
    }

    static class NetStatsData {
        public long mRxBytes = 0;
        public long mRxPackets = 0;
        public long mTxBytes = 0;
        public long mTxPackets = 0;

        public NetStatsData() {}

        public NetStatsData(long _mRxBytes, long _mRxPackets, long _mTxBytes, long _mTxPackets) {
            mRxBytes = _mRxBytes;
            mRxPackets = _mRxPackets;
            mTxBytes = _mTxBytes;
            mTxPackets = _mTxPackets;
        }

        public static NetStatsData subtract(NetStatsData l, NetStatsData r) {
            var ret = new NetStatsData();
            ret.mRxBytes = l.mRxBytes - r.mRxBytes;
            ret.mRxPackets = l.mRxPackets - r.mRxPackets;
            ret.mTxBytes = l.mTxBytes - r.mTxBytes;
            ret.mTxPackets = l.mTxPackets - r.mTxPackets;
            return ret;
        }
    }

    private Map<String, NetStatsData> lastStats = new LinkedHashMap<>();

    static long convertToLong(byte[] bytes, int index)
    {
        long value = 0L;

        try {
            // Iterating through for loop
            for (int i = index + 8 - 1; i >= index ; i--) {
                // Shifting previous value 8 bits to right and
                // add it with next value
                value = (value << 8) + (bytes[i] & 255);
            }
        } catch (Exception e) {
            LOGGER.error(e);
        }

        return value;
    }

    private NetStatsData fromBytes(byte[] bytes) {
        long[] data = new long[4];
        for (int i = 0; i < 4; i++) {
            data[i] = convertToLong(bytes, i * 8);
        }
        return new NetStatsData(data[0], data[1], data[2], data[3]);
    }

    Map<String, NetStatsData> acquireNetworkData() {
        var ret = new LinkedHashMap<String, NetStatsData>();

        // get traffic by contacting with AndroidPerfServer
        byte[] byteData = device.sendMSG(String.format("network %d", device.getTargetPackageUid()));
        NetStatsData netStatsData = fromBytes(byteData);
        LOGGER.debug(String.format("rx %d %d, tx %d %d", netStatsData.mRxBytes, netStatsData.mRxPackets, netStatsData.mTxBytes, netStatsData.mTxPackets));
        ret.put(APP_STRING, netStatsData);

        // get interface traffic by reading /proc/net/dev, and analyse it
        String []activeInterface = device.execCmd("ifconfig -S | cut -d' ' -f1").split("\n");
        if (activeInterface.length > 0 && activeInterface[0].startsWith("Error executing adb cmd"))
            return ret;

        String cmd = "cat /proc/net/dev | awk '{sub(/:/,\"\",$1); print $1,$2,$3,$10,$11}'" + " | grep -Ei '" +
                    String.join("|", activeInterface) + "'";
        String []intfTraffic = device.execCmd(cmd).split("\n");
        for (String traffic: intfTraffic) {
            String []s_array = traffic.split(" ");
            long []l_array = Arrays.stream(s_array, 1, 5).mapToLong(Long::parseLong).toArray();
            NetStatsData data = new NetStatsData(l_array[0], l_array[1], l_array[2], l_array[3]);
            ret.put(s_array[0], data);
        }

        return ret;
    }

    @Override
    void update() {
        var controller = device.getController();
        var retrievedData = acquireNetworkData();
        var chartAppendData = new LinkedHashMap<String, XYChart.Data<Number, Number>>();

        if (retrievedData.size() == 0)
            return;

        retrievedData.forEach((intf, data) -> {
            var deltaData = NetStatsData.subtract(data, lastStats.computeIfAbsent(intf, k -> data));

            chartAppendData.put(String.format("%s recv", intf), new XYChart.Data<>(timer, deltaData.mRxBytes / 1024));
            chartAppendData.put(String.format("%s send", intf), new XYChart.Data<>(timer, deltaData.mTxBytes / 1024));
        });

        lastStats = retrievedData;
        Platform.runLater(() -> chart.addDataToChart(chartAppendData));

        super.update();
    }
}
