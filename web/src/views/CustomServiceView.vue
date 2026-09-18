<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import {
  confirmCustomServiceDelivery,
  createCustomServiceRequest,
  listCustomServiceRequests,
  type CustomServiceRequest,
} from '@/api/memorials'
import { customServiceStatusLabels } from '@/config/site'
import { useAuth } from '@/stores/auth'

const auth = useAuth()
const router = useRouter()
const requests = ref<CustomServiceRequest[]>([])
const loading = ref(true)
const submitting = ref(false)
const confirmingId = ref<string | null>(null)
const error = ref('')
const success = ref('')
const form = reactive({ contactDetails: '', requestDetails: '' })

function formatDate(value: string | null) {
  if (!value) return '待确认'
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium' }).format(new Date(value))
}

async function load() {
  loading.value = true
  try {
    requests.value = await listCustomServiceRequests()
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '服务申请暂时无法读取。'
  } finally {
    loading.value = false
  }
}

async function submit() {
  error.value = ''
  success.value = ''
  if (form.contactDetails.trim().length < 3 || form.requestDetails.trim().length < 10) {
    error.value = '请留下联系方式，并用至少 10 个字说明希望我们协助的内容。'
    return
  }
  submitting.value = true
  try {
    const request = await createCustomServiceRequest(form.contactDetails, form.requestDetails)
    requests.value.unshift(request)
    form.contactDetails = ''
    form.requestDetails = ''
    success.value = '申请已提交。运营人员会先确认材料范围和交付安排；此步骤不会产生站内扣款。'
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '申请暂时无法提交。'
  } finally {
    submitting.value = false
  }
}

async function confirmDelivery(request: CustomServiceRequest) {
  if (!window.confirm('确认后，这项定制服务会标记为完成。是否继续？')) return
  confirmingId.value = request.id
  error.value = ''
  try {
    const updated = await confirmCustomServiceDelivery(request.id)
    requests.value = requests.value.map((item) => (item.id === updated.id ? updated : item))
    success.value = '已确认交付完成。谢谢你把这段回忆交给我们一起安放。'
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '暂时无法确认交付。'
  } finally {
    confirmingId.value = null
  }
}

onMounted(async () => {
  try {
    await auth.hydrate()
  } catch {
    error.value = '暂时无法连接服务，请刷新后重试。'
    loading.value = false
    return
  }
  if (!auth.state.user) {
    await router.replace({ name: 'login', query: { next: '/custom-service' } })
    return
  }
  await load()
})
</script>

<template>
  <section class="section service-page">
    <header class="page-intro service-intro">
      <p class="eyebrow">专属定制</p>
      <h1>如果你暂时没有力气整理，<br />我们可以陪你一起完成。</h1>
      <p>
        ¥699
        起的定制服务包含材料整理、故事润色与页面代建。先提交申请，由运营人员确认范围与交付安排；正式支付开放前，站内不会向你扣款。
      </p>
      <RouterLink class="text-link" to="/pricing">← 返回套餐说明</RouterLink>
    </header>

    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <p v-if="success" class="form-success" role="status">{{ success }}</p>

    <section class="service-boundary" aria-label="定制服务范围">
      <article><strong>7 个工作日</strong><span>材料齐全并确认范围后开始计算</span></article>
      <article><strong>2 轮修改</strong><span>第三轮起会明确标识为额外范围</span></article>
      <article><strong>先确认再开始</strong><span>未确认范围或支付前不启动制作</span></article>
    </section>

    <section class="service-section request-section">
      <div>
        <p class="eyebrow">发起申请</p>
        <h2>告诉我们，你希望留下些什么。</h2>
        <p>
          请不要在这里发送身份证件、支付密码等敏感信息。照片材料将在确认范围后通过受控上传方式收集。
        </p>
      </div>
      <form class="service-form" @submit.prevent="submit">
        <label
          ><span>联系方式</span
          ><input
            v-model="form.contactDetails"
            maxlength="280"
            autocomplete="email"
            placeholder="邮箱、微信号或方便联系的方式"
            required
        /></label>
        <label
          ><span>希望协助的内容</span
          ><textarea
            v-model="form.requestDetails"
            maxlength="3000"
            rows="7"
            placeholder="例如：有 40 张照片和一些聊天记录，希望整理成一页给家人留念；想突出 TA 的性格与陪伴日常。"
            required
          />
        </label>
        <button class="button" type="submit" :disabled="submitting">
          {{ submitting ? '正在提交…' : '提交定制申请' }}
        </button>
      </form>
    </section>

    <section class="service-section request-history">
      <div class="section-heading">
        <div>
          <p class="eyebrow">我的申请</p>
          <h2>每一步都看得见。</h2>
        </div>
      </div>
      <p v-if="loading" class="history-empty">正在读取服务进度…</p>
      <p v-else-if="requests.length === 0" class="history-empty">
        还没有提交申请。你可以先从上方填写希望我们协助的内容。
      </p>
      <div v-else class="request-list">
        <article v-for="request in requests" :key="request.id" class="request-card">
          <div class="request-head">
            <span class="service-status" :class="request.status.toLowerCase()">{{
              customServiceStatusLabels[request.status]
            }}</span
            ><time>{{ formatDate(request.updatedAt) }} 更新</time>
          </div>
          <p class="request-copy">{{ request.requestDetails }}</p>
          <dl class="request-meta">
            <div>
              <dt>材料</dt>
              <dd>{{ request.materialsReady ? '已确认齐全' : '等待确认或补充' }}</dd>
            </div>
            <div>
              <dt>截止日期</dt>
              <dd>{{ formatDate(request.dueDate) }}</dd>
            </div>
            <div>
              <dt>修改轮次</dt>
              <dd :class="{ 'out-of-scope': request.revisionCount > 2 }">
                {{ request.revisionCount }} / 2
                <span v-if="request.revisionCount > 2">· 已超出约定范围</span>
              </dd>
            </div>
          </dl>
          <p v-if="request.customerMessage" class="customer-message">
            <strong>进度说明：</strong>{{ request.customerMessage }}
          </p>
          <button
            v-if="request.status === 'DELIVERED'"
            class="button button-small"
            type="button"
            :disabled="confirmingId === request.id"
            @click="confirmDelivery(request)"
          >
            {{ confirmingId === request.id ? '正在确认…' : '确认已交付' }}
          </button>
        </article>
      </div>
    </section>
  </section>
</template>

<style scoped>
.service-page {
  padding-top: 46px;
}
.service-intro {
  max-width: 800px;
}
.service-intro h1 {
  max-width: 770px;
}
.service-intro p {
  max-width: 710px;
}
.form-error,
.form-success {
  margin-top: 24px;
}
.service-boundary {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-top: 48px;
}
.service-boundary article {
  display: grid;
  gap: 8px;
  min-height: 145px;
  padding: 24px;
  border: 1px solid var(--border);
  border-radius: 18px;
  background: var(--surface);
}
.service-boundary strong {
  color: var(--primary);
  font-family: Georgia, serif;
  font-size: 25px;
}
.service-boundary span,
.service-section > div > p {
  color: var(--muted);
  font-size: 14px;
  line-height: 1.7;
}
.service-section {
  display: grid;
  grid-template-columns: 0.78fr 1.22fr;
  gap: 52px;
  margin-top: 72px;
  padding-top: 32px;
  border-top: 1px solid var(--border);
}
.service-section h2 {
  margin-bottom: 12px;
  font-size: 37px;
}
.service-form {
  display: grid;
  gap: 18px;
}
.service-form label {
  display: grid;
  gap: 8px;
}
.service-form label span {
  font-size: 14px;
  font-weight: 700;
}
.service-form textarea {
  resize: vertical;
}
.service-form .button {
  justify-self: start;
}
.request-history {
  grid-template-columns: 0.55fr 1.45fr;
}
.history-empty {
  margin: 0;
  color: var(--muted);
}
.request-list {
  display: grid;
  gap: 14px;
}
.request-card {
  padding: 23px;
  border: 1px solid var(--border);
  border-radius: 18px;
  background: var(--surface);
}
.request-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.request-head time {
  color: var(--muted);
  font-size: 12px;
}
.service-status {
  padding: 5px 9px;
  border-radius: 999px;
  background: #f5e6d8;
  color: var(--primary);
  font-size: 12px;
  font-weight: 700;
}
.service-status.in_progress,
.service-status.delivered {
  background: #dfeee5;
  color: #397151;
}
.service-status.revision,
.service-status.out_of_scope {
  background: #f8e3d5;
  color: #994b2d;
}
.service-status.completed {
  background: #eceae5;
  color: #5d625e;
}
.request-copy {
  margin: 18px 0;
  white-space: pre-wrap;
  line-height: 1.8;
}
.request-meta {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin: 0;
}
.request-meta div {
  padding: 12px;
  border-radius: 12px;
  background: #fbf7f1;
}
.request-meta dt {
  color: var(--muted);
  font-size: 12px;
}
.request-meta dd {
  margin: 4px 0 0;
  font-size: 13px;
}
.out-of-scope {
  color: #a5402c;
  font-weight: 700;
}
.customer-message {
  margin: 16px 0;
  padding: 12px 14px;
  border-left: 3px solid #dfb396;
  background: #fcf6f0;
  line-height: 1.7;
}
.customer-message strong {
  color: var(--primary);
}
@media (max-width: 800px) {
  .service-boundary {
    grid-template-columns: 1fr;
  }
  .service-section,
  .request-history {
    grid-template-columns: 1fr;
    gap: 26px;
  }
  .service-section h2 {
    font-size: 31px;
  }
}
@media (max-width: 560px) {
  .request-meta {
    grid-template-columns: 1fr;
  }
  .request-head {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }
}
</style>
