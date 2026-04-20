package naughty.tuzamate.global.config;

import lombok.RequiredArgsConstructor;
import naughty.tuzamate.auth.resolver.UserIdInfoResolver;
import naughty.tuzamate.auth.resolver.UserInfoResolver;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import io.netty.channel.ChannelOption;

import java.time.Duration;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final UserInfoResolver userInfoResolver;
    private final UserIdInfoResolver userIdInfoResolver;

    // WebClient과 RestTemplate 모두 최대 20개의 커넥션을 유지하도록 설정
    @Bean
    public WebClient webClient() {
        ConnectionProvider provider = ConnectionProvider.builder("benchmark")
                .maxConnections(20)
                .pendingAcquireTimeout(Duration.ofSeconds(10))
                .build();
        HttpClient httpClient = HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(5));
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Bean
    public RestTemplate restTemplate() {
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(20);
        connectionManager.setDefaultMaxPerRoute(20);
        connectionManager.setDefaultConnectionConfig(ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(5000))
                .setSocketTimeout(Timeout.ofMilliseconds(5000))
                .setValidateAfterInactivity(Timeout.ofMilliseconds(1000))
                .build());

        RequestConfig requestConfig = RequestConfig.custom()
                .setResponseTimeout(Timeout.ofMilliseconds(5000))
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .setRetryStrategy(new DefaultHttpRequestRetryStrategy(0, Timeout.ofMilliseconds(0)))
                .build();

        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);

        return new RestTemplate(factory);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(userInfoResolver);
        resolvers.add(userIdInfoResolver);
    }
}
