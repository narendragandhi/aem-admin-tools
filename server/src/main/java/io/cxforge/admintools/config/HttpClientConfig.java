package io.cxforge.admintools.config;

import lombok.RequiredArgsConstructor;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class HttpClientConfig {

    private final AemConfig aemConfig;
    private final LlmConfig llmConfig;

    @Bean(name = "aemHttpClient")
    public CloseableHttpClient aemHttpClient() {
        AemConfig.Timeout timeout = aemConfig.getTimeout();

        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(timeout.getConnection()))
                .setSocketTimeout(Timeout.ofMilliseconds(timeout.getSocket()))
                .build();

        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setDefaultConnectionConfig(connectionConfig);
        connectionManager.setMaxTotal(50);
        connectionManager.setDefaultMaxPerRoute(20);

        RequestConfig requestConfig = RequestConfig.custom()
                .setResponseTimeout(Timeout.ofMilliseconds(timeout.getRequest()))
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(timeout.getConnection()))
                .build();

        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .build();
    }

    @Bean(name = "llmHttpClient")
    public CloseableHttpClient llmHttpClient() {
        LlmConfig.Timeout timeout = llmConfig.getTimeout();

        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(timeout.getConnection()))
                .setSocketTimeout(Timeout.ofMilliseconds(timeout.getSocket()))
                .build();

        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setDefaultConnectionConfig(connectionConfig);
        connectionManager.setMaxTotal(20);
        connectionManager.setDefaultMaxPerRoute(10);

        RequestConfig requestConfig = RequestConfig.custom()
                .setResponseTimeout(Timeout.ofMilliseconds(timeout.getRequest()))
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(timeout.getConnection()))
                .build();

        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .build();
    }
}
