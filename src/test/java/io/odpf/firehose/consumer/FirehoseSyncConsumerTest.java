package io.odpf.firehose.consumer;

import io.odpf.firehose.config.KafkaConsumerConfig;
import io.odpf.firehose.consumer.kafka.ConsumerAndOffsetManager;
import io.odpf.firehose.consumer.kafka.FirehoseKafkaConsumer;
import io.odpf.firehose.consumer.kafka.OffsetManager;
import io.odpf.firehose.message.Message;
import io.odpf.firehose.filter.FilteredMessages;
import io.odpf.firehose.filter.NoOpFilter;
import io.odpf.firehose.metrics.Instrumentation;
import io.odpf.firehose.metrics.Metrics;
import io.odpf.firehose.sink.Sink;
import io.odpf.firehose.tracer.SinkTracer;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class FirehoseSyncConsumerTest {
    @Mock
    private FirehoseKafkaConsumer firehoseKafkaConsumer;
    @Mock
    private Sink sink;
    @Mock
    private Instrumentation instrumentation;
    @Mock
    private SinkTracer tracer;
    private FirehoseSyncConsumer firehoseSyncConsumer;
    private List<Message> messages;

    @Before
    public void setUp() throws Exception {
        Message msg1 = new Message(new byte[]{}, new byte[]{}, "topic", 0, 100);
        Message msg2 = new Message(new byte[]{}, new byte[]{}, "topic", 0, 100);
        messages = Arrays.asList(msg1, msg2);
        OffsetManager offsetManger = new OffsetManager();
        KafkaConsumerConfig kafkaConsumerConfig = ConfigFactory.create(KafkaConsumerConfig.class, System.getenv());
        ConsumerAndOffsetManager consumerAndOffsetManager = new ConsumerAndOffsetManager(Collections.singletonList(sink), offsetManger, firehoseKafkaConsumer, kafkaConsumerConfig, instrumentation);
        FirehoseFilter firehoseFilter = new FirehoseFilter(new NoOpFilter(instrumentation), instrumentation);
        firehoseSyncConsumer = new FirehoseSyncConsumer(sink, tracer, consumerAndOffsetManager, firehoseFilter, instrumentation);
        when(firehoseKafkaConsumer.readMessages()).thenReturn(messages);
    }

    @Test
    public void shouldProcessPartitions() throws IOException {
        firehoseSyncConsumer.process();
        verify(sink).pushMessage(messages);
    }

    @Test
    public void shouldProcessEmptyPartitions() throws IOException {
        when(firehoseKafkaConsumer.readMessages()).thenReturn(new ArrayList<>());
        firehoseSyncConsumer.process();
        verify(sink, times(0)).pushMessage(anyList());
    }

    @Test
    public void shouldSendNoOfMessagesReceivedCount() throws IOException {
        firehoseSyncConsumer.process();
        verify(instrumentation).logInfo("Processed {} records in consumer", 2);
    }

    @Test
    public void shouldCallTracerWithTheSpan() throws IOException {
        firehoseSyncConsumer.process();
        verify(sink).pushMessage(messages);
        verify(tracer).startTrace(messages);
        verify(tracer).finishTrace(any());
    }

    @Test
    public void shouldCloseConsumerIfConsumerIsNotNull() throws IOException {
        firehoseSyncConsumer.close();
        verify(instrumentation, times(1)).logInfo("closing consumer");
        verify(tracer, times(1)).close();
        verify(firehoseKafkaConsumer, times(1)).close();
        verify(sink, times(1)).close();
        verify(instrumentation, times(1)).close();
    }

    @Test
    public void shouldAddOffsetsForInvalidMessages() throws Exception {
        FirehoseFilter firehoseFilter = Mockito.mock(FirehoseFilter.class);
        ConsumerAndOffsetManager consumerAndOffsetManager = Mockito.mock(ConsumerAndOffsetManager.class);
        firehoseSyncConsumer = new FirehoseSyncConsumer(sink, tracer, consumerAndOffsetManager, firehoseFilter, instrumentation);
        Message msg1 = new Message(new byte[]{}, new byte[]{}, "topic", 0, 100);
        Message msg2 = new Message(new byte[]{}, new byte[]{}, "topic", 0, 100);
        Message msg3 = new Message(new byte[]{}, new byte[]{}, "topic", 0, 100);
        messages = Arrays.asList(msg1, msg2, msg3);
        Mockito.when(consumerAndOffsetManager.readMessages()).thenReturn(messages);
        Mockito.when(firehoseFilter.applyFilter(messages)).thenReturn(new FilteredMessages() {{
            addToValidMessages(msg3);
            addToValidMessages(msg1);
            addToInvalidMessages(msg2);
        }});
        Mockito.when(tracer.startTrace(messages)).thenReturn(new ArrayList<>());
        firehoseSyncConsumer.process();

        Mockito.verify(consumerAndOffsetManager, Mockito.times(1)).forceAddOffsetsAndSetCommittable(new ArrayList<Message>() {{
            add(msg2);
        }});
        Mockito.verify(sink, times(1)).pushMessage(new ArrayList<Message>() {{
            add(msg3);
            add(msg1);
        }});
        Mockito.verify(consumerAndOffsetManager, times(1)).addOffsetsAndSetCommittable(new ArrayList<Message>() {{
            add(msg3);
            add(msg1);
        }});
        Mockito.verify(consumerAndOffsetManager, times(1)).commit();

        verify(instrumentation, times(1)).logInfo("Processed {} records in consumer", 3);
        verify(tracer, times(1)).startTrace(messages);
        verify(tracer, times(1)).finishTrace(new ArrayList<>());
        verify(instrumentation, times(1)).captureDurationSince(eq(Metrics.SOURCE_KAFKA_PARTITIONS_PROCESS_TIME_MILLISECONDS), any(Instant.class));
    }

    @Test
    public void shouldNotCloseConsumerIfConsumerIsNull() throws IOException {
        KafkaConsumerConfig kafkaConsumerConfig = ConfigFactory.create(KafkaConsumerConfig.class, System.getenv());
        ConsumerAndOffsetManager consumerAndOffsetManager = new ConsumerAndOffsetManager(Collections.singletonList(sink), new OffsetManager(), null, kafkaConsumerConfig, instrumentation);
        FirehoseFilter firehoseFilter = new FirehoseFilter(new NoOpFilter(instrumentation), instrumentation);
        firehoseSyncConsumer = new FirehoseSyncConsumer(sink, tracer, consumerAndOffsetManager, firehoseFilter, instrumentation);
        firehoseSyncConsumer.close();
        verify(instrumentation, times(0)).logInfo("closing consumer");
        verify(tracer, times(1)).close();
        verify(firehoseKafkaConsumer, times(0)).close();
        verify(sink, times(1)).close();
        verify(instrumentation, times(1)).close();
    }
}
