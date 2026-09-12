package jp.co.savingfavorite;

import java.util.Locale;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public void register(UserRegistrationForm form) {
        String email = form.getEmail().trim().toLowerCase(Locale.ROOT);
        User user = new User(
                form.getName().trim(),
                email,
                passwordEncoder.encode(form.getPassword()));
        userRepository.save(user);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email.trim().toLowerCase(Locale.ROOT));
    }
}
