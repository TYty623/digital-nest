package com.digitalnest.petmemorial.account;

import com.digitalnest.petmemorial.shared.error.ApiException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AccountService accountService;

    @Test
    void registerNormalizesEmailAndCreatesAccount() {
        when(accountRepository.findByEmail("friend@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("a-long-enough-password")).thenReturn("encoded-password");
        when(accountRepository.create(any(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> new UserAccount(
                        invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3), "ACTIVE"));

        CurrentUser result = accountService.register(" Friend@Example.com ", "a-long-enough-password", "  豆包的家人  ");

        assertThat(result.email()).isEqualTo("friend@example.com");
        assertThat(result.displayName()).isEqualTo("豆包的家人");
        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        verify(accountRepository).findByEmail(emailCaptor.capture());
        assertThat(emailCaptor.getValue()).isEqualTo("friend@example.com");
    }

    @Test
    void loginRejectsWrongPassword() {
        UserAccount account = new UserAccount(java.util.UUID.randomUUID(), "friend@example.com", "hash", "朋友", "ACTIVE");
        when(accountRepository.findByEmail("friend@example.com")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("wrong-password", "hash")).thenReturn(false);

        assertThatThrownBy(() -> accountService.login("friend@example.com", "wrong-password"))
                .isInstanceOf(ApiException.class)
                .hasMessage("邮箱或密码不正确。");
    }
}
