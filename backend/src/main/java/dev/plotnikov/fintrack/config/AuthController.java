package dev.plotnikov.fintrack.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import dev.plotnikov.fintrack.user.AppUser;
import dev.plotnikov.fintrack.user.AppUserRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();
    private final LoginRateLimiter rateLimiter;

    private final AppUserRepository appUsers;
    private final FinTrackProperties properties;

    public AuthController(UserDetailsService users, PasswordEncoder encoder, LoginRateLimiter rateLimiter,
                          AppUserRepository appUsers, FinTrackProperties properties) {
        this.appUsers = appUsers;
        this.properties = properties;
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        this.authenticationManager = new ProviderManager(provider);
        this.rateLimiter = rateLimiter;
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
                                                     HttpServletResponse response) {
        String ip = request.getRemoteAddr();
        if (rateLimiter.isBlocked(ip)) {
            return ResponseEntity.status(429).body(Map.of("error", "Too many failed attempts. Try again in 15 minutes."));
        }
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(body.username(), body.password()));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
            if (request.getSession(false) != null) {
                request.changeSessionId();
            }
            contextRepository.saveContext(context, request, response);
            rateLimiter.reset(ip);
            return ResponseEntity.ok(Map.of("username", auth.getName(), "authenticated", true));
        } catch (AuthenticationException e) {
            rateLimiter.recordFailure(ip);
            return ResponseEntity.status(401).body(Map.of("error", "Wrong username or password"));
        }
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request, HttpServletResponse response,
                                      Authentication authentication) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return Map.of("authenticated", false);
    }

    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        boolean ok = authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getName());
        if (!ok) {
            return Map.of("authenticated", false, "demoMode", properties.demoMode());
        }
        AppUser u = appUsers.findByUsername(authentication.getName()).orElseThrow();
        return Map.of("authenticated", true, "demoMode", properties.demoMode(), "id", u.getId(),
                "username", u.getUsername(), "fullName", u.getFullName(), "role", u.getRole(),
                "office", u.getOffice() == null ? "All offices" : u.getOffice().getName(),
                "officeId", u.getOffice() == null ? -1 : u.getOffice().getId());
    }

    @GetMapping("/demo-users")
    public List<Map<String, Object>> demoUsers() {
        if (!properties.demoMode()) {
            return List.of();
        }
        return appUsers.findAllByOrderByRoleAscFullNameAsc().stream()
                .sorted(Comparator.comparing((AppUser u) -> u.getRole().ordinal()).thenComparing(AppUser::getFullName))
                .map(u -> Map.<String, Object>of(
                "username", u.getUsername(), "password", properties.demoPassword(), "fullName", u.getFullName(),
                "role", u.getRole(), "office", u.getOffice() == null ? "All offices" : u.getOffice().getName())).toList();
    }
}
