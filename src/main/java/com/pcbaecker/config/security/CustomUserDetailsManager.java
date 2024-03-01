package com.pcbaecker.config.security;

import com.pcbaecker.domain.users.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsManager implements UserDetailsManager {

    private final UserRepository userRepository;

    public CustomUserDetailsManager(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void createUser(UserDetails user) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void updateUser(UserDetails user) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void deleteUser(String username) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void changePassword(String oldPassword, String newPassword) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean userExists(String username) {
        return this.userRepository.countByUsername(username) > 0;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return this.userRepository.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
