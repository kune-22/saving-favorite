package jp.co.savingfavorite;

import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(UserRepository repository) {
        return email -> {
            User user = repository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                    .orElseThrow(() -> new UsernameNotFoundException("Account not found"));
            return org.springframework.security.core.userdetails.User
                    .withUsername(user.getEmail()).password(user.getPasswordHash())
                    .roles("USER").build();
        };
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/register", "/login", "/css/**", "/js/**", "/fonts/**", "/error", "/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors.defaultAuthenticationEntryPointFor(
                        (request, response, exception) -> response.sendError(401),
                        request -> request.getServletPath().startsWith("/api/")))
                .formLogin(login -> login.loginPage("/register?mode=login")
                        .loginProcessingUrl("/login").usernameParameter("email")
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/register?mode=login&error=true").permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/register?mode=login&logout=true"))
                .build();
    }
}
