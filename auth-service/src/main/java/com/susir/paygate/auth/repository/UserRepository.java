package com.susir.paygate.auth.repository;

import com.susir.paygate.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

// Spring automatically implements all the database operations for us
// (save, findById, delete etc.) just by extending JpaRepository.
// We only need to define the custom queries we specifically need.
public interface UserRepository extends JpaRepository<User, Long> {

    // Find a user by their username — used during login to check
    // if the person trying to log in actually exists in our database.
    // Spring translates this method name into SQL automatically:
    // SELECT * FROM users WHERE username = ?
    Optional<User> findByUsername(String username);
}