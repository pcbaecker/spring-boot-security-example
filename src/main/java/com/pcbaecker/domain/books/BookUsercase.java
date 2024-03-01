package com.pcbaecker.domain.books;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Component
public class BookUsercase {

    private final BookRepository bookRepository;

    public BookUsercase(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    public Book findById(Long userId) {
        return this.bookRepository.findById(userId).orElse(null);
    }

    public Book create(String title, String author) {
        if (!StringUtils.hasText(title) || !StringUtils.hasText(author)) {
            throw new IllegalArgumentException("Title and author are required");
        }

        Book book = new Book();
        book.setTitle(title);
        book.setAuthor(author);
        book.setCreatedAt(LocalDateTime.now());
        book.setUpdatedAt(LocalDateTime.now());
        book = this.bookRepository.save(book);

        return book;
    }

    public Book update(Long id, String title, String author) {
        if (!StringUtils.hasText(title) || !StringUtils.hasText(author)) {
            throw new IllegalArgumentException("Title and author are required");
        }

        Book book = this.bookRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Book not found"));

        // We can check the authentication and authorization by asking the security context holder
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        final Book finalBook = book;
        boolean isAuthorized = auth.getAuthorities().stream().anyMatch(
                a -> a.getAuthority().equals("ROLE_PUBLISHER")
                        || (a.getAuthority().equals("ROLE_AUTHOR") && finalBook.getAuthor().equals(auth.getName())));
        if (!isAuthorized) {
            throw new RuntimeException("You are not authorized to update this book");
        }

        book.setTitle(title);
        book.setAuthor(author);
        book.setUpdatedAt(LocalDateTime.now());
        book = this.bookRepository.save(book);

        return book;
    }

    public Iterable<Book> findAll() {
        return this.bookRepository.findAll();
    }
}
