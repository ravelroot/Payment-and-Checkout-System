package dev.velo.user;

import dev.velo.auth.PasswordPolicy;
import dev.velo.auth.SessionService;
import dev.velo.common.ApiException;
import dev.velo.common.PageResponse;
import dev.velo.common.Texts;
import dev.velo.common.Times;
import dev.velo.order.OrderRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;
    private final AddressRepository addresses;
    private final OrderRepository orders;
    private final PasswordEncoder encoder;
    private final SessionService sessions;
    private final EntityManager em;

    public UserService(UserRepository users, AddressRepository addresses, OrderRepository orders, PasswordEncoder encoder,
                       SessionService sessions, EntityManager em) {
        this.users = users;
        this.addresses = addresses;
        this.orders = orders;
        this.encoder = encoder;
        this.sessions = sessions;
        this.em = em;
    }

    public User get(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("Usuário não encontrado."));
    }

    public static AccountDtos.Profile profile(User u) {
        return new AccountDtos.Profile(u.getId(), u.getFullName(), u.getEmail(), u.getRole(), u.getPhone(), u.getDocument(),
                Times.instant(u.getCreatedAt()), Times.instant(u.getLastLoginAt()));
    }

    @Transactional
    public User updateProfile(Long id, AccountDtos.ProfileInput in) {
        User u = get(id);
        u.setFullName(Texts.clean(in.fullName()));
        u.setPhone(Texts.clean(in.phone()));
        u.setDocument(Texts.clean(in.document()));
        return users.save(u);
    }

    @Transactional
    public void changePassword(Long id, AccountDtos.PasswordInput in) {
        User u = get(id);
        if (!encoder.matches(in.currentPassword(), u.getPasswordHash())) {
            throw ApiException.badRequest("WRONG_PASSWORD", "A senha atual está incorreta.");
        }
        if (encoder.matches(in.newPassword(), u.getPasswordHash())) {
            throw ApiException.badRequest("SAME_PASSWORD", "A nova senha precisa ser diferente da atual.");
        }
        PasswordPolicy.check(in.newPassword(), u.getEmail(), u.getFullName());
        u.setPasswordHash(encoder.encode(in.newPassword()));
        users.save(u);
    }

    @Transactional(readOnly = true)
    public List<AccountDtos.AddressView> addresses(Long userId) {
        return addresses.findByUserIdOrderByDefaultAddressDescIdAsc(userId).stream().map(UserService::view).toList();
    }

    @Transactional
    public AccountDtos.AddressView saveAddress(Long userId, Long id, AccountDtos.AddressInput in) {
        Address a = id == null ? new Address() : addresses.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Endereço não encontrado."));
        if (id == null && addresses.countByUserId(userId) >= 10) {
            throw ApiException.badRequest("LIMIT", "Você pode guardar até 10 endereços.");
        }
        a.setUserId(userId);
        a.setLabel(Texts.clean(in.label()) == null ? "Endereço" : Texts.clean(in.label()));
        a.setRecipient(Texts.clean(in.recipient()));
        a.setZipCode(dev.velo.order.OrderService.zip(in.zipCode()));
        a.setStreet(Texts.clean(in.street()));
        a.setNumber(Texts.clean(in.number()));
        a.setComplement(Texts.clean(in.complement()));
        a.setDistrict(Texts.clean(in.district()));
        a.setCity(Texts.clean(in.city()));
        a.setState(in.state().trim().toUpperCase(Locale.ROOT));
        boolean first = addresses.countByUserId(userId) == 0;
        boolean makeDefault = first || Boolean.TRUE.equals(in.isDefault());
        if (makeDefault) {
            for (Address other : addresses.findByUserIdOrderByDefaultAddressDescIdAsc(userId)) {
                if (other.isDefaultAddress() && !other.getId().equals(a.getId())) {
                    other.setDefaultAddress(false);
                    addresses.save(other);
                }
            }
        }
        a.setDefaultAddress(makeDefault || (a.getId() != null && a.isDefaultAddress() && !Boolean.FALSE.equals(in.isDefault())));
        return view(addresses.save(a));
    }

    @Transactional
    public void deleteAddress(Long userId, Long id) {
        Address a = addresses.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Endereço não encontrado."));
        boolean wasDefault = a.isDefaultAddress();
        addresses.delete(a);
        if (wasDefault) {
            addresses.findByUserIdOrderByDefaultAddressDescIdAsc(userId).stream().findFirst().ifPresent(next -> {
                next.setDefaultAddress(true);
                addresses.save(next);
            });
        }
    }

    static AccountDtos.AddressView view(Address a) {
        return new AccountDtos.AddressView(a.getId(), a.getLabel(), a.getRecipient(), a.getZipCode(), a.getStreet(), a.getNumber(),
                a.getComplement(), a.getDistrict(), a.getCity(), a.getState(), a.isDefaultAddress());
    }

    @Transactional(readOnly = true)
    public PageResponse<AccountDtos.AdminUserView> search(String q, UserRole role, Boolean active, int page, int size) {
        int sz = Math.max(1, Math.min(size, 100));
        int pg = Math.max(0, page);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<User> cq = cb.createQuery(User.class);
        Root<User> root = cq.from(User.class);
        cq.select(root).where(predicates(cb, root, q, role, active)).orderBy(cb.asc(root.get("fullName")), cb.asc(root.get("id")));
        List<User> list = em.createQuery(cq).setFirstResult(pg * sz).setMaxResults(sz).getResultList();
        CriteriaQuery<Long> count = cb.createQuery(Long.class);
        Root<User> cr = count.from(User.class);
        count.select(cb.count(cr)).where(predicates(cb, cr, q, role, active));
        long total = em.createQuery(count).getSingleResult();
        return PageResponse.of(list.stream().map(this::adminView).toList(), pg, sz, total);
    }

    private Predicate[] predicates(CriteriaBuilder cb, Root<User> root, String q, UserRole role, Boolean active) {
        List<Predicate> p = new ArrayList<>();
        String like = Texts.like(q);
        if (like != null) {
            p.add(cb.or(cb.like(cb.lower(root.get("fullName")), like, '\\'), cb.like(cb.lower(root.get("email")), like, '\\')));
        }
        if (role != null) {
            p.add(cb.equal(root.get("role"), role));
        }
        if (active != null) {
            p.add(cb.equal(root.get("active"), active));
        }
        return p.toArray(Predicate[]::new);
    }

    public AccountDtos.AdminUserView adminView(User u) {
        return new AccountDtos.AdminUserView(u.getId(), u.getFullName(), u.getEmail(), u.getRole(), u.isActive(), u.getPhone(),
                u.getDocument(), Times.instant(u.getCreatedAt()), Times.instant(u.getLastLoginAt()), orders.countByUserId(u.getId()));
    }

    @Transactional
    public User adminSave(Long id, AccountDtos.AdminUserInput in, Long actorId) {
        boolean creating = id == null;
        User u = creating ? new User() : get(id);
        String email = Texts.email(in.email());
        if (!email.equals(u.getEmail()) && users.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "Já existe uma conta com este e-mail.");
        }
        boolean active = in.active() == null || in.active();
        if (!creating && u.getId().equals(actorId) && (!active || in.role() != UserRole.ADMIN)) {
            throw ApiException.badRequest("SELF", "Você não pode desativar nem rebaixar a própria conta.");
        }
        if (!creating && u.getRole() == UserRole.ADMIN && u.isActive() && (in.role() != UserRole.ADMIN || !active)
                && users.countByRoleAndActiveTrue(UserRole.ADMIN) <= 1) {
            throw ApiException.badRequest("LAST_ADMIN", "É preciso manter pelo menos um administrador ativo.");
        }
        u.setFullName(Texts.clean(in.fullName()));
        u.setEmail(email);
        u.setRole(in.role());
        u.setActive(active);
        u.setPhone(Texts.clean(in.phone()));
        u.setDocument(Texts.clean(in.document()));
        if (creating) {
            if (in.password() == null || in.password().isBlank()) {
                throw ApiException.badRequest("PASSWORD", "Defina uma senha inicial.");
            }
            PasswordPolicy.check(in.password(), email, in.fullName());
            u.setPasswordHash(encoder.encode(in.password()));
        }
        User saved = users.save(u);
        if (!active) {
            sessions.revokeAll(saved.getId());
        }
        return saved;
    }

    @Transactional
    public void resetPassword(Long id, String password) {
        User u = get(id);
        PasswordPolicy.check(password, u.getEmail(), u.getFullName());
        u.setPasswordHash(encoder.encode(password));
        u.setFailedLogins(0);
        u.setLockedUntil(null);
        users.save(u);
        sessions.revokeAll(id);
    }
}
