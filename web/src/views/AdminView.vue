<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  getAdminOverview,
  archiveAdminMemorial,
  listAdminAuditLogs,
  listAdminCustomServiceRequests,
  listAdminMemorials,
  listAdminOrders,
  listAdminTributes,
  listAdminBurialRecords,
  moderateBurialRecord,
  moderateAdminTribute,
  refundAdminMockOrder,
  updateAdminCustomServiceRequest,
  type AdminAuditLog,
  type AdminCustomServiceRequest,
  type AdminMemorial,
  type AdminOrder,
  type AdminOverview,
  type AdminTribute,
  type BurialRecord,
  type CustomServiceStatus,
} from '@/api/memorials'

type ReviewStatus = 'PENDING' | 'REPORTED'
type StaffServiceStatus = Exclude<CustomServiceStatus, 'COMPLETED'>
type ServiceEdit = {
  status: StaffServiceStatus
  materialsReady: boolean
  assignee: string
  dueDate: string
  revisionCount: number
  customerMessage: string
  reason: string
}

const overview = ref<AdminOverview | null>(null)
const tributes = ref<AdminTribute[]>([])
const memorials = ref<AdminMemorial[]>([])
const orders = ref<AdminOrder[]>([])
const auditLogs = ref<AdminAuditLog[]>([])
const customServiceRequests = ref<AdminCustomServiceRequest[]>([])
const burialRecords = ref<BurialRecord[]>([])
const burialReasons = ref<Record<string, string>>({})
const activeStatus = ref<ReviewStatus>('PENDING')
const reasons = ref<Record<string, string>>({})
const memorialReasons = ref<Record<string, string>>({})
const orderRefundReasons = ref<Record<string, string>>({})
const serviceEdits = ref<Record<string, ServiceEdit>>({})
const loading = ref(true)
const actionId = ref<string | null>(null)
const error = ref('')
const actionError = ref('')

const refundableOrders = computed(() => orders.value.filter((order) => order.status === 'PAID'))

const statusLabels: Record<ReviewStatus, string> = {
  PENDING: '待审核',
  REPORTED: '被举报',
}
const reviewStatuses: ReviewStatus[] = ['PENDING', 'REPORTED']
const serviceStatuses: { value: StaffServiceStatus; label: string }[] = [
  { value: 'SUBMITTED', label: '待确认范围' },
  { value: 'MATERIALS_PENDING', label: '等待材料' },
  { value: 'IN_PROGRESS', label: '制作中' },
  { value: 'REVISION', label: '修改中' },
  { value: 'OUT_OF_SCOPE', label: '超出范围' },
  { value: 'DELIVERED', label: '已交付待确认' },
  { value: 'CANCELED', label: '已取消' },
]

function formatDate(value: string | null) {
  if (!value) return '未填写'
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(
    new Date(value),
  )
}

function formatAmount(cents: number, currency: string) {
  return new Intl.NumberFormat('zh-CN', { style: 'currency', currency }).format(cents / 100)
}

function formatBytes(value: number) {
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / (1024 * 1024)).toFixed(1)} MB`
}

async function loadReviewQueue() {
  tributes.value = await listAdminTributes(activeStatus.value)
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [nextOverview, nextOrders, nextAuditLogs, nextCustomServiceRequests, nextMemorials, nextBurials] =
      await Promise.all([
        getAdminOverview(),
        listAdminOrders(),
        listAdminAuditLogs(),
        listAdminCustomServiceRequests(),
        listAdminMemorials(),
        listAdminBurialRecords(),
      ])
    overview.value = nextOverview
    orders.value = nextOrders
    auditLogs.value = nextAuditLogs
    customServiceRequests.value = nextCustomServiceRequests
    memorials.value = nextMemorials
    burialRecords.value = nextBurials
    await loadReviewQueue()
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '管理台暂时无法读取，请稍后重试。'
  } finally {
    loading.value = false
  }
}

async function selectStatus(status: ReviewStatus) {
  activeStatus.value = status
  actionError.value = ''
  try {
    await loadReviewQueue()
  } catch (caught) {
    actionError.value = caught instanceof Error ? caught.message : '审核队列暂时无法读取。'
  }
}

async function moderate(tribute: AdminTribute, status: 'APPROVED' | 'HIDDEN') {
  const reason = (reasons.value[tribute.id] ?? '').trim()
  if (reason.length < 2) {
    actionError.value = '请先写下至少 2 个字的审核原因，便于后续追溯。'
    return
  }

  actionError.value = ''
  actionId.value = tribute.id
  try {
    await moderateAdminTribute(tribute.id, status, reason)
    reasons.value[tribute.id] = ''
    await load()
  } catch (caught) {
    actionError.value = caught instanceof Error ? caught.message : '审核操作没有完成。'
  } finally {
    actionId.value = null
  }
}

async function reviewBurial(record: BurialRecord, status: 'APPROVED' | 'REJECTED') {
  const reason = (burialReasons.value[record.id] ?? '').trim()
  if (reason.length < 2) { actionError.value = '请先写下至少 2 个字的归处档案审核原因。'; return }
  actionId.value = `burial-${record.id}`; actionError.value = ''
  try { await moderateBurialRecord(record.id, status, reason); delete burialReasons.value[record.id]; await load() }
  catch (caught) { actionError.value = caught instanceof Error ? caught.message : '归处档案审核没有完成。' }
  finally { actionId.value = null }
}

async function archiveMemorial(memorial: AdminMemorial) {
  const reason = (memorialReasons.value[memorial.id] ?? '').trim()
  if (reason.length < 2) {
    actionError.value = '请先写下至少 2 个字的下线原因，便于后续追溯。'
    return
  }
  if (!window.confirm(`下线「${memorial.petName}」后，访客会立即无法打开原分享链接。是否继续？`))
    return

  actionError.value = ''
  actionId.value = `memorial-${memorial.id}`
  try {
    await archiveAdminMemorial(memorial.id, reason)
    delete memorialReasons.value[memorial.id]
    await load()
  } catch (caught) {
    actionError.value = caught instanceof Error ? caught.message : '页面暂时没有下线。'
  } finally {
    actionId.value = null
  }
}

async function refundMockOrder(order: AdminOrder) {
  const reason = (orderRefundReasons.value[order.id] ?? '').trim()
  if (reason.length < 2) {
    actionError.value = '请先写下至少 2 个字的退款原因，便于后续追溯。'
    return
  }
  if (!window.confirm(`撤销「${order.planName}」的本地模拟开通后，账户会立即恢复到未付费权益。是否继续？`)) return

  actionError.value = ''
  actionId.value = `refund-${order.id}`
  try {
    const result = await refundAdminMockOrder(order.id, reason)
    if (!result.newlyRefunded) {
      actionError.value = '这笔订单已经完成退款，不会重复撤销权益。'
    }
    delete orderRefundReasons.value[order.id]
    await load()
  } catch (caught) {
    actionError.value = caught instanceof Error ? caught.message : '本地模拟退款暂时没有完成。'
  } finally {
    actionId.value = null
  }
}

function editFor(request: AdminCustomServiceRequest): ServiceEdit {
  const existing = serviceEdits.value[request.id]
  if (existing) return existing
  const edit: ServiceEdit = {
    status: request.status === 'COMPLETED' ? 'DELIVERED' : request.status,
    materialsReady: request.materialsReady,
    assignee: request.assignee ?? '',
    dueDate: request.dueDate ?? '',
    revisionCount: request.revisionCount,
    customerMessage: request.customerMessage ?? '',
    reason: '',
  }
  serviceEdits.value[request.id] = edit
  return edit
}

async function updateServiceRequest(request: AdminCustomServiceRequest) {
  const edit = editFor(request)
  if (edit.reason.trim().length < 2) {
    actionError.value = '请先写下至少 2 个字的更新原因，便于后续追溯。'
    return
  }
  actionError.value = ''
  actionId.value = `service-${request.id}`
  try {
    await updateAdminCustomServiceRequest(request.id, {
      ...edit,
      assignee: edit.assignee || null,
      dueDate: edit.dueDate || null,
      customerMessage: edit.customerMessage || null,
    })
    delete serviceEdits.value[request.id]
    await load()
  } catch (caught) {
    actionError.value = caught instanceof Error ? caught.message : '服务工单暂时没有更新。'
  } finally {
    actionId.value = null
  }
}

onMounted(load)
</script>

<template>
  <section class="section-narrow admin-page">
    <p class="eyebrow">运营管理台</p>
    <h1>把需要处理的事，<em>安静地处理好。</em></h1>
    <p class="intro">
      这里仅向拥有运营或审核角色的账户开放。每一次内容处置都需要写明原因，并保留在审计记录中。
    </p>

    <p v-if="error" class="form-error">{{ error }}</p>
    <p v-if="actionError" class="form-error">{{ actionError }}</p>

    <template v-if="!loading && overview">
      <section class="overview-grid" aria-label="运营概览">
        <article>
          <strong>{{ overview.pendingTributes }}</strong
          ><span>待审核留言</span>
        </article>
        <article>
          <strong>{{ overview.reportedTributes }}</strong
          ><span>被举报留言</span>
        </article>
        <article>
          <strong>{{ overview.publishedMemorials }}</strong
          ><span>已发布小窝</span>
        </article>
        <article>
          <strong>{{ overview.pendingOrders }}</strong
          ><span>待支付订单</span>
        </article>
        <article>
          <strong>{{ overview.pendingDeletionRequests }}</strong
          ><span>待清理删除请求</span>
        </article>
        <article>
          <strong>{{ overview.openCustomServiceRequests }}</strong
          ><span>进行中的定制服务</span>
        </article>
        <article>
          <strong>{{ overview.uniqueVisitorsToday }}</strong
          ><span>今日匿名独立访问</span>
        </article>
        <article>
          <strong>{{ overview.uniqueVisitorsLast7Days }}</strong
          ><span>近 7 日匿名独立访问</span>
        </article>
        <article>
          <strong>{{ overview.newMemorialsLast7Days }}</strong
          ><span>近 7 日新建小窝</span>
        </article>
        <article>
          <strong>{{ overview.publishedMemorialsLast7Days }}</strong
          ><span>近 7 日发布小窝</span>
        </article>
        <article>
          <strong>{{ overview.shareEventsLast7Days }}</strong
          ><span>近 7 日主人主动分享</span>
        </article>
        <article>
          <strong>{{ overview.paidOrdersLast30Days }}</strong
          ><span>近 30 日已支付订单</span>
        </article>
        <article>
          <strong>{{ formatBytes(overview.managedMediaBytes) }}</strong
          ><span>当前受控媒体用量</span>
        </article>
      </section>

      <p class="analytics-note">
        访问按“纪念页 + 匿名访客指纹 + 当日”去重；不会保存访问者的原始 IP 地址。分享仅统计主人复制链接或下载卡片的次数；媒体用量只汇总数据库仍管理的文件字节数。
      </p>

      <section class="admin-section">
        <div class="section-head"><div><p class="eyebrow">安葬与归处审核</p><h2>只判断是否适合公开展示</h2></div><p class="section-note">不核发线下资质，也不展示精确地址。遇到交易、导流或个人敏感信息应退回。</p></div>
        <p v-if="burialRecords.length === 0" class="empty-copy">当前没有待审核的归处档案。</p>
        <div v-else class="review-list"><article v-for="record in burialRecords" :key="record.id" class="review-card"><div class="review-meta"><span class="status-tag pending">待审核</span><span>{{ record.petName }} · {{ record.ownerDisplayName }}</span><span>{{ formatDate(record.updatedAt) }}</span></div><h3>{{ record.region || '未填写地区' }} · {{ record.placeName || '未填写场所称呼' }}</h3><p>{{ record.remembranceText || '没有补充纪念文字。' }}</p><label><span>审核原因（写入审计记录）</span><input v-model="burialReasons[record.id]" maxlength="500" placeholder="例如：符合公开规则，仅展示城市级信息" /></label><div class="review-actions"><button class="button button-small button-quiet" :disabled="actionId === `burial-${record.id}`" @click="reviewBurial(record, 'APPROVED')">通过公开</button><button class="button button-small" :disabled="actionId === `burial-${record.id}`" @click="reviewBurial(record, 'REJECTED')">退回修改</button></div></article></div>
      </section>

      <section class="admin-section">
        <div class="section-head">
          <div>
            <p class="eyebrow">内容审核</p>
            <h2>先看需要被照看的话</h2>
          </div>
          <div class="review-tabs" aria-label="审核队列筛选">
            <button
              v-for="status in reviewStatuses"
              :key="status"
              type="button"
              :class="{ active: activeStatus === status }"
              @click="selectStatus(status)"
            >
              {{ statusLabels[status] }}
            </button>
          </div>
        </div>
        <p v-if="tributes.length === 0" class="empty-copy">这个队列暂时没有需要处理的留言。</p>
        <div v-else class="review-list">
          <article v-for="tribute in tributes" :key="tribute.id" class="review-card">
            <div class="review-meta">
              <span class="status-tag" :class="tribute.status.toLowerCase()">{{
                statusLabels[activeStatus]
              }}</span
              ><span>{{ tribute.petName }} · {{ tribute.ownerDisplayName }} 的小窝</span
              ><span>{{ formatDate(tribute.createdAt) }}</span>
            </div>
            <blockquote>“{{ tribute.message }}”</blockquote>
            <p class="review-author">
              访客：{{ tribute.authorName }}
              <span v-if="tribute.reportCount">· 已被举报 {{ tribute.reportCount }} 次</span>
            </p>
            <label
              ><span>审核原因（会写入审计记录）</span
              ><input
                v-model="reasons[tribute.id]"
                :aria-label="`${tribute.authorName} 的审核原因`"
                maxlength="280"
                placeholder="例如：含不当营销内容，已隐藏"
            /></label>
            <div class="review-actions">
              <button
                class="button button-small button-quiet"
                type="button"
                :disabled="actionId === tribute.id"
                @click="moderate(tribute, 'APPROVED')"
              >
                显示留言</button
              ><button
                class="button button-small"
                type="button"
                :disabled="actionId === tribute.id"
                @click="moderate(tribute, 'HIDDEN')"
              >
                隐藏留言
              </button>
            </div>
          </article>
        </div>
      </section>

      <section class="admin-section memorial-review-section">
        <div class="section-head">
          <div>
            <p class="eyebrow">公开页巡查</p>
            <h2>必要时，先把页面安静地下线。</h2>
          </div>
          <p class="section-note">
            下线会立即关闭公开访问并留下原因；页面主人保留内容，恢复分享仍由主人完成。
          </p>
        </div>
        <p v-if="memorials.length === 0" class="empty-copy">暂时没有公开页面需要巡查。</p>
        <div v-else class="review-list">
          <article v-for="memorial in memorials" :key="memorial.id" class="review-card">
            <div class="review-meta">
              <span class="status-tag">已发布</span
              ><span>{{ memorial.petName }} · {{ memorial.species }}</span
              ><span>{{ memorial.ownerDisplayName }} 的小窝</span
              ><span>{{ formatDate(memorial.updatedAt) }}</span>
            </div>
            <blockquote v-if="memorial.farewellMessage">
              “{{ memorial.farewellMessage }}”
            </blockquote>
            <p v-else class="review-author">页面暂未填写纪念语。</p>
            <div class="review-actions">
              <a class="text-link" :href="`/m/${memorial.slug}`" target="_blank" rel="noopener"
                >在新页面查看 →</a
              >
            </div>
            <label
              ><span>下线原因（会写入审计记录）</span
              ><input
                v-model="memorialReasons[memorial.id]"
                :aria-label="`${memorial.petName} 的下线原因`"
                maxlength="280"
                placeholder="例如：公开内容需要进一步核验"
            /></label>
            <div class="review-actions">
              <button
                class="button button-small"
                type="button"
                :disabled="actionId === `memorial-${memorial.id}`"
                @click="archiveMemorial(memorial)"
              >
                {{ actionId === `memorial-${memorial.id}` ? '正在下线…' : '暂时下线页面' }}
              </button>
            </div>
          </article>
        </div>
      </section>

      <section class="admin-section service-admin-section">
        <div class="section-head">
          <div>
            <p class="eyebrow">人工定制</p>
            <h2>先确认范围，再开始安放。</h2>
          </div>
          <p class="section-note">
            用户的联系方式仅在此处对运营角色可见。第三轮修改会自动标记为超出约定范围。
          </p>
        </div>
        <p v-if="customServiceRequests.length === 0" class="empty-copy">
          暂时没有进行中的定制服务申请。
        </p>
        <div v-else class="service-request-list">
          <article
            v-for="request in customServiceRequests"
            :key="request.id"
            class="service-request-card"
          >
            <div class="service-request-head">
              <div>
                <strong>{{ request.ownerDisplayName }}</strong
                ><small>{{ request.ownerEmail }} · {{ request.contactDetails }}</small>
              </div>
              <span :class="['service-status', request.status.toLowerCase()]">{{
                serviceStatuses.find((item) => item.value === request.status)?.label ??
                request.status
              }}</span>
            </div>
            <p class="service-request-copy">{{ request.requestDetails }}</p>
            <div class="service-fields">
              <label
                ><span>服务状态</span
                ><select v-model="editFor(request).status">
                  <option
                    v-for="status in serviceStatuses"
                    :key="status.value"
                    :value="status.value"
                  >
                    {{ status.label }}
                  </option>
                </select></label
              >
              <label
                ><span>负责人</span
                ><input v-model="editFor(request).assignee" maxlength="80" placeholder="例如：小林"
              /></label>
              <label
                ><span>截止日期</span><input v-model="editFor(request).dueDate" type="date"
              /></label>
              <label
                ><span>修改轮次</span
                ><input
                  v-model.number="editFor(request).revisionCount"
                  type="number"
                  min="0"
                  max="50"
                /><small v-if="editFor(request).revisionCount > 2" class="scope-warning"
                  >第 3 轮起超出约定范围</small
                ></label
              >
              <label class="materials-check"
                ><input v-model="editFor(request).materialsReady" type="checkbox" /><span
                  >材料已确认齐全</span
                ></label
              >
              <label class="wide"
                ><span>给用户的进度说明</span
                ><textarea
                  v-model="editFor(request).customerMessage"
                  maxlength="800"
                  rows="3"
                  placeholder="例如：已收到照片，正在整理时间线。"
                />
              </label>
              <label class="wide"
                ><span>本次更新原因（写入审计记录）</span
                ><input
                  v-model="editFor(request).reason"
                  maxlength="280"
                  placeholder="例如：已确认材料齐全并安排负责人"
              /></label>
            </div>
            <button
              class="button button-small"
              type="button"
              :disabled="actionId === `service-${request.id}`"
              @click="updateServiceRequest(request)"
            >
              {{ actionId === `service-${request.id}` ? '正在保存…' : '保存服务更新' }}
            </button>
          </article>
        </div>
      </section>

      <section class="admin-section split-section">
        <div>
          <p class="eyebrow">订单概览</p>
          <h2>最近的套餐订单</h2>
        </div>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>账户</th>
                <th>套餐</th>
                <th>金额</th>
                <th>状态</th>
                <th>创建时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="order in orders" :key="order.id">
                <td>
                  <strong>{{ order.ownerDisplayName }}</strong
                  ><small>{{ order.ownerEmail }}</small>
                </td>
                <td>{{ order.planName }}</td>
                <td>{{ formatAmount(order.amountCents, order.currency) }}</td>
                <td>
                  {{ order.status }}
                  <small v-if="order.refundedAt">退款于 {{ formatDate(order.refundedAt) }}</small>
                </td>
                <td>{{ formatDate(order.createdAt) }}</td>
              </tr>
              <tr v-if="orders.length === 0">
                <td colspan="5" class="empty-copy">还没有订单。</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section class="admin-section">
        <div class="section-head">
          <div>
            <p class="eyebrow">本地付款核验</p>
            <h2>需要撤销的模拟订单</h2>
          </div>
          <p class="section-note">
            仅本地模拟支付可操作。正式支付必须通过支付渠道的退款接口处理，不能使用这里的按钮。
          </p>
        </div>
        <p v-if="refundableOrders.length === 0" class="empty-copy">
          当前没有可撤销的本地模拟订单。
        </p>
        <div v-else class="review-list">
          <article v-for="order in refundableOrders" :key="order.id" class="review-card">
            <div class="review-meta">
              <span class="status-tag">本地模拟已支付</span
              ><span>{{ order.ownerDisplayName }} · {{ order.planName }}</span
              ><span>{{ formatAmount(order.amountCents, order.currency) }}</span
              ><span>{{ formatDate(order.paidAt) }}</span>
            </div>
            <p class="review-author">
              退款会将订单标记为已退款，并立即撤销对应的套餐权益；每笔新退款都会写入审计记录。
            </p>
            <label
              ><span>退款原因（会写入审计记录）</span
              ><input
                v-model="orderRefundReasons[order.id]"
                :aria-label="`${order.planName} 的退款原因`"
                maxlength="280"
                placeholder="例如：用户不再需要付费托管"
            /></label>
            <div class="review-actions">
              <button
                class="button button-small"
                type="button"
                :disabled="actionId === `refund-${order.id}`"
                @click="refundMockOrder(order)"
              >
                {{ actionId === `refund-${order.id}` ? '正在退款…' : '撤销本地模拟付款' }}
              </button>
            </div>
          </article>
        </div>
      </section>

      <section class="admin-section split-section audit-section">
        <div>
          <p class="eyebrow">审计记录</p>
          <h2>处置都有来处</h2>
        </div>
        <ol class="audit-list">
          <li v-for="log in auditLogs" :key="log.id">
            <strong>{{
              log.action === 'TRIBUTE_APPROVED'
                ? '显示留言'
                : log.action === 'TRIBUTE_HIDDEN'
                  ? '隐藏留言'
                  : log.action === 'MEMORIAL_ARCHIVED'
                    ? '下线公开页面'
                    : log.action === 'BILLING_ORDER_REFUNDED'
                      ? '撤销本地模拟付款'
                    : '更新定制服务'
            }}</strong
            ><span
              >{{ log.actorDisplayName ?? '已删除账户' }} · {{ formatDate(log.createdAt) }}</span
            >
            <p>{{ log.reason }}</p>
          </li>
          <li v-if="auditLogs.length === 0" class="empty-copy">还没有运营处置记录。</li>
        </ol>
      </section>
    </template>
    <p v-else-if="!error" class="empty-copy">正在读取运营数据…</p>
  </section>
</template>

<style scoped>
.admin-page {
  padding-top: 48px;
}
.admin-page h1 {
  max-width: 720px;
}
.intro {
  max-width: 690px;
  margin-bottom: 42px;
  font-size: 16px;
}
.overview-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}
.overview-grid article {
  display: grid;
  gap: 6px;
  min-height: 125px;
  padding: 22px;
  border: 1px solid var(--border);
  border-radius: 18px;
  background: var(--surface);
}
.overview-grid strong {
  color: var(--primary);
  font-family: Georgia, serif;
  font-size: 38px;
}
.overview-grid span {
  color: var(--muted);
  font-size: 13px;
}
.analytics-note {
  max-width: 560px;
  margin: 14px 0 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.6;
}
.admin-section {
  margin-top: 72px;
  padding-top: 32px;
  border-top: 1px solid var(--border);
}
.section-head {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 24px;
}
.section-head h2,
.split-section h2 {
  margin-bottom: 0;
  font-size: 37px;
}
.section-note {
  max-width: 350px;
  margin: 0;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.7;
}
.review-tabs {
  display: flex;
  gap: 8px;
}
.review-tabs button {
  padding: 9px 14px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: transparent;
  color: var(--muted);
  font-size: 13px;
}
.review-tabs button.active {
  border-color: var(--primary);
  background: #faede7;
  color: var(--primary);
  font-weight: 700;
}
.review-list,
.service-request-list {
  display: grid;
  gap: 14px;
  margin-top: 30px;
}
.review-card,
.service-request-card {
  padding: 25px;
  border: 1px solid var(--border);
  border-radius: 18px;
  background: var(--surface);
}
.review-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 7px 13px;
  color: var(--muted);
  font-size: 12px;
}
.status-tag,
.service-status {
  padding: 3px 8px;
  border-radius: 999px;
  background: #f4e6d5;
  color: var(--primary);
  font-size: 12px;
  font-weight: 700;
}
.status-tag.reported,
.service-status.out_of_scope {
  background: #f6ddd9;
  color: #a64032;
}
.service-status.in_progress,
.service-status.delivered {
  background: #dfeee5;
  color: #397151;
}
.review-card blockquote {
  margin: 18px 0 10px;
  color: var(--text);
  font-family: 'Noto Serif SC', serif;
  font-size: 19px;
  line-height: 1.8;
}
.review-author {
  margin-bottom: 20px;
  font-size: 13px;
}
.review-card label,
.service-fields label {
  display: block;
}
.review-card label span,
.service-fields label > span {
  display: block;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 700;
}
.review-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 9px;
  margin-top: 14px;
}
.empty-copy {
  margin-top: 25px;
}
.service-request-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}
.service-request-head strong,
.service-request-head small {
  display: block;
}
.service-request-head small {
  margin-top: 4px;
  color: var(--muted);
  font-size: 12px;
}
.service-request-copy {
  margin: 18px 0;
  white-space: pre-wrap;
  line-height: 1.75;
}
.service-fields {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 13px;
}
.service-fields .wide {
  grid-column: span 2;
}
.service-fields textarea {
  resize: vertical;
}
.materials-check {
  display: flex !important;
  align-items: center;
  gap: 8px;
  padding-top: 28px;
}
.materials-check input {
  width: auto;
}
.materials-check span {
  margin: 0 !important;
}
.scope-warning {
  display: block;
  margin-top: 6px;
  color: #a64032;
  font-size: 12px;
}
.service-request-card > .button {
  margin-top: 16px;
}
.split-section {
  display: grid;
  grid-template-columns: 0.7fr 1.3fr;
  gap: 48px;
}
.table-wrap {
  overflow-x: auto;
}
.table-wrap table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.table-wrap th,
.table-wrap td {
  padding: 13px 9px;
  border-bottom: 1px solid var(--border);
  color: var(--muted);
  text-align: left;
  white-space: nowrap;
}
.table-wrap th {
  color: var(--text);
  font-size: 12px;
}
.table-wrap td strong,
.table-wrap td small {
  display: block;
}
.table-wrap td strong {
  color: var(--text);
}
.table-wrap td small {
  margin-top: 3px;
  font-size: 11px;
}
.audit-list {
  display: grid;
  gap: 13px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.audit-list li {
  padding: 16px 18px;
  border-radius: 14px;
  background: rgb(255 253 249 / 70%);
}
.audit-list strong,
.audit-list span {
  display: block;
}
.audit-list span {
  margin-top: 5px;
  color: var(--muted);
  font-size: 12px;
}
.audit-list p {
  margin: 9px 0 0;
  font-size: 14px;
}
.form-error {
  margin-top: 20px;
}
@media (max-width: 900px) {
  .overview-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .split-section {
    grid-template-columns: 1fr;
    gap: 28px;
  }
  .service-fields {
    grid-template-columns: repeat(2, 1fr);
  }
}
@media (max-width: 620px) {
  .overview-grid,
  .service-fields {
    grid-template-columns: 1fr;
  }
  .section-head,
  .service-request-head {
    align-items: flex-start;
    flex-direction: column;
  }
  .section-head h2,
  .split-section h2 {
    font-size: 31px;
  }
  .review-card,
  .service-request-card {
    padding: 21px;
  }
  .service-fields .wide {
    grid-column: span 1;
  }
  .materials-check {
    padding-top: 0;
  }
}
</style>
