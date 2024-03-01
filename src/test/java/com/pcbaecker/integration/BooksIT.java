package com.pcbaecker.integration;

import com.pcbaecker.domain.books.Book;
import com.pcbaecker.domain.books.BookRepository;
import com.pcbaecker.domain.books.BookRestController;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
public class BooksIT {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private WebTestClient client;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PostgreSQLContainer.IMAGE);

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(
            RedisContainer.DEFAULT_IMAGE_NAME.withTag(RedisContainer.DEFAULT_TAG));

    @Test
    @WithMockUser(username = "user")
    public void getBooks() {
        // GIVEN
        this.bookRepository.saveAll(List.of(
                new Book(1L,"The Hobbit", "J.R.R. Tolkien", LocalDateTime.now(), LocalDateTime.now()),
                new Book(2L,"The Lord of the Rings", "J.R.R. Tolkien", LocalDateTime.now(), LocalDateTime.now())
        ));

        // WHEN
        client
                .get()
                .uri("/books")
                .exchange()

                // THEN
                .expectStatus().isOk()
                .expectBodyList(Book.class)
                .consumeWith(response -> {
                    List<Book> books = response.getResponseBody();
                    assertThat(books).isNotNull();
                    assertThat(books).isNotEmpty();
                });
    }


    @Test
    @WithMockUser(username = "user")
    public void getSingleBook() {
        // GIVEN
        this.bookRepository.saveAll(List.of(
                new Book(1L,"The Hobbit", "J.R.R. Tolkien", LocalDateTime.now(), LocalDateTime.now()),
                new Book(2L,"The Lord of the Rings", "J.R.R. Tolkien", LocalDateTime.now(), LocalDateTime.now())
        ));

        // WHEN
        client
                .get()
                .uri("/books/1")
                .exchange()

                // THEN
                .expectStatus().isOk()
                .expectBody(Book.class)
                .consumeWith(response -> {
                    Book book = response.getResponseBody();
                    assertThat(book).isNotNull();
                    assertThat(book.getId()).isEqualTo(1L);
                    assertThat(book.getTitle()).isEqualTo("The Hobbit");
                    assertThat(book.getAuthor()).isEqualTo("J.R.R. Tolkien");
                });
    }

    @Test
    @WithMockUser(username = "publisher", roles = "PUBLISHER")
    public void createBook() {
        // GIVEN

        // WHEN
        client
                .post()
                .uri("/books")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(new BookRestController.CreateBookRequest("mytitle", "myauthor")))
                .exchange()

                // THEN
                .expectStatus().isOk()
                .expectBody(Book.class)
                .consumeWith(response -> {
                    Book book = response.getResponseBody();
                    assertThat(book).isNotNull();
                    assertThat(book.getId()).isNotNull();
                    assertThat(book.getTitle()).isEqualTo("mytitle");
                    assertThat(book.getAuthor()).isEqualTo("myauthor");
                });
    }
}
