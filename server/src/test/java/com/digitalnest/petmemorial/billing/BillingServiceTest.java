package com.digitalnest.petmemorial.billing;

import com.digitalnest.petmemorial.shared.error.ApiException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BillingServiceTest {

    @Test
    void doesNotCreateCheckoutOrdersWhenThePaymentProviderIsDisabled() {
        BillingService service = new BillingService(null, new PlanCatalog(), false);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.createOrder(UUID.randomUUID(), "GUARDIAN", "checkout-guardian-0001"));

        assertEquals("PAYMENT_NOT_AVAILABLE", exception.code());
    }
}
