package com.susir.paygate.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Starts the whole app. application.yaml points at Docker's "postgres-db" host and
// Eureka, which don't exist outside Docker — so for this test we swap in an
// in-memory H2 database and turn off Eureka. Flyway runs the real migrations on H2
// and Hibernate validates the entities against them.
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:auth;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"eureka.client.enabled=false",
		"jwt.secret=test-secret-that-is-at-least-32-characters-long"
})
class AuthServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
