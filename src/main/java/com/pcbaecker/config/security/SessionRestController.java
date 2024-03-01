package com.pcbaecker.config.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcbaecker.domain.users.User;
import com.pcbaecker.domain.users.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import static org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;

@RestController
@RequestMapping("/")
public class SessionRestController {

    private static final String USER_AGENT = "USER_AGENT";

    private final FindByIndexNameSessionRepository<? extends Session> sessions;
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;

    public SessionRestController(FindByIndexNameSessionRepository<? extends Session> sessions, AuthenticationManager authenticationManager, UserRepository userRepository) {
        this.sessions = sessions;
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> authenticateUser(@RequestBody LoginRequest request, HttpServletRequest req) {
        Authentication authentication = authenticationManager
                .authenticate(new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        SecurityContext sc = SecurityContextHolder.getContext();
        sc.setAuthentication(authentication);
        HttpSession session = req.getSession(true);
        session.setAttribute(SPRING_SECURITY_CONTEXT_KEY, sc);
        String userAgent = StringUtils.hasText(req.getHeader("User-Agent")) ? req.getHeader("User-Agent") : "Unknown";
        session.setAttribute(USER_AGENT, userAgent);
        return new ResponseEntity<>("User login successfully!...", HttpStatus.OK);
    }

    @GetMapping(value = "/sessions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Set<SessionInfo>> getMySessions(HttpServletRequest req) {
        final String username = SecurityContextHolder.getContext().getAuthentication().getName();
        final String currentSessionId = req.getSession().getId();
        var result = this.sessions.findByPrincipalName(username).entrySet().stream().map(e -> new SessionInfo(
                DigestUtils.sha256Hex(e.getKey()),
                e.getValue().getAttribute(USER_AGENT),
                e.getValue().getCreationTime().toEpochMilli(),
                e.getValue().getLastAccessedTime().toEpochMilli(),
                currentSessionId.equals(e.getKey())
        )).collect(Collectors.toSet());
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @DeleteMapping(value = "/sessions/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> deleteSession(@PathVariable("id") String id, HttpServletRequest req) {
        final String username = SecurityContextHolder.getContext().getAuthentication().getName();
        final String currentSessionId = req.getSession().getId();
        if (DigestUtils.sha256Hex(currentSessionId).equals(id)) {
            return new ResponseEntity<>("Cannot delete current session", HttpStatus.BAD_REQUEST);
        }
        var sessionOpt = this.sessions.findByPrincipalName(username).entrySet().stream().filter(
                e -> DigestUtils.sha256Hex(e.getKey()).equals(id)).findFirst();
        if (sessionOpt.isEmpty()) {
            return new ResponseEntity<>("Session not found", HttpStatus.NOT_FOUND);
        }
        this.sessions.deleteById(sessionOpt.get().getKey());
        return new ResponseEntity<>("Session deleted", HttpStatus.OK);
    }

    @Secured("ROLE_ADMIN")
    @PostMapping(value = "/user", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<User> createUser(@RequestBody CreateUserRequest request) throws JsonProcessingException {
        User user = User.builder()
                .username(request.username())
                .password(request.password())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        user = userRepository.save(user);
        //ObjectMapper oj = new ObjectMapper();
        //System.err.println(oj.writeValueAsString(user));
        return new ResponseEntity<>(user, HttpStatus.CREATED);
    }

    public record LoginRequest(
            String username,
            String password
    ) {}

    public record SessionInfo(
            String id,
            String userAgent,
            long creationTime,
            long lastAccessedTime,
            boolean isCurrent
    ) {}

    public record CreateUserRequest(
            @NotBlank(message = "Username is mandatory")
            @Min(value = 3, message = "Username must be at least 3 characters long")
            String username,
            @NotBlank(message = "Password is mandatory")
            @Min(value = 8, message = "Password must be at least 8 characters long")
            String password
    ) {}

}
