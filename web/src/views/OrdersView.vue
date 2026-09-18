<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  createBillingOrder,
  listAccountEntitlements,
  listBillingOrders,
  listBillingPlans,
  mockPayBillingOrder,
  type AccountEntitlement,
  type BillingOrder,
  type BillingPlan,
} from '@/api/memorials'
import { useAuth } from '@/stores/auth'

const router = useRouter()
const auth = useAuth()
const plans = ref<BillingPlan[]>([])
const orders = ref<BillingOrder[]>([])
const entitlements = ref<AccountEntitlement[]>([])
const loading = ref(true)
const payingPlan = ref<string | null>(null)
const errorMessage = ref('')
const successMessage = ref('')

const payablePlans = computed(() =>
  plans.value.filter((plan) => plan.amountCents > 0 && plan.checkoutAvailable),
)

function planSummary(
  plan: Pick<BillingPlan, 'photoLimit' | 'shortVideoLimit' | 'timelineLimit'>,
) {
  const parts = [
    `${plan.photoLimit} 张照片`,
    `${plan.timelineLimit} 个时间线节点`,
    '会员解锁全部六套外观作品集',
  ]
  if (plan.shortVideoLimit > 0) parts.splice(1, 0, `${plan.shortVideoLimit} 个短视频`)
  return parts
}

function formatMoney(amountCents: number, currency: string) {
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency,
    minimumFractionDigits: 0,
  }).format(amountCents / 100)
}

function formatDate(value: string | null) {
  if (!value) return '未填写'
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium' }).format(new Date(value))
}

async function load() {
  errorMessage.value = ''
  try {
    const [nextPlans, nextOrders, nextEntitlements] = await Promise.all([
      listBillingPlans(),
      listBillingOrders(),
      listAccountEntitlements(),
    ])
    plans.value = nextPlans
    orders.value = nextOrders
    entitlements.value = nextEntitlements
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '订单信息暂时无法读取。'
  } finally {
    loading.value = false
  }
}

async function startMockCheckout(plan: BillingPlan) {
  if (!window.confirm(`将以本地模拟支付开通「${plan.name}」。真实支付不会在此环境发起，是否继续？`))
    return
  payingPlan.value = plan.code
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const order = await createBillingOrder(plan.code, crypto.randomUUID())
    const result = await mockPayBillingOrder(order.id)
    successMessage.value = result.newlyPaid
      ? `已开通「${plan.name}」，权益已写入账户。`
      : '这笔订单已经完成开通。'
    await load()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '暂时无法完成开通。'
  } finally {
    payingPlan.value = null
  }
}

onMounted(async () => {
  try {
    await auth.hydrate()
  } catch {
    errorMessage.value = '暂时无法连接服务，请刷新后重试。'
    loading.value = false
    return
  }
  if (!auth.state.user) {
    await router.replace({ name: 'login', query: { next: '/account/orders' } })
    return
  }
  await load()
})
</script>

<template>
  <section class="section orders-page">
    <header class="page-intro account-intro">
      <p class="eyebrow">订单与权益</p>
      <h1>套餐记录，<br />清清楚楚留在这里。</h1>
      <p>
        订单金额和权益都由服务端保存。当前只在后端显式开启的本地环境提供模拟支付；正式支付上线前，线上环境不会出现开通按钮。
      </p>
      <RouterLink class="text-link" to="/account">← 返回我的小窝</RouterLink>
    </header>

    <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
    <p v-if="successMessage" class="form-success" role="status">{{ successMessage }}</p>

    <div v-if="loading" class="account-empty"><p>正在读取订单信息…</p></div>
    <template v-else>
      <section class="order-section">
        <div class="section-heading">
          <div>
            <p class="eyebrow">当前权益</p>
            <h2>已开通的托管</h2>
          </div>
        </div>
        <div v-if="entitlements.length" class="entitlement-grid">
          <article v-for="item in entitlements" :key="item.id" class="entitlement-card">
            <p class="plan-name">{{ item.status === 'ACTIVE' ? '生效中' : '已失效' }}</p>
            <h3>{{ item.planName }}</h3>
            <p>{{ planSummary(item).join(' · ') }}</p>
            <small>托管至 {{ formatDate(item.hostedUntil) }}</small>
            <small v-if="item.revokedAt">退款撤销于 {{ formatDate(item.revokedAt) }}</small>
          </article>
        </div>
        <div v-else class="order-empty">
          尚未开通付费套餐；免费体验仍可正常创建和发布基础纪念页。
        </div>
      </section>

      <section class="order-section">
        <div class="section-heading">
          <div>
            <p class="eyebrow">本地验证</p>
            <h2>模拟开通</h2>
          </div>
        </div>
        <p v-if="!payablePlans.length" class="order-empty">
          当前环境没有开启模拟支付。真实微信支付会在商户审核、验签与回调配置完成后另行开放。
        </p>
        <div v-else class="checkout-grid">
          <article v-for="plan in payablePlans" :key="plan.code" class="checkout-card">
            <p class="plan-name">{{ plan.name }}</p>
            <h3>{{ formatMoney(plan.amountCents, plan.currency) }}<small>/ 次</small></h3>
            <p>{{ planSummary(plan).join(' · ') }} · {{ plan.hostedYears }} 年托管</p>
            <button
              class="button"
              type="button"
              :disabled="payingPlan !== null"
              @click="startMockCheckout(plan)"
            >
              {{ payingPlan === plan.code ? '正在开通…' : '本地模拟开通' }}
            </button>
          </article>
        </div>
      </section>

      <section class="order-section">
        <div class="section-heading">
          <div>
            <p class="eyebrow">订单记录</p>
            <h2>每一笔都有凭据</h2>
          </div>
        </div>
        <div v-if="orders.length" class="order-table-wrap">
          <table class="order-table">
            <thead>
              <tr>
                <th>套餐</th>
                <th>金额</th>
                <th>状态</th>
                <th>创建时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="order in orders" :key="order.id">
                <td>{{ order.planName }}</td>
                <td>{{ formatMoney(order.amountCents, order.currency) }}</td>
                <td>
                  <span
                    class="order-status"
                    :class="`order-status-${order.status.toLowerCase()}`"
                    >{{
                      order.status === 'PAID'
                        ? '已支付'
                        : order.status === 'PENDING'
                          ? '待支付'
                          : order.status === 'REFUNDED'
                            ? '已退款'
                            : '已取消'
                    }}</span
                  ><small v-if="order.refundedAt">退款于 {{ formatDate(order.refundedAt) }}</small>
                </td>
                <td>{{ formatDate(order.createdAt) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else class="order-empty">还没有订单记录。</div>
      </section>
    </template>
  </section>
</template>
