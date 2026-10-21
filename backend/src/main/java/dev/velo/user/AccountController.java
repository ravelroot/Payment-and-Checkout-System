package dev.velo.user;

import dev.velo.auth.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final UserService users;

    public AccountController(UserService users) {
        this.users = users;
    }

    @GetMapping
    public AccountDtos.Profile profile() {
        return UserService.profile(users.get(CurrentUser.id()));
    }

    @PutMapping
    public AccountDtos.Profile update(@Valid @RequestBody AccountDtos.ProfileInput in) {
        return UserService.profile(users.updateProfile(CurrentUser.id(), in));
    }

    @PutMapping("/password")
    public ResponseEntity<Void> password(@Valid @RequestBody AccountDtos.PasswordInput in) {
        users.changePassword(CurrentUser.id(), in);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/addresses")
    public List<AccountDtos.AddressView> addresses() {
        return users.addresses(CurrentUser.id());
    }

    @PostMapping("/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountDtos.AddressView createAddress(@Valid @RequestBody AccountDtos.AddressInput in) {
        return users.saveAddress(CurrentUser.id(), null, in);
    }

    @PutMapping("/addresses/{id}")
    public AccountDtos.AddressView updateAddress(@PathVariable Long id, @Valid @RequestBody AccountDtos.AddressInput in) {
        return users.saveAddress(CurrentUser.id(), id, in);
    }

    @DeleteMapping("/addresses/{id}")
    public ResponseEntity<Void> deleteAddress(@PathVariable Long id) {
        users.deleteAddress(CurrentUser.id(), id);
        return ResponseEntity.noContent().build();
    }
}
