package com.acorn.abc.external.book.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 외부 도서 API 호출용 RestClient 3개 bean 구성.
 * provider별 base URL과 connect/read timeout을 적용한다.
 */
@Configuration
public class BookApiClientConfig {

    private static final String NAVER_BASE_URL = "https://openapi.naver.com";
    private static final String ALADIN_BASE_URL = "https://www.aladin.co.kr";
    private static final String INFONARU_BASE_URL = "http://data4library.kr";

    private SimpleClientHttpRequestFactory requestFactory(BookApiProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.getTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.getTimeoutMs()));
        return factory;
    }

    @Bean
    public RestClient naverRestClient(BookApiProperties properties) {
        return RestClient.builder()
                .baseUrl(NAVER_BASE_URL)
                .requestFactory(requestFactory(properties))
                .build();
    }

    @Bean
    public RestClient aladinRestClient(BookApiProperties properties) {
        return RestClient.builder()
                .baseUrl(ALADIN_BASE_URL)
                .requestFactory(requestFactory(properties))
                .build();
    }

    @Bean
    public RestClient infoNaruRestClient(BookApiProperties properties) {
        return RestClient.builder()
                .baseUrl(INFONARU_BASE_URL)
                .requestFactory(requestFactory(properties))
                .build();
    }
}
