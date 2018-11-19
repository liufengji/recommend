package com.victor.bussiness.utils;

import com.victor.commons.conf.ConfigurationManager;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.Jedis;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Configuration
public class Configure {

    private String esClusterName;
    private String esHost;
    private int esPort;
    private String redisHost;

    public Configure() {
        this.esClusterName = ConfigurationManager.config().getString("es.cluster.name");
        this.esHost = ConfigurationManager.config().getString("es.transportHosts").split(":")[0];
        this.esPort = Integer.parseInt(ConfigurationManager.config().getString("es.transportHosts").split(":")[1]);
        this.redisHost = ConfigurationManager.config().getString("jedis.host");
    }

    @Bean(name = "transportClient")
    public TransportClient getTransportClient() throws UnknownHostException {
        Settings settings = Settings.builder().put("cluster.name", esClusterName).build();
        TransportClient esClient = new PreBuiltTransportClient(settings);
        esClient.addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(esHost), esPort));
        return esClient;
    }

    @Bean(name = "jedis")
    public Jedis getRedisClient() {
        Jedis jedis = new Jedis(redisHost);
        return jedis;
    }
}
