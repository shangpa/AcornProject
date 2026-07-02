package com.acorn.abc.external.book.service;

import com.acorn.abc.common.exception.BusinessException;
import com.acorn.abc.common.exception.ErrorCode;
import com.acorn.abc.external.book.client.AladinBookClient;
import com.acorn.abc.external.book.client.InfoNaruBookClient;
import com.acorn.abc.external.book.client.NaverBookClient;
import com.acorn.abc.external.book.dto.BookMetadataResult;
import com.acorn.abc.external.book.dto.ProviderBookData;
import com.acorn.abc.external.book.dto.ProviderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookMetadataAggregateServiceTest {

    @Mock
    private InfoNaruBookClient infoNaruBookClient;
    @Mock
    private NaverBookClient naverBookClient;
    @Mock
    private AladinBookClient aladinBookClient;

    @InjectMocks
    private BookMetadataAggregateService service;

    @Test
    void normalizeIsbn13_stripsHyphenAndSpace() {
        assertThat(service.normalizeIsbn13("978-89-6626-095-9")).isEqualTo("9788966260959");
        assertThat(service.normalizeIsbn13(" 9788966260959 ")).isEqualTo("9788966260959");
    }

    @Test
    void normalizeIsbn13_rejectsInvalidFormat() {
        assertThatThrownBy(() -> service.normalizeIsbn13("12345"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThatThrownBy(() -> service.normalizeIsbn13("abcdefghijklm"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.normalizeIsbn13(null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void aggregate_appliesFieldPriority() {
        when(infoNaruBookClient.fetch("9788966260959")).thenReturn(new ProviderBookData(
                ProviderStatus.SUCCESS_HAS_DATA, "InfoTitle", "InfoAuthor", "InfoPublisher",
                "info-cover", "short", "2013", null, null));
        when(naverBookClient.fetch("9788966260959")).thenReturn(new ProviderBookData(
                ProviderStatus.SUCCESS_HAS_DATA, "NaverTitle", "NaverAuthor", "NaverPublisher",
                "naver-cover", "medium desc", "20131224", null, null));
        when(aladinBookClient.fetch("9788966260959")).thenReturn(new ProviderBookData(
                ProviderStatus.SUCCESS_HAS_DATA, "AladinTitle", "AladinAuthor", "AladinPublisher",
                "aladin-cover", "the longest description here", "2013-12-24", "2724",
                "국내도서 > 컴퓨터/모바일 > 프로그래밍 > 개발방법론"));

        BookMetadataResult result = service.aggregate("9788966260959");

        assertThat(result.title()).isEqualTo("InfoTitle");            // info > aladin > naver
        assertThat(result.author()).isEqualTo("InfoAuthor");          // info > naver > aladin
        assertThat(result.publisher()).isEqualTo("InfoPublisher");    // info > aladin > naver
        assertThat(result.coverImageUrl()).isEqualTo("aladin-cover"); // aladin > info > naver
        assertThat(result.description()).isEqualTo("the longest description here"); // longest
        assertThat(result.pubDate()).isEqualTo("2013-12-24");         // aladin > naver > info
        assertThat(result.aladinCategoryId()).isEqualTo("2724");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void aggregate_fallsBackWhenHigherPriorityMissing() {
        when(infoNaruBookClient.fetch("9788966260959")).thenReturn(ProviderBookData.noData());
        when(naverBookClient.fetch("9788966260959")).thenReturn(new ProviderBookData(
                ProviderStatus.SUCCESS_HAS_DATA, "NaverTitle", "NaverAuthor", "NaverPublisher",
                "naver-cover", "naver desc", "20131224", null, null));
        when(aladinBookClient.fetch("9788966260959")).thenReturn(ProviderBookData.noData());

        BookMetadataResult result = service.aggregate("9788966260959");

        assertThat(result.title()).isEqualTo("NaverTitle");
        assertThat(result.coverImageUrl()).isEqualTo("naver-cover");
        assertThat(result.warnings()).isNotEmpty(); // no-data warnings collected
    }

    @Test
    void aggregate_collectsProviderFailureWarnings() {
        when(infoNaruBookClient.fetch("9788966260959")).thenReturn(ProviderBookData.failedRetryable());
        when(naverBookClient.fetch("9788966260959")).thenReturn(new ProviderBookData(
                ProviderStatus.SUCCESS_HAS_DATA, "T", "A", "P", "c", "d", "20200101", null, null));
        when(aladinBookClient.fetch("9788966260959")).thenReturn(ProviderBookData.failed());

        BookMetadataResult result = service.aggregate("9788966260959");

        assertThat(result.infonaru()).isEqualTo(ProviderStatus.FAILED_RETRYABLE);
        assertThat(result.aladin()).isEqualTo(ProviderStatus.FAILED);
        assertThat(result.warnings()).anyMatch(w -> w.contains("정보나루"));
        assertThat(result.warnings()).anyMatch(w -> w.contains("알라딘"));
    }

    @Test
    void aggregate_addsWarningWhenTitleMissing() {
        when(infoNaruBookClient.fetch("9788966260959")).thenReturn(ProviderBookData.noData());
        when(naverBookClient.fetch("9788966260959")).thenReturn(ProviderBookData.noData());
        when(aladinBookClient.fetch("9788966260959")).thenReturn(ProviderBookData.noData());

        BookMetadataResult result = service.aggregate("9788966260959");

        assertThat(result.title()).isNull();
        assertThat(result.warnings()).anyMatch(w -> w.contains("제목"));
    }
}
