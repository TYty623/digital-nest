package com.digitalnest.petmemorial.billing;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class PlanCatalog {

    private final Map<String, BillingPlan> plans = new LinkedHashMap<>();

    public PlanCatalog() {
        // 短视频只作为受限的相册媒体，不提供自动播放、背景音乐或转码承诺。
        register(new BillingPlan("FREE", "免费体验", 0, "CNY", 12, 0, 5, 1, 1, false));
        register(new BillingPlan("GUARDIAN", "温暖守护", 9_900, "CNY", 80, 3, 30, 3, 3, true));
        register(new BillingPlan("TREASURE", "时光珍藏", 29_900, "CNY", 300, 15, 100, 6, 10, true));
        register(new BillingPlan("CUSTOM", "专属定制", 69_900, "CNY", 300, 15, 100, 6, 10, false));
    }

    public Collection<BillingPlan> all() {
        return plans.values();
    }

    public Optional<BillingPlan> find(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(plans.get(code.trim().toUpperCase(Locale.ROOT)));
    }

    private void register(BillingPlan plan) {
        plans.put(plan.code(), plan);
    }
}
