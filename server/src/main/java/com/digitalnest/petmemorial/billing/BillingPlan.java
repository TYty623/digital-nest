package com.digitalnest.petmemorial.billing;

public record BillingPlan(
        String code,
        String name,
        int amountCents,
        String currency,
        int photoLimit,
        int shortVideoLimit,
        int timelineLimit,
        int themeLimit,
        int hostedYears,
        boolean checkoutEligible
) {
    public boolean isFree() {
        return amountCents == 0;
    }
}
