package naughty.tuzamate.global.config;

import lombok.RequiredArgsConstructor;
import naughty.tuzamate.auth.resolver.UserIdInfoResolver;
import naughty.tuzamate.auth.resolver.UserInfoResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final UserInfoResolver userInfoResolver;
    private final UserIdInfoResolver userIdInfoResolver;

    @Value("${stock.api.timeout.connect-ms:2000}")
    private int connectTimeoutMs;

    @Value("${stock.api.timeout.read-ms:5000}")
    private int readTimeoutMs;

    @Bean

    public WebClient webClient() {
        return WebClient.builder().build();
    }

    @Bean
    public RestTemplate restTemplate() {
        // 무한 대기 방지를 위해 타임아웃 명시
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(requestFactory);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(userInfoResolver);
        resolvers.add(userIdInfoResolver);
    }
}
