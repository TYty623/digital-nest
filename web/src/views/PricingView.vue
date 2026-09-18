<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { listBillingPlans, type BillingPlan } from '@/api/memorials'
import { pricingPageContent, pricingPlanFeatures, pricingPlans } from '@/config/pricing'

const remotePlans = ref<BillingPlan[]>([])
const loading = ref(true)
const loadError = ref('')
const displayPlans = computed(() => pricingPlans.flatMap((presentation) => {
  const plan = remotePlans.value.find((candidate) => candidate.code === presentation.code)
  return plan ? [{ ...presentation, plan }] : []
}))

onMounted(async () => {
  try {
    remotePlans.value = await listBillingPlans()
  } catch {
    loadError.value = '套餐信息暂时无法读取。为避免展示与实际结算不一致的金额，请稍后重试。'
  } finally {
    loading.value = false
  }
})

function checkoutAvailable(plan: BillingPlan) {
  return plan.checkoutAvailable
}

function formatPrice(plan: BillingPlan) {
  return new Intl.NumberFormat('zh-CN', { style: 'currency', currency: plan.currency, minimumFractionDigits: 0 })
    .format(plan.amountCents / 100)
}
</script>

<template>
  <section class="section page-intro centered"><p class="eyebrow">{{ pricingPageContent.eyebrow }}</p><h1>{{ pricingPageContent.titleFirstLine }}<br />{{ pricingPageContent.titleSecondLine }}</h1><p>{{ pricingPageContent.description }}</p></section>
  <section class="section pricing-grid" aria-live="polite">
    <p v-if="loading" class="pricing-status">正在读取最新套餐…</p>
    <div v-else-if="loadError" class="pricing-status pricing-error" role="alert">{{ loadError }}</div>
    <template v-else>
      <article v-for="item in displayPlans" :key="item.code" class="pricing-card" :class="{ featured: item.featured }">
        <p class="plan-name">{{ item.name }}</p>
        <h2>{{ formatPrice(item.plan) }}<small v-if="item.plan.amountCents > 0">/ 次</small></h2>
        <p class="plan-note">{{ item.note }}</p>
        <ul><li v-for="feature in pricingPlanFeatures(item.plan)" :key="feature">{{ feature }}</li></ul>
        <RouterLink class="button" :class="{ 'button-quiet': !item.featured }" :to="item.plan.amountCents === 0 ? '/create' : (checkoutAvailable(item.plan) ? '/account/orders' : '/help')">{{ item.plan.amountCents === 0 ? '免费开始创建' : (checkoutAvailable(item.plan) ? '前往开通' : '了解开放进度') }}</RouterLink>
      </article>
    </template>
  </section>
  <section class="section service-callout"><div><p class="eyebrow">{{ pricingPageContent.customService.eyebrow }}</p><h2>{{ pricingPageContent.customService.title }}</h2><p>{{ pricingPageContent.customService.description }}</p><RouterLink class="text-link" to="/refund">{{ pricingPageContent.customService.policyLabel }}</RouterLink></div><RouterLink class="button button-quiet" to="/custom-service">{{ pricingPageContent.customService.actionLabel }}</RouterLink></section>
</template>
