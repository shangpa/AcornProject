package com.acorn.abc.book.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "category", uniqueConstraints = {
        @UniqueConstraint(name = "uk_category_aladin_category_id", columnNames = "aladin_category_id"),
        @UniqueConstraint(name = "uk_category_parent_name", columnNames = {"parent_category_id", "category_name"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Long categoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_category_id")
    private Category parent;

    @Column(name = "category_name", nullable = false, length = 100)
    private String categoryName;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "aladin_category_id", length = 20)
    private String aladinCategoryId;

    @Column(name = "aladin_category_name", length = 255)
    private String aladinCategoryName;

    public static Category create(String categoryName, Category parent, int displayOrder) {
        Category category = new Category();
        category.categoryName = categoryName;
        category.parent = parent;
        category.displayOrder = displayOrder;
        category.status = "ACTIVE";
        return category;
    }

    public void linkAladinCategory(String aladinCategoryId, String aladinCategoryName) {
        this.aladinCategoryId = aladinCategoryId;
        this.aladinCategoryName = aladinCategoryName;
    }
}
