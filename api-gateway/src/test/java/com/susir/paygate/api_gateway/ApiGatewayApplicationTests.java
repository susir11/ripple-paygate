package com.susir.paygate.api_gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.route.RouteLocator;

import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.boot.test.context.SpringBootTest;

// Starts the whole gateway (without Eureka) and checks the routes from application.yml
// were actually loaded — a typo in the config keys would leave the gateway with no routes.
@SpringBootTest(properties = {
		"eureka.client.enabled=false",
		"jwt.secret=test-secret-that-is-at-least-32-characters-long"
})
class ApiGatewayApplicationTests {

	@Autowired
	RouteLocator routes;

	@Test
	void contextLoadsWithAllRoutes() {
		assertThat(routes.getRoutes().map(r -> r.getId()).collectList().block())
				.containsExactly("auth-service-route", "esewa-callback-route", "payment-service-route");
	}

}
