package io.odpf.firehose.config.converter;

import io.odpf.firehose.sink.dlq.DLQWriterType;
import org.aeonbits.owner.Converter;

import java.lang.reflect.Method;

public class DlqWriterTypeConverter implements Converter<DLQWriterType> {
    @Override
    public DLQWriterType convert(Method method, String input) {
        return DLQWriterType.valueOf(input.toUpperCase());
    }
}
