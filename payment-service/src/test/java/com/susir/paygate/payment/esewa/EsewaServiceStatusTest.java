package com.susir.paygate.payment.esewa;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EsewaServiceStatusTest {

    static final String STATUS_URL =
            "https://rc.esewa.com.np/api/epay/transaction/status/?product_code=EPAYTEST&total_amount=100.0&transaction_uuid=abc-123";

    private MockRestServiceServer server;
    private EsewaService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new EsewaService(EsewaServiceTest.SANDBOX, builder);
    }

    @Test
    void checkStatus_complete() {
        server.expect(requestTo(STATUS_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"product_code\":\"EPAYTEST\",\"transaction_uuid\":\"abc-123\",\"total_amount\":100.0,\"status\":\"COMPLETE\",\"ref_id\":\"R1\"}",
                        MediaType.APPLICATION_JSON));

        assertEquals("COMPLETE", service.checkStatus("abc-123", "100.0"));
        server.verify();
    }

    @Test
    void checkStatus_notFound() {
        server.expect(requestTo(STATUS_URL))
                .andRespond(withSuccess("{\"status\":\"NOT_FOUND\",\"ref_id\":null}", MediaType.APPLICATION_JSON));

        assertEquals("NOT_FOUND", service.checkStatus("abc-123", "100.0"));
    }

    @Test
    void checkStatus_serverError_returnsError() {
        server.expect(requestTo(STATUS_URL)).andRespond(withServerError());

        assertEquals("ERROR", service.checkStatus("abc-123", "100.0"));
    }

    @Test
    void checkStatus_serviceUnavailableBody_returnsError() {
        // eSewa's documented timeout body has no "status" field
        server.expect(requestTo(STATUS_URL))
                .andRespond(withSuccess("{\"code\":0,\"error_message\":\"Service is currently unavailable\"}",
                        MediaType.APPLICATION_JSON));

        assertEquals("ERROR", service.checkStatus("abc-123", "100.0"));
    }
}
