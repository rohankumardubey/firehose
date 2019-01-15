package com.gojek.esb.factory;

import com.gojek.de.stencil.StencilClient;
import com.gojek.de.stencil.StencilClientFactory;
import com.gojek.esb.config.AuditConfig;
import com.gojek.esb.config.ExponentialBackOffProviderConfig;
import com.gojek.esb.config.KafkaConsumerConfig;
import com.gojek.esb.config.RetryQueueConfig;
import com.gojek.esb.consumer.EsbGenericConsumer;
import com.gojek.esb.consumer.FireHoseConsumer;
import com.gojek.esb.exception.EglcConfigurationException;
import com.gojek.esb.filter.EsbMessageFilter;
import com.gojek.esb.filter.Filter;
import com.gojek.esb.metrics.StatsDReporter;
import com.gojek.esb.sink.BackOff;
import com.gojek.esb.sink.BackOffProvider;
import com.gojek.esb.sink.ExponentialBackOffProvider;
import com.gojek.esb.sink.Sink;
import com.gojek.esb.sink.clevertap.ClevertapSinkFactory;
import com.gojek.esb.sink.db.DBSinkFactory;
import com.gojek.esb.sink.http.HttpSinkFactory;
import com.gojek.esb.sink.influxdb.InfluxSinkFactory;
import com.gojek.esb.sink.log.LogSinkFactory;
import com.gojek.esb.sink.SinkWithRetryQueue;
import com.gojek.esb.sink.SinkWithRetry;
import com.gojek.esb.util.Clock;
import com.timgroup.statsd.NonBlockingStatsDClient;
import com.timgroup.statsd.StatsDClient;
import org.aeonbits.owner.ConfigFactory;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

public class FireHoseConsumerFactory {

    private Map<String, String> config = System.getenv();
    private final KafkaConsumerConfig kafkaConsumerConfig;
    private final StatsDReporter statsDReporter;
    private final Clock clockInstance;
    private static final Logger LOGGER = LoggerFactory.getLogger(FireHoseConsumerFactory.class);
    private StencilClient stencilClient;


    public FireHoseConsumerFactory(KafkaConsumerConfig kafkaConsumerConfig) {
        this.kafkaConsumerConfig = kafkaConsumerConfig;
        LOGGER.info("--------- Config ---------");
        LOGGER.info(this.kafkaConsumerConfig.getKafkaAddress());
        LOGGER.info(this.kafkaConsumerConfig.getKafkaTopic());
        LOGGER.info(this.kafkaConsumerConfig.getConsumerGroupId());
        LOGGER.info("--------- ------ ---------");
        Optional<StatsDClient> statsDClient = Optional.empty();
        try {
            statsDClient = Optional.of(new NonBlockingStatsDClient("firehose", this.kafkaConsumerConfig.getStatsDHost(),
                    this.kafkaConsumerConfig.getStatsDPort()));
        } catch (Exception e) {
            LOGGER.error("Exception on creating StatsD client, disabling StatsD and Audit client", e);
            LOGGER.error("FireHose is running without collecting any metrics!!!!!!!!");
        }
        clockInstance = new Clock();
        statsDReporter = new StatsDReporter(statsDClient, clockInstance, this.kafkaConsumerConfig.getStatsDTags().split(","));
        String stencilUrl = this.kafkaConsumerConfig.stencilUrl();
        stencilClient = this.kafkaConsumerConfig.enableStencilClient()
                ? StencilClientFactory.getClient(stencilUrl, config)
                : StencilClientFactory.getClient();


    }

    /**
     * Helps to build consumer based on the config.
     *
     * @return FireHoseConsumer
     */
    public FireHoseConsumer buildConsumer() {

        AuditConfig auditConfig = ConfigFactory.create(AuditConfig.class, config);
        Filter filter = new EsbMessageFilter(kafkaConsumerConfig);
        GenericKafkaFactory genericKafkaFactory = new GenericKafkaFactory();

        EsbGenericConsumer consumer = genericKafkaFactory.createConsumer(kafkaConsumerConfig, auditConfig, config, statsDReporter, filter);

        Sink retrySink = withRetry(getSink(), genericKafkaFactory);
        return new FireHoseConsumer(consumer, retrySink, statsDReporter, clockInstance);
    }

    /**
     * return the basic Sink implementation based on the config.
     *
     * @return Sink
     */
    private Sink getSink() {
        switch (kafkaConsumerConfig.getSinkType()) {
            case DB:
                return new DBSinkFactory().create(config, statsDReporter, stencilClient);
            case HTTP:
                return new HttpSinkFactory().create(config, statsDReporter, stencilClient);
            case INFLUXDB:
                return new InfluxSinkFactory().create(config, statsDReporter, stencilClient);
            case LOG:
                return new LogSinkFactory().create(config, statsDReporter, stencilClient);
            case CLEVERTAP:
                return new ClevertapSinkFactory().create(config, statsDReporter, stencilClient);
            default:
                throw new EglcConfigurationException("Invalid FireHose SINK type");

        }
    }

    /**
     * to enable the retry feature for the basic sinks based on the config.
     *
     * @param basicSink
     * @param genericKafkaFactory
     * @return Sink
     */
    private Sink withRetry(Sink basicSink, GenericKafkaFactory genericKafkaFactory) {
        ExponentialBackOffProviderConfig backOffConfig = ConfigFactory.create(
                ExponentialBackOffProviderConfig.class, config);
        BackOffProvider backOffProvider = new ExponentialBackOffProvider(backOffConfig.exponentialBackoffInitialTimeInMs(),
                backOffConfig.exponentialBackoffRate(), backOffConfig.exponentialBackoffMaximumBackoffInMs(), statsDReporter, new BackOff());

        if (kafkaConsumerConfig.getRetryQueueEnabled()) {
            RetryQueueConfig retryQueueConfig = ConfigFactory.create(RetryQueueConfig.class, config);
            KafkaProducer<byte[], byte[]> kafkaProducer = genericKafkaFactory.getKafkaProducer(retryQueueConfig);

            return new SinkWithRetryQueue(new SinkWithRetry(basicSink, backOffProvider, statsDReporter, kafkaConsumerConfig.getMaximumRetryAttempts()),
                    kafkaProducer, retryQueueConfig.getRetryTopic(), statsDReporter, backOffProvider);
        } else {
            return new SinkWithRetry(basicSink, backOffProvider, statsDReporter);
        }
    }
}