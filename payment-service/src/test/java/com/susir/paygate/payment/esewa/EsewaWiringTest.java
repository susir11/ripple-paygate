package com.susir.paygate.payment.esewa;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring must be able to create EsewaService on its own. Spring Boot 4 does not
 * register a RestClient.Builder bean with just the webmvc starter, so EsewaService
 * must not depend on one being present (this crashed payment-service at startup).
 */
class EsewaWiringTest {

    @Test
    void esewaService_startsWithoutRestClientBuilderBean() {
        new ApplicationContextRunner()
                .withBean(EsewaProperties.class, () -> EsewaServiceTest.SANDBOX)
                .withBean(EsewaService.class)
                .run(ctx -> assertThat(ctx).hasNotFailed().hasSingleBean(EsewaService.class));
    }
}
