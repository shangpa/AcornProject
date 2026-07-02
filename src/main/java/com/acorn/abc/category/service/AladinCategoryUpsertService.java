package com.acorn.abc.category.service;

import com.acorn.abc.book.domain.Category;
import com.acorn.abc.category.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 알라딘 카테고리 경로 upsert를 approve 트랜잭션과 분리된 REQUIRES_NEW 트랜잭션으로 처리한다.
 * UNIQUE 제약 충돌 시 바깥 트랜잭션이 rollback-only로 오염되지 않도록 별도 트랜잭션으로 격리한다.
 */
@Service
@RequiredArgsConstructor
public class AladinCategoryUpsertService {

    private final CategoryRepository categoryRepository;

    /**
     * aladinCategoryId로 기존 leaf가 있으면 재사용, 없으면 경로(path)를 root부터 순서대로 생성/재사용하고
     * 마지막 leaf에 aladin 카테고리를 연결한다. leaf categoryId 반환.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long createOrGet(String aladinCategoryId, String aladinCategoryName, List<String> path) {
        Optional<Category> existing = categoryRepository.findByAladinCategoryId(aladinCategoryId);
        if (existing.isPresent()) {
            return existing.get().getCategoryId();
        }

        Category parent = null;
        Category node = null;
        for (String name : path) {
            node = (parent == null)
                    ? categoryRepository.findByParentIsNullAndCategoryName(name).orElse(null)
                    : categoryRepository.findByParentAndCategoryName(parent, name).orElse(null);
            if (node == null) {
                node = categoryRepository.save(Category.create(name, parent, 0));
            }
            parent = node;
        }

        node.linkAladinCategory(aladinCategoryId, aladinCategoryName);
        categoryRepository.save(node);
        return node.getCategoryId();
    }

    /**
     * 경합으로 다른 트랜잭션이 먼저 생성한 경우, 기존 category를 재조회한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<Long> findLeafId(String aladinCategoryId, List<String> path) {
        Optional<Category> byId = categoryRepository.findByAladinCategoryId(aladinCategoryId);
        if (byId.isPresent()) {
            return Optional.of(byId.get().getCategoryId());
        }

        Category parent = null;
        Category node = null;
        for (String name : path) {
            Optional<Category> found = (parent == null)
                    ? categoryRepository.findByParentIsNullAndCategoryName(name)
                    : categoryRepository.findByParentAndCategoryName(parent, name);
            if (found.isEmpty()) {
                return Optional.empty();
            }
            node = found.get();
            parent = node;
        }
        return node == null ? Optional.empty() : Optional.of(node.getCategoryId());
    }
}
