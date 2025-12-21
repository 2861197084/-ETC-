package com.etc.config;

import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class HBaseConfig {

    @Bean(destroyMethod = "close")
    public Connection hbaseConnection(
            @Value("${HBASE_ZOOKEEPER_QUORUM:zookeeper}") String quorum) throws IOException {
        ParsedQuorum parsed = ParsedQuorum.parse(quorum);

        org.apache.hadoop.conf.Configuration config = HBaseConfiguration.create();
        config.set("hbase.zookeeper.quorum", parsed.host);
        config.set("hbase.zookeeper.property.clientPort", String.valueOf(parsed.port));
        return ConnectionFactory.createConnection(config);
    }

    private record ParsedQuorum(String host, int port) {
        static ParsedQuorum parse(String input) {
            if (input == null || input.isBlank()) return new ParsedQuorum("zookeeper", 2181);
            String trimmed = input.trim();
            if (!trimmed.contains(":")) return new ParsedQuorum(trimmed, 2181);
            String[] parts = trimmed.split(":", 2);
            String host = parts[0].isBlank() ? "zookeeper" : parts[0];
            int port = 2181;
            try {
                port = Integer.parseInt(parts[1]);
            } catch (Exception ignored) {
            }
            return new ParsedQuorum(host, port);
        }
    }
}
