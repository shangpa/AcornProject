package com.acorn.abc.external.book.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 외부 도서 API(정보나루/네이버/알라딘) 설정값 바인딩.
 * prefix: external.book
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "external.book")
public class BookApiProperties {

    private Infonaru infonaru = new Infonaru();
    private Naver naver = new Naver();
    private Aladin aladin = new Aladin();
    private int timeoutMs = 5000;
    private boolean enabled = false;

    @Getter
    @Setter
    public static class Infonaru {
        private String key;
    }

    @Getter
    @Setter
    public static class Naver {
        private List<NaverClient> clients = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class NaverClient {
        private String id;
        private String secret;
    }

    @Getter
    @Setter
    public static class Aladin {
        private List<String> keys = new ArrayList<>();
    }
}
