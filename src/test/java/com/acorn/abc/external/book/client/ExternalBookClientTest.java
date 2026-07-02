package com.acorn.abc.external.book.client;

import com.acorn.abc.external.book.config.BookApiProperties;
import com.acorn.abc.external.book.dto.ProviderBookData;
import com.acorn.abc.external.book.dto.ProviderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ExternalBookClientTest {

    private static final String ISBN = "9788966260959";

    private BookApiProperties enabledProperties() {
        BookApiProperties properties = new BookApiProperties();
        properties.setEnabled(true);
        BookApiProperties.NaverClient naverClient = new BookApiProperties.NaverClient();
        naverClient.setId("id");
        naverClient.setSecret("secret");
        properties.getNaver().setClients(List.of(naverClient));
        properties.getAladin().setKeys(List.of("ttbkey"));
        properties.getInfonaru().setKey("infokey");
        return properties;
    }

    @Test
    void naverClient_mapsFirstItem() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://openapi.naver.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(containsString("/v1/search/book.json")))
                .andRespond(withSuccess(
                        "{\"items\":[{\"title\":\"클린코드\",\"author\":\"로버트 마틴\",\"publisher\":\"인사이트\","
                                + "\"image\":\"http://img\",\"description\":\"desc\",\"pubdate\":\"20131224\"}]}",
                        MediaType.APPLICATION_JSON));

        NaverBookClient client = new NaverBookClient(builder.build(), enabledProperties());
        ProviderBookData data = client.fetch(ISBN);

        assertThat(data.status()).isEqualTo(ProviderStatus.SUCCESS_HAS_DATA);
        assertThat(data.title()).isEqualTo("클린코드");
        assertThat(data.author()).isEqualTo("로버트 마틴");
        assertThat(data.pubDate()).isEqualTo("20131224");
        server.verify();
    }

    @Test
    void naverClient_returnsNoDataWhenEmptyItems() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://openapi.naver.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(containsString("/v1/search/book.json")))
                .andRespond(withSuccess("{\"items\":[]}", MediaType.APPLICATION_JSON));

        NaverBookClient client = new NaverBookClient(builder.build(), enabledProperties());
        ProviderBookData data = client.fetch(ISBN);

        assertThat(data.status()).isEqualTo(ProviderStatus.SUCCESS_NO_DATA);
    }

    @Test
    void naverClient_notTriedWhenDisabled() {
        BookApiProperties properties = enabledProperties();
        properties.setEnabled(false);
        NaverBookClient client = new NaverBookClient(
                RestClient.builder().baseUrl("https://openapi.naver.com").build(), properties);

        assertThat(client.fetch(ISBN).status()).isEqualTo(ProviderStatus.NOT_TRIED);
    }

    @Test
    void aladinClient_mapsFirstItemFromTextHtml() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://www.aladin.co.kr");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(containsString("/ttb/api/ItemLookUp.aspx")))
                .andRespond(withSuccess(
                        "{\"item\":[{\"title\":\"클린코드\",\"author\":\"마틴\",\"publisher\":\"인사이트\","
                                + "\"cover\":\"http://cover\",\"description\":\"aladin desc\",\"pubDate\":\"2013-12-24\","
                                + "\"categoryId\":\"2724\"}]}",
                        MediaType.TEXT_HTML));

        AladinBookClient client = new AladinBookClient(builder.build(), enabledProperties());
        ProviderBookData data = client.fetch(ISBN);

        assertThat(data.status()).isEqualTo(ProviderStatus.SUCCESS_HAS_DATA);
        assertThat(data.pubDate()).isEqualTo("2013-12-24");
        assertThat(data.categoryId()).isEqualTo("2724");
        assertThat(data.coverImageUrl()).isEqualTo("http://cover");
        server.verify();
    }

    @Test
    void aladinClient_returnsFailedWhenErrorCode() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://www.aladin.co.kr");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(containsString("/ttb/api/ItemLookUp.aspx")))
                .andRespond(withSuccess("{\"errorCode\":8,\"errorMessage\":\"invalid key\"}", MediaType.TEXT_HTML));

        AladinBookClient client = new AladinBookClient(builder.build(), enabledProperties());
        ProviderBookData data = client.fetch(ISBN);

        assertThat(data.status()).isEqualTo(ProviderStatus.FAILED);
    }

    @Test
    void infoNaruClient_mapsBookDetail() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://data4library.kr");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(containsString("/api/srchDtlList")))
                .andRespond(withSuccess(
                        "{\"response\":{\"detail\":[{\"book\":{\"bookname\":\"클린코드\",\"authors\":\"마틴\","
                                + "\"publisher\":\"인사이트\",\"bookImageURL\":\"http://img\",\"description\":\"desc\","
                                + "\"publication_year\":\"2013\"}}]}}",
                        MediaType.APPLICATION_JSON));

        InfoNaruBookClient client = new InfoNaruBookClient(builder.build(), enabledProperties());
        ProviderBookData data = client.fetch(ISBN);

        assertThat(data.status()).isEqualTo(ProviderStatus.SUCCESS_HAS_DATA);
        assertThat(data.title()).isEqualTo("클린코드");
        assertThat(data.author()).isEqualTo("마틴");
        assertThat(data.pubDate()).isEqualTo("2013");
        server.verify();
    }

    @Test
    void infoNaruClient_returnsNoDataWhenEmptyDetail() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://data4library.kr");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(containsString("/api/srchDtlList")))
                .andRespond(withSuccess("{\"response\":{\"detail\":[]}}", MediaType.APPLICATION_JSON));

        InfoNaruBookClient client = new InfoNaruBookClient(builder.build(), enabledProperties());
        ProviderBookData data = client.fetch(ISBN);

        assertThat(data.status()).isEqualTo(ProviderStatus.SUCCESS_NO_DATA);
    }
}
