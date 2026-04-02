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

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(16); // 최대 쓰레드 개수
        executor.setQueueCapacity(100); // 대기 큐의 최대 크기
        executor.setAllowCoreThreadTimeOut(true);


        executor.setThreadNamePrefix("Stock-Thread-"); // 쓰레드 이름 접두사 설정

        /**
         * ThreadPoolExecutor의 기본 정책은 AbortPolicy로, 큐가 가득 찼을 때 예외를 발생시킴
         * 작업 요청한 스레드에서 직접 그 일 처리하도록 한다.
         *
         */
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        executor.initialize();
        return executor;
    }
}
