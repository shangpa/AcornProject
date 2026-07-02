package com.acorn.abc.book.repository;

import com.acorn.abc.book.domain.Book;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long>, JpaSpecificationExecutor<Book> {

    boolean existsByIsbn(String isbn);

    Optional<Book> findByIsbn(String isbn);

    @Query("SELECT DISTINCT b FROM Book b LEFT JOIN b.publisher p " +
            "WHERE (b.isbn IS NULL OR b.isbn <> :isbn) " +
            "AND (b.title LIKE :titleLike " +
            "  OR (:publisher IS NOT NULL AND p.publisherName LIKE :publisherLike) " +
            "  OR (:author IS NOT NULL AND EXISTS (" +
            "    SELECT 1 FROM BookAuthor ba WHERE ba.book = b AND ba.author.authorName LIKE :authorLike))" +
            ") " +
            "ORDER BY b.bookId DESC")
    List<Book> findPossibleDuplicates(
            @Param("isbn") String isbn,
            @Param("titleLike") String titleLike,
            @Param("publisher") String publisher,
            @Param("publisherLike") String publisherLike,
            @Param("author") String author,
            @Param("authorLike") String authorLike,
            Pageable pageable
    );

    @Query("SELECT DISTINCT b FROM Book b LEFT JOIN b.publisher p " +
            "WHERE b.status = 'AVAILABLE' " +
            "AND (b.title LIKE :like OR b.isbn LIKE :like OR p.publisherName LIKE :like " +
            "  OR EXISTS (SELECT 1 FROM BookAuthor ba WHERE ba.book = b AND ba.author.status = 'ACTIVE' AND ba.author.authorName LIKE :like) " +
            "  OR EXISTS (SELECT 1 FROM BookKeywordMap bkm WHERE bkm.book = b AND bkm.keyword.status = 'ACTIVE' AND bkm.keyword.keywordName LIKE :like)" +
            ") " +
            "AND (:categoryId IS NULL OR EXISTS (SELECT 1 FROM BookCategory bc WHERE bc.book = b AND bc.category.categoryId = :categoryId)) " +
            "AND (:rentalType IS NULL OR b.rentalType = :rentalType)")
    Page<Book> search(
            @Param("like") String like,
            @Param("categoryId") Long categoryId,
            @Param("rentalType") String rentalType,
            Pageable pageable
    );

    @Query("SELECT b FROM Book b WHERE b.status = 'AVAILABLE' " +
            "AND (:baseBookId IS NULL OR b.bookId <> :baseBookId) " +
            "AND (:type <> 'AUTHOR' OR EXISTS (" +
            "  SELECT 1 FROM BookAuthor ba WHERE ba.book = b AND ba.author.authorId IN (" +
            "    SELECT baseBa.author.authorId FROM BookAuthor baseBa WHERE baseBa.book.bookId = :baseBookId)" +
            ")) " +
            "AND (:type <> 'CATEGORY' OR EXISTS (" +
            "  SELECT 1 FROM BookCategory bc WHERE bc.book = b AND bc.category.categoryId IN (" +
            "    SELECT baseBc.category.categoryId FROM BookCategory baseBc WHERE baseBc.book.bookId = :baseBookId)" +
            ")) " +
            "AND (:type <> 'KEYWORD' OR EXISTS (" +
            "  SELECT 1 FROM BookKeywordMap bkm WHERE bkm.book = b AND bkm.keyword.keywordId IN (" +
            "    SELECT baseBkm.keyword.keywordId FROM BookKeywordMap baseBkm WHERE baseBkm.book.bookId = :baseBookId)" +
            ")) " +
            "ORDER BY b.rentalCount DESC, b.averageRating DESC, b.createdAt DESC, b.bookId DESC")
    List<Book> findRecommendations(
            @Param("baseBookId") Long baseBookId,
            @Param("type") String type,
            Pageable pageable
    );

    @Query("SELECT cb.book FROM CollectionBook cb " +
            "WHERE cb.collection.collectionId = :collectionId " +
            "AND cb.collection.status = 'ACTIVE' AND cb.book.status = 'AVAILABLE' " +
            "ORDER BY cb.displayOrder ASC NULLS LAST, cb.createdAt ASC, cb.book.createdAt DESC")
    List<Book> findByCollectionId(@Param("collectionId") Long collectionId, Pageable pageable);
}
