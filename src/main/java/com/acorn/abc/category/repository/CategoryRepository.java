package com.acorn.abc.category.repository;

import com.acorn.abc.book.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByStatusOrderByDisplayOrderAscCategoryIdAsc(String status);

    Optional<Category> findByAladinCategoryId(String aladinCategoryId);

    Optional<Category> findByParentIsNullAndCategoryName(String categoryName);

    Optional<Category> findByParentAndCategoryName(Category parent, String categoryName);
}
