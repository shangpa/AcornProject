package com.acorn.abc.category.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 알라딘 카테고리(id + "국내도서>소설>..." 형식 name)를 내부 category 테이블에 자동 매핑한다.
 * 실제 INSERT는 REQUIRES_NEW로 격리된 AladinCategoryUpsertService가 담당하며,
 * UNIQUE 제약 충돌(DataIntegrityViolationException) 시 재조회로 기존 category를 재사용한다.
 */
@Service
@RequiredArgsConstructor
public class AladinCategoryImportService {

    private final AladinCategoryUpsertService upsertService;

    public Optional<Long> importOrGet(String aladinCategoryId, String aladinCategoryName) {
        if (aladinCategoryId == null || aladinCategoryId.isBlank()) {
            return Optional.empty();
        }
        List<String> path = parsePath(aladinCategoryName);
        if (path.isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.of(upsertService.createOrGet(aladinCategoryId, aladinCategoryName, path));
        } catch (DataIntegrityViolationException e) {
            return upsertService.findLeafId(aladinCategoryId, path);
        }
    }

    private List<String> parsePath(String aladinCategoryName) {
        if (aladinCategoryName == null || aladinCategoryName.isBlank()) {
            return List.of();
        }
        return Arrays.stream(aladinCategoryName.split(">"))
                .map(String::trim)
                .filter(segment -> !segment.isEmpty())
                .toList();
    }
}
