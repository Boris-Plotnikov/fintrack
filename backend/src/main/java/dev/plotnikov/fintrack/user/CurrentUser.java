package dev.plotnikov.fintrack.user;

import dev.plotnikov.fintrack.common.ForbiddenException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {
    private final AppUserRepository users;

    public CurrentUser(AppUserRepository users) {
        this.users = users;
    }

    public AppUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new ForbiddenException("Not logged in");
        }
        return users.findByUsername(auth.getName()).orElseThrow(() -> new ForbiddenException("Unknown user"));
    }

    public static boolean isManagement(AppUser user) {
        return user.getRole() == Role.OWNER || user.getRole() == Role.MANAGER;
    }

    public static void requireManagement(AppUser user, String action) {
        if (!isManagement(user)) {
            throw new ForbiddenException("Only managers and the owner can " + action);
        }
    }
}
