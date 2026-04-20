package naughty.tuzamate.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;


@Configuration
public class ThreadPoolConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        return createExecutor("Stock-Thread-");
    }

    // KRX와 NASDAQ 각각 별도의 스레드 풀을 사용하여 독립적으로 작업 처리
    @Bean(name = "krxTaskExecutor")
    public Executor krxTaskExecutor() {
        return createExecutor("KRX-Thread-");
    }

    @Bean(name = "nasdaqTaskExecutor")
    public Executor nasdaqTaskExecutor() {
        return createExecutor("NASDAQ-Thread-");
    }

    private ThreadPoolTaskExecutor createExecutor(String threadNamePrefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
