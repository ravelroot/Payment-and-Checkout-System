package dev.velo.user;

import dev.velo.auth.CurrentUser;
import dev.velo.auth.SessionService;
import dev.velo.common.PageResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserService users;
    private final SessionService sessions;

    public AdminUserController(UserService users, SessionService sessions) {
        this.users = users;
        this.sessions = sessions;
    }

    @GetMapping
    public PageResponse<AccountDtos.AdminUserView> list(@RequestParam(required = false) String q,
                                                        @RequestParam(required = false) UserRole role,
                                                        @RequestParam(required = false) Boolean active,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return users.search(q, role, active, page, size);
    }

    @GetMapping("/{id}")
    public AccountDtos.AdminUserView get(@PathVariable Long id) {
        return users.adminView(users.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountDtos.AdminUserView create(@Valid @RequestBody AccountDtos.AdminUserInput in) {
        return users.adminView(users.adminSave(null, in, CurrentUser.id()));
    }

    @PutMapping("/{id}")
    public AccountDtos.AdminUserView update(@PathVariable Long id, @Valid @RequestBody AccountDtos.AdminUserInput in) {
        return users.adminView(users.adminSave(id, in, CurrentUser.id()));
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<Void> password(@PathVariable Long id, @Valid @RequestBody AccountDtos.ResetPassword in) {
        users.resetPassword(id, in.password());
        sessions.revokeAll(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/sessions/revoke")
    public Map<String, Integer> revoke(@PathVariable Long id) {
        return Map.of("revoked", sessions.revokeAll(id));
    }
}
