package com.pcbaecker.domain.books;

import com.pcbaecker.config.security.UserHasRolePublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("books")
public class BookRestController {

    private final BookUsercase usecase;

    public BookRestController(BookUsercase usecase) {
        this.usecase = usecase;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Iterable<Book>> getAll() {
        return new ResponseEntity<>(this.usecase.findAll(), HttpStatus.OK);
    }

    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Book> getById(@PathVariable("id") Long id) {
        Book book = this.usecase.findById(id);
        if (book == null) {
            return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(book, HttpStatus.OK);
    }

    // We can build our own custom annotations to replace the @Secured annotation
    @UserHasRolePublisher
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Book> create(@RequestBody CreateBookRequest request) {
        Book created = this.usecase.create(request.title, request.author);
        if (created == null) {
            return ResponseEntity.internalServerError().build();
        }
        return ResponseEntity.ok(created);
    }

    // We can use the @Secured annotation to check for roles
    @Secured({"ROLE_PUBLISHER", "ROLE_AUTHOR"})
    @PostMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Book> update(@RequestBody UpdateBookRequest request) {
        Book created = this.usecase.create(request.title, request.author);
        if (created == null) {
            return ResponseEntity.internalServerError().build();
        }
        return ResponseEntity.ok(created);
    }

    public record CreateBookRequest(
            String title,
            String author
    ) {
    }
    public record UpdateBookRequest(
            String title,
            String author
    ) {
    }
}
