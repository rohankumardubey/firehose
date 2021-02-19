package com.gojek.esb.sink.http.request;

import com.gojek.de.stencil.client.StencilClient;
import com.gojek.esb.config.HttpSinkConfig;
import com.gojek.esb.metrics.StatsDReporter;
import com.gojek.esb.sink.http.request.types.SimpleRequest;
import com.gojek.esb.sink.http.request.types.DynamicUrlRequest;
import com.gojek.esb.sink.http.request.types.ParameterizedHeaderRequest;
import com.gojek.esb.sink.http.request.types.ParameterizedUriRequest;
import com.gojek.esb.sink.http.request.types.Request;
import com.gojek.esb.sink.http.request.uri.UriParser;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertTrue;
import static org.mockito.MockitoAnnotations.initMocks;

public class RequestFactoryTest {
    @Mock
    private StencilClient stencilClient;
    @Mock
    private StatsDReporter statsDReporter;
    @Mock
    private UriParser uriParser;
    private HttpSinkConfig httpSinkConfig;

    private Map<String, String> configuration = new HashMap<>();

    @Before
    public void setup() {
        initMocks(this);
        configuration = new HashMap<String, String>();
    }

    @Test
    public void shouldReturnBatchRequestWhenPrameterSourceIsDisabledAndServiceUrlIsConstant() {
        configuration.put("sink.http.service.url", "http://127.0.0.1:1080/api");
        httpSinkConfig = ConfigFactory.create(HttpSinkConfig.class, configuration);

        Request request = new RequestFactory(statsDReporter, httpSinkConfig, stencilClient, uriParser).createRequest();

        assertTrue(request instanceof SimpleRequest);
    }

    @Test
    public void shouldReturnDynamicUrlRequestWhenPrameterSourceIsDisabledAndServiceUrlIsNotParametrised() {
        configuration.put("sink.http.service.url", "http://127.0.0.1:1080/api,%s");
        httpSinkConfig = ConfigFactory.create(HttpSinkConfig.class, configuration);

        Request request = new RequestFactory(statsDReporter, httpSinkConfig, stencilClient, uriParser).createRequest();

        assertTrue(request instanceof DynamicUrlRequest);
    }

    @Test
    public void shouldReturnParameterizedRequstWhenParameterSourceIsNotDisableAndPlacementTypeIsHeader() {
        configuration.put("sink.http.parameter.source", "key");
        configuration.put("sink.http.parameter.placement", "header");
        configuration.put("sink.http.service.url", "http://127.0.0.1:1080/api,%s");
        httpSinkConfig = ConfigFactory.create(HttpSinkConfig.class, configuration);

        Request request = new RequestFactory(statsDReporter, httpSinkConfig, stencilClient, uriParser).createRequest();

        assertTrue(request instanceof ParameterizedHeaderRequest);
    }

    @Test
    public void shouldReturnParameterizedRequstWhenParameterSourceIsNotDisableAndPlacementTypeIsQuery() {
        configuration.put("sink.http.parameter.source", "key");
        configuration.put("sink.http.parameter.placement", "query");
        configuration.put("sink.http.service.url", "http://127.0.0.1:1080/api,%s");
        httpSinkConfig = ConfigFactory.create(HttpSinkConfig.class, configuration);

        Request request = new RequestFactory(statsDReporter, httpSinkConfig, stencilClient, uriParser).createRequest();

        assertTrue(request instanceof ParameterizedUriRequest);
    }
}
