package com.etc.platform.config;

import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

@Configuration
public class HBaseConfig implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(HBaseConfig.class);

    /**
     * 控制是否尝试真实连接 HBase。前端联调阶段可以置为 false，避免连接超时。
     */
    @Value("${hbase.enabled:false}")
    private boolean hbaseEnabled;

    @Value("${hbase.zookeeper.quorum:localhost}")
    private String zookeeperQuorum;

    @Value("${hbase.zookeeper.clientPort:2181}")
    private String zookeeperClientPort;

    private Connection connection;

    /**
     * 创建 HBase Connection。
     * 如果本机无法解析或连接到远程 HBase（如 centos7-166 等），不会阻止应用启动，
     * 而是记录告警并返回 null，业务层会优雅地返回空数据。
     */
    @Bean
    public Connection hbaseConnection() {
        if (!hbaseEnabled) {
            log.info("HBase is disabled by config (hbase.enabled=false), running in mock mode.");
            this.connection = null;
            return null;
        }

        org.apache.hadoop.conf.Configuration config = HBaseConfiguration.create();
        config.set("hbase.zookeeper.quorum", zookeeperQuorum);
        config.set("hbase.zookeeper.property.clientPort", zookeeperClientPort);
        config.set("hbase.client.retries.number", "3");

        // 快速 DNS 预检查，避免 createConnection 在启动阶段长时间重试阻塞
        String[] hosts = zookeeperQuorum.split(",");
        for (String host : hosts) {
            String h = host.trim();
            if (h.isEmpty()) continue;
            try {
                InetAddress.getAllByName(h);
            } catch (UnknownHostException e) {
                log.warn("HBase ZK host cannot be resolved: {} , skip connecting HBase. " +
                        "All HBase-based APIs will return empty/mock data.", h);
                this.connection = null;
                return this.connection;
            }
        }

        try {
            this.connection = ConnectionFactory.createConnection(config);
            log.info("HBase connection created to {}", zookeeperQuorum);
        } catch (IOException e) {
            log.warn("Failed to create HBase connection to {}:{} - running in 'no HBase' mode. " +
                            "All HBase-based APIs will return empty data.",
                    zookeeperQuorum, zookeeperClientPort, e);
            this.connection = null;
        }
        return this.connection;
    }

    @Override
    public void destroy() {
        if (this.connection != null) {
            try {
                this.connection.close();
            } catch (IOException e) {
                log.warn("Error while closing HBase connection", e);
            }
        }
    }
}
