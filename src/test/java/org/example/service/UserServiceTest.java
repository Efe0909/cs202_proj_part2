package org.example.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private org.example.dao.UserDAO userDAO;

    @InjectMocks
    private UserService userService;

    @Test
    void register_newUsername_insertsAndReturnsHashedUser() {
        when(userDAO.usernameExists("alice")).thenReturn(false);
        when(userDAO.insert(any(org.example.model.User.class), anyString())).thenReturn(42);
        when(userDAO.addAddress(eq(42), anyString(), anyString())).thenReturn(99);

        org.example.model.User result = userService.register("alice", "plaintext", "alice@example.com",
                "Alice Smith", "CUSTOMER", "Istanbul", "Kadikoy");

        ArgumentCaptor<org.example.model.User> userCaptor = ArgumentCaptor.forClass(org.example.model.User.class);
        ArgumentCaptor<String> saltCaptor = ArgumentCaptor.forClass(String.class);
        verify(userDAO, times(1)).insert(userCaptor.capture(), saltCaptor.capture());

        assertEquals("alice", result.getUsername());
        assertNotEquals("plaintext", result.getPassword(),
                "Stored password should be hashed, not plaintext");
        assertEquals(42, result.getUserId());
    }

    @Test
    void register_existingUsername_throwsIllegalArgumentException() {
        when(userDAO.usernameExists("bob")).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> userService.register("bob", "pass", "bob@example.com",
                        "Bob Jones", "CUSTOMER", "Ankara", "Cankaya"));

        verify(userDAO, never()).insert(any(), anyString());
    }

    @Test
    void login_correctPassword_returnsUser() {
        String salt = org.example.util.PasswordUtil.generateSalt();
        String hashedPassword = org.example.util.PasswordUtil.hash("secret123", salt);

        org.example.model.User storedUser = new org.example.model.User(1, "carol", hashedPassword, "carol@example.com",
                "Carol White", "CUSTOMER");

        when(userDAO.findSaltByUsername("carol")).thenReturn(Optional.of(salt));
        when(userDAO.findByUsername("carol")).thenReturn(Optional.of(storedUser));

        Optional<org.example.model.User> result = userService.login("carol", "secret123");

        assertTrue(result.isPresent(), "Login with correct password should succeed");
        assertEquals("carol", result.get().getUsername());
    }

    @Test
    void login_wrongPassword_returnsEmpty() {
        String salt = org.example.util.PasswordUtil.generateSalt();
        String hashedPassword = org.example.util.PasswordUtil.hash("secret123", salt);

        org.example.model.User storedUser = new org.example.model.User(2, "dave", hashedPassword, "dave@example.com",
                "Dave Brown", "CUSTOMER");

        when(userDAO.findSaltByUsername("dave")).thenReturn(Optional.of(salt));
        when(userDAO.findByUsername("dave")).thenReturn(Optional.of(storedUser));

        Optional<org.example.model.User> result = userService.login("dave", "wrongpassword");

        assertFalse(result.isPresent(), "Login with wrong password should return empty");
    }

    @Test
    void login_unknownUsername_returnsEmpty() {
        when(userDAO.findSaltByUsername("ghost")).thenReturn(Optional.empty());

        Optional<org.example.model.User> result = userService.login("ghost", "anypass");

        assertFalse(result.isPresent(), "Login for unknown user should return empty");
        verify(userDAO, never()).findByUsername(anyString());
    }
}
