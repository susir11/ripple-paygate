package com.susir.paygate.payment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Starts the whole app. application.yml points at Docker's "postgres-db" host and
// Eureka, which don't exist outside Docker — so for this test we swap in an
// in-memory H2 database and turn off Eureka registration.
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:paygate;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
		"eureka.client.enabled=false"
})
class PaymentServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
