package com.vault.authservice.repository;


import com.vault.authservice.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
@DataJpaTest(properties = {
        "spring.test.database.replace=none"
})
public class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldFindUserByEmail() {
        User user = new User(
                "Test User",
                "test@example.com",
                "hashed-password"
        );

        userRepository.save(user);

        Optional<User> result = userRepository.findByEmail("test@example.com");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail())
                .isEqualTo("test@example.com");
    }

    @Test
    void shouldReturnEmptyWhenEmailDoesNotExist() {

        Optional<User> result =
                userRepository.findByEmail("unknown@example.com");

        assertThat(result).isEmpty();
    }
}
