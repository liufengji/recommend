package com.victor.kafkastream;

import com.victor.commons.conf.ConfigurationManager;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.processor.TopologyBuilder;

import java.util.Properties;

public class KafakaStreamApp {

    public static void main(String[] args) {

        String broker = ConfigurationManager.config().getString("kafka.broker.list");
        String zookeeper = ConfigurationManager.config().getString("zookeeper.list");

        String from = "prelog";
        String to = "log";

        Properties settings = new Properties();
        settings.put(StreamsConfig.APPLICATION_ID_CONFIG,"logProccor");
        settings.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG,broker);
        settings.put(StreamsConfig.ZOOKEEPER_CONNECT_CONFIG,zookeeper);

        StreamsConfig config = new StreamsConfig(settings);

        //topo结构，  from -> 自定义处理器 -> to
        // ...parentNames 表示 连接的谁，数据何处来
        TopologyBuilder builder = new TopologyBuilder();
        builder.addSource("source",from)
                .addProcessor("process",()-> new LogProcessor(),"source")
                .addSink("sink",to,"process");

        KafkaStreams streams = new KafkaStreams(builder,config);
        streams.start();

    }
}
