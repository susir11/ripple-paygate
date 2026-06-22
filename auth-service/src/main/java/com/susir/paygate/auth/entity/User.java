package com.susir.paygate.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// This tells Spring this class maps to a database table.
// Just like Payment in payment-service maps to the "payments" table,
// this maps to a "users" table that Hibernate will create automatically.
@Entity
@Table(name = "users")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The username must be unique — no two users can have the same one.
    // This is like an account number in remittance — unique per participant.
    @Column(unique = true, nullable = false)
    private String username;

    // We NEVER store plain text passwords — only the encrypted hash.
    // Spring Security's BCrypt encoder handles this for us.
    @Column(nullable = false)
    private String password;

    // The role determines what the user is allowed to do.
    // For now we just have "ROLE_USER" but this could expand to
    // "ROLE_ADMIN" later — same idea as access levels in a banking system.
    @Column(nullable = false)
    private String role;
}