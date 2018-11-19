package com.victor.kafkastream;


import org.apache.kafka.streams.processor.Processor;
import org.apache.kafka.streams.processor.ProcessorContext;

//处理器
//abc
public class LogProcessor implements Processor<byte[],byte[]>{


    private ProcessorContext context;


    @Override
    public void init(ProcessorContext context) {
        this.context = context;
    }


    @Override
    public void process(byte[] key, byte[] value) {
        String input = new String(value);

        if(input.contains("abc")){
            String clear = input.split("abc")[1].trim();
            context.forward("LogProcessor".getBytes(), clear.getBytes());
        }
    }


    @Override
    public void punctuate(long timestamp) {

    }


    @Override
    public void close() {

    }
}
