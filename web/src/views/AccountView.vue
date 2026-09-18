<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  cancelAccountDeletion,
  downloadAccountExport,
  getAccountDeletionStatus,
  getMemorialExperience,
  getRitualSpace,
  listMemorials,
  requestAccountDeletion,
  type AccountDeletionStatus,
  type Anniversary,
  type Memorial,
  type MemorialExperience,
  type RitualSpace,
} from '@/api/memorials'
import { useAuth } from '@/stores/auth'

type MemoryOverview = {
  experience: MemorialExperience
  ritualSpace: RitualSpace
}

const router = useRouter()
const auth = useAuth()
const memorials = ref<Memorial[]>([])
const activeMemorialId = ref('')
const overview = ref<MemoryOverview | null>(null)
const loading = ref(true)
const overviewLoading = ref(false)
const errorMessage = ref('')
const overviewError = ref('')
const exporting = ref(false)
const deleting = ref(false)
const deleteConfirmationOpen = ref(false)
const deleteConfirmationText = ref('')
const deletionStatus = ref<AccountDeletionStatus>({ pending: false, scheduledFor: null })
let overviewRequest = 0

const activeMemorial = computed(
  () => memorials.value.find((memorial) => memorial.id === activeMemorialId.value) ?? null,
)

const todaySeed = computed(() => {
  const today = new Date()
  return Math.floor(Date.UTC(today.getFullYear(), today.getMonth(), today.getDate()) / 86400000)
})

const selectedDayMoment = computed(() => {
  const moments = overview.value?.experience.dayMoments ?? []
  return moments.length ? (moments[todaySeed.value % moments.length] ?? null) : null
})

const selectedLifeDetail = computed(() => {
  const details = (overview.value?.experience.lifeDetails ?? []).filter((detail) =>
    detail.answer.trim(),
  )
  return details.length ? (details[todaySeed.value % details.length] ?? null) : null
})

const revisitMemory = computed(() => {
  if (selectedDayMoment.value) {
    const detail = selectedDayMoment.value.story?.trim()
    return {
      source: `${selectedDayMoment.value.momentTime} 的一天`,
      text:
        detail ||
        `${selectedDayMoment.value.title}，${selectedDayMoment.value.placeName || '在熟悉的地方'}。`,
    }
  }
  if (selectedLifeDetail.value) return { source: '生命指纹', text: selectedLifeDetail.value.answer }
  if (activeMemorial.value?.aboutTa?.trim())
    return { source: '关于 TA', text: activeMemorial.value.aboutTa }
  if (activeMemorial.value?.farewellMessage?.trim())
    return { source: '留下的话', text: activeMemorial.value.farewellMessage }
  return {
    source: '从一个细节开始',
    text: `今天可以为 ${activeMemorial.value?.petName || 'TA'} 留下一件小事：一个动作、一种声音，或一个熟悉的位置。`,
  }
})

const memoryStats = computed(() => ({
  dayMoments: overview.value?.experience.dayMoments.length ?? 0,
  lifeDetails: overview.value?.experience.lifeDetails.length ?? 0,
  sounds: overview.value?.experience.sounds.length ?? 0,
  keepsakes: overview.value?.experience.keepsakes.length ?? 0,
}))

function toUpcomingDate(anniversary: Anniversary) {
  if (anniversary.repeatRule === 'OFF') return null
  const source = new Date(`${anniversary.eventDate}T12:00:00`)
  if (Number.isNaN(source.getTime())) return null
  const now = new Date()
  if (anniversary.repeatRule === 'ONCE') return source >= now ? source : null

  const next = new Date(now.getFullYear(), source.getMonth(), source.getDate(), 12)
  if (next < now) next.setFullYear(next.getFullYear() + 1)
  return next
}

const nextAnniversary = computed(() => {
  const candidates = (overview.value?.ritualSpace.anniversaries ?? [])
    .map((anniversary) => ({ anniversary, date: toUpcomingDate(anniversary) }))
    .filter((item): item is { anniversary: Anniversary; date: Date } => item.date !== null)
    .sort((left, right) => left.date.getTime() - right.date.getTime())
  return candidates[0] ?? null
})

const nextAnniversaryLabel = computed(() => {
  if (!nextAnniversary.value) return '还没有设置纪念日'
  const { anniversary, date } = nextAnniversary.value
  const formatter = new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric' })
  return `${formatter.format(date)} · ${anniversary.title}`
})

async function loadOverview(memorialId: string) {
  const requestId = ++overviewRequest
  overviewLoading.value = true
  overviewError.value = ''
  overview.value = null
  try {
    const [experience, ritualSpace] = await Promise.all([
      getMemorialExperience(memorialId),
      getRitualSpace(memorialId),
    ])
    if (requestId === overviewRequest) overview.value = { experience, ritualSpace }
  } catch (error) {
    if (requestId === overviewRequest) {
      overviewError.value =
        error instanceof Error ? error.message : '暂时无法整理这处纪念空间的回看内容。'
    }
  } finally {
    if (requestId === overviewRequest) overviewLoading.value = false
  }
}

async function selectMemorial(memorialId: string) {
  if (!memorialId || memorialId === activeMemorialId.value) return
  activeMemorialId.value = memorialId
  await loadOverview(memorialId)
}

async function load() {
  errorMessage.value = ''
  try {
    const [items, status] = await Promise.all([listMemorials(), getAccountDeletionStatus()])
    memorials.value = items
    deletionStatus.value = status
    const firstMemorial = items[0]
    if (firstMemorial) {
      activeMemorialId.value = firstMemorial.id
      await loadOverview(firstMemorial.id)
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '暂时无法读取你的纪念空间。'
  } finally {
    loading.value = false
  }
}

async function exportData() {
  exporting.value = true
  errorMessage.value = ''
  try {
    const archive = await downloadAccountExport()
    const url = URL.createObjectURL(archive)
    const link = document.createElement('a')
    link.href = url
    link.download = 'digital-nest-export.zip'
    link.click()
    window.setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '资料包暂时无法生成。'
  } finally {
    exporting.value = false
  }
}

function openDeletionConfirmation() {
  deleteConfirmationText.value = ''
  deleteConfirmationOpen.value = true
}

async function removeAccount() {
  if (deleteConfirmationText.value !== 'DELETE') return
  deleting.value = true
  errorMessage.value = ''
  try {
    const result = await requestAccountDeletion()
    deletionStatus.value = { pending: result.scheduled, scheduledFor: result.scheduledFor }
    deleteConfirmationOpen.value = false
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : '删除请求暂时无法提交，请稍后重试。'
  } finally {
    deleting.value = false
  }
}

async function revokeDeletion() {
  deleting.value = true
  errorMessage.value = ''
  try {
    deletionStatus.value = await cancelAccountDeletion()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '暂时无法取消删除，请稍后重试。'
  } finally {
    deleting.value = false
  }
}

onMounted(async () => {
  try {
    await auth.hydrate()
  } catch {
    errorMessage.value = '暂时无法连接服务，请稍后刷新重试。'
    loading.value = false
    return
  }
  if (!auth.state.user) {
    await router.replace({ name: 'login', query: { next: '/account' } })
    return
  }
  await load()
})
</script>

<template>
  <section class="section account-page">
    <header class="page-intro account-intro">
      <p class="eyebrow">我的纪念</p>
      <h1>你好，{{ auth.state.user?.displayName || '朋友' }}。</h1>
      <p>先从一处安静的回看开始。照片、故事与仪式记录默认只属于你，分享范围始终由你决定。</p>
      <div class="account-actions">
        <RouterLink class="button" to="/create">建立新的纪念空间</RouterLink>
        <RouterLink class="text-link" to="/account/orders">会员权益与订单</RouterLink>
        <RouterLink class="text-link" to="/custom-service">定制服务申请</RouterLink>
        <button class="text-link" type="button" :disabled="exporting" @click="exportData">
          {{ exporting ? '正在准备资料包…' : '导出我的全部资料' }}
        </button>
        <button
          v-if="!deletionStatus.pending"
          class="text-link danger-link"
          type="button"
          :disabled="deleting"
          @click="openDeletionConfirmation"
        >
          删除账号与所有内容
        </button>
        <button v-else class="text-link" type="button" :disabled="deleting" @click="revokeDeletion">
          {{ deleting ? '正在取消…' : '取消删除请求' }}
        </button>
      </div>
    </header>

    <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>

    <section
      v-if="deleteConfirmationOpen"
      class="deletion-confirmation"
      aria-labelledby="deletion-title"
    >
      <div>
        <p class="quiet-label">永久删除前确认</p>
        <h2 id="deletion-title">先把资料带走，再决定是否告别。</h2>
        <p>提交后公开页和图片会立即下线。你有 7 天时间撤销，期满后账户与纪念内容将永久删除。</p>
      </div>
      <form @submit.prevent="removeAccount">
        <label for="deletion-confirmation-text">输入 DELETE 以确认</label>
        <input
          id="deletion-confirmation-text"
          v-model="deleteConfirmationText"
          autocomplete="off"
        />
        <div class="deletion-actions">
          <button class="button button-quiet" type="button" @click="deleteConfirmationOpen = false">
            暂不删除
          </button>
          <button
            class="button danger-button"
            :disabled="deleteConfirmationText !== 'DELETE' || deleting"
          >
            {{ deleting ? '正在提交…' : '提交删除请求' }}
          </button>
        </div>
      </form>
    </section>

    <aside v-if="deletionStatus.pending" class="account-empty deletion-notice" role="status">
      <h2>删除请求已提交</h2>
      <p>
        你的公开纪念页和图片已下线。请在
        {{ new Date(deletionStatus.scheduledFor || '').toLocaleString('zh-CN') }}
        前取消删除；到期后，账户与全部内容会永久删除。
      </p>
    </aside>

    <div v-if="loading" class="account-empty account-loading"><p>正在打开你的纪念空间…</p></div>

    <template v-else-if="activeMemorial">
      <section class="memory-home" aria-labelledby="revisit-title">
        <div class="memory-home-copy">
          <p class="quiet-label">今天回来看一眼</p>
          <h2 id="revisit-title">先想起 {{ activeMemorial.petName }} 的这一点。</h2>
          <p class="memory-source">{{ revisitMemory.source }}</p>
          <blockquote>“{{ revisitMemory.text }}”</blockquote>
          <div class="revisit-actions">
            <RouterLink class="button" :to="`/editor/${activeMemorial.id}/room`"
              >补一段真实的日常</RouterLink
            >
            <RouterLink class="text-link" :to="`/editor/${activeMemorial.id}/ritual`"
              >进入纪念仪式</RouterLink
            >
          </div>
        </div>
        <aside class="memory-home-aside" :class="{ loading: overviewLoading }" aria-live="polite">
          <p class="quiet-label">已收好的痕迹</p>
          <dl>
            <div>
              <dt>一天里的时刻</dt>
              <dd>{{ memoryStats.dayMoments }}</dd>
            </div>
            <div>
              <dt>生命指纹</dt>
              <dd>{{ memoryStats.lifeDetails }}</dd>
            </div>
            <div>
              <dt>声音记忆</dt>
              <dd>{{ memoryStats.sounds }}</dd>
            </div>
            <div>
              <dt>旧物故事</dt>
              <dd>{{ memoryStats.keepsakes }}</dd>
            </div>
          </dl>
          <div class="next-revisit">
            <span>下一次回看</span>
            <strong>{{ nextAnniversaryLabel }}</strong>
            <RouterLink :to="`/editor/${activeMemorial.id}/calendar`">整理纪念日</RouterLink>
          </div>
          <p v-if="overviewError" class="form-error" role="alert">{{ overviewError }}</p>
        </aside>
      </section>

      <div
        v-if="memorials.length > 1"
        class="memorial-switcher"
        aria-label="切换纪念空间"
      >
        <button
          v-for="memorial in memorials"
          :key="memorial.id"
          type="button"
          :aria-pressed="activeMemorialId === memorial.id"
          @click="selectMemorial(memorial.id)"
        >
          {{ memorial.petName }}
        </button>
      </div>

      <section class="memory-routes" aria-label="继续整理记忆">
        <RouterLink class="memory-route route-main" :to="`/editor/${activeMemorial.id}/room`">
          <span>生命档案</span>
          <strong>把一天、习惯、声音和旧物慢慢拼回来。</strong>
          <small>从一个生活细节开始</small>
        </RouterLink>
        <RouterLink class="memory-route" :to="`/editor/${activeMemorial.id}/ritual`">
          <span>纪念仪式</span>
          <strong>留一点时间，安静想起 TA。</strong>
          <small>私密完成，不强迫分享</small>
        </RouterLink>
        <RouterLink class="memory-route" :to="`/editor/${activeMemorial.id}/calendar`">
          <span>时间信箱</span>
          <strong>纪念日与未来开启的信。</strong>
          <small>提醒由你主动开启</small>
        </RouterLink>
        <RouterLink
          class="memory-route route-habitat"
          :to="`/habitat?memorial=${activeMemorial.id}`"
        >
          <span>记忆角落</span>
          <strong>把一些生活细节留成一处风景。</strong>
          <small>私密、可选，不进入推荐流</small>
        </RouterLink>
      </section>

      <section class="all-memorials" aria-labelledby="all-memorials-title">
        <div class="all-memorials-heading">
          <div>
            <p class="quiet-label">所有纪念空间</p>
            <h2 id="all-memorials-title">每一处，都可以慢慢整理。</h2>
          </div>
          <RouterLink class="text-link" to="/create">建立新的纪念空间</RouterLink>
        </div>
        <div class="account-grid">
          <article v-for="memorial in memorials" :key="memorial.id" class="account-card">
            <div
              class="account-cover"
              :style="
                memorial.coverImageUrl
                  ? { backgroundImage: `url(${memorial.coverImageUrl})` }
                  : undefined
              "
            >
              <span v-if="!memorial.coverImageUrl" aria-hidden="true">⌁</span>
            </div>
            <div class="account-card-copy">
              <p class="account-meta">
                {{ memorial.species }} ·
                {{
                  memorial.status === 'ARCHIVED'
                    ? '已下线'
                    : memorial.status === 'PUBLISHED'
                      ? {
                          PUBLIC: '公开可见',
                          LINK: '持链接访问',
                          PASSWORD: '口令访问',
                          PRIVATE: '仅自己可见',
                        }[memorial.visibility]
                      : '私密草稿'
                }}
              </p>
              <h3>{{ memorial.petName }}</h3>
              <blockquote>
                {{
                  memorial.farewellMessage
                    ? `“${memorial.farewellMessage}”`
                    : '还没有留下第一句话。'
                }}
              </blockquote>
              <div class="card-actions">
                <RouterLink class="text-link" :to="`/editor/${memorial.id}/room`"
                  >进入生命档案</RouterLink
                >
                <RouterLink
                  v-if="memorial.status === 'PUBLISHED'"
                  class="text-link"
                  :to="`/m/${memorial.slug}`"
                  >查看纪念页</RouterLink
                >
                <RouterLink v-else class="text-link" :to="`/editor/${memorial.id}`"
                  >继续整理</RouterLink
                >
              </div>
            </div>
          </article>
        </div>
      </section>
    </template>

    <div v-else-if="!loading" class="account-empty">
      <span aria-hidden="true">⌂</span>
      <h2>这里还没有纪念空间。</h2>
      <p>从一张照片和一个昵称开始，就够了。</p>
      <RouterLink class="button" to="/create">免费创建纪念空间</RouterLink>
    </div>
  </section>
</template>

<style scoped>
.account-page {
  padding-bottom: clamp(5rem, 12vw, 10rem);
}
.account-intro {
  max-width: 48rem;
}
.account-intro > p:not(.eyebrow) {
  max-width: 39rem;
}
.account-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.8rem 1.2rem;
  margin-top: 1.8rem;
}
.danger-link {
  color: #d49a95;
}

.deletion-confirmation,
.memory-home {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(17rem, 0.65fr);
  gap: clamp(1.75rem, 5vw, 5rem);
  margin-top: 2.75rem;
  padding: clamp(1.5rem, 5vw, 4rem);
  border: 1px solid color-mix(in srgb, var(--primary) 25%, var(--border));
  border-radius: 1.1rem;
  background:
    radial-gradient(circle at 7% 12%, rgb(216 190 136 / 14%), transparent 29rem), var(--surface);
}
.deletion-confirmation h2,
.memory-home h2,
.all-memorials h2 {
  max-width: 42rem;
  margin: 0.45rem 0 1rem;
  font-size: clamp(2rem, 4vw, 3.45rem);
  line-height: 1.32;
  text-wrap: balance;
}
.deletion-confirmation p:not(.quiet-label) {
  max-width: 34rem;
  font-size: 0.94rem;
}
.deletion-confirmation form {
  align-self: end;
}
.deletion-confirmation label {
  display: block;
  margin-bottom: 0.6rem;
  color: var(--muted);
  font-size: 0.82rem;
}
.deletion-actions,
.revisit-actions,
.card-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.8rem 1.1rem;
}
.deletion-actions {
  margin-top: 1rem;
}
.danger-button {
  border-color: #b86b68;
  background: #b86b68;
}
.quiet-label {
  margin: 0;
  color: var(--primary);
  font-size: 0.72rem;
  font-weight: 650;
  letter-spacing: 0.1em;
}

.memory-home-copy {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}
.memory-source {
  margin: 0;
  color: var(--primary);
  font-size: 0.82rem;
}
.memory-home blockquote {
  max-width: 43rem;
  margin: 1rem 0 1.7rem;
  color: var(--text);
  font: clamp(1.1rem, 1.8vw, 1.45rem) / 1.85 var(--font-serif);
  white-space: pre-wrap;
}
.memory-home-aside {
  min-height: 100%;
  padding: 1.25rem 0 0 1.6rem;
  border-left: 1px solid var(--border);
}
.memory-home-aside.loading {
  opacity: 0.65;
}
.memory-home-aside dl {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1rem;
  margin: 1.45rem 0 1.6rem;
}
.memory-home-aside dl div {
  min-width: 0;
}
.memory-home-aside dt {
  color: var(--muted);
  font-size: 0.74rem;
}
.memory-home-aside dd {
  margin: 0.35rem 0 0;
  color: var(--text);
  font: 2rem / 1 var(--font-serif);
  font-variant-numeric: tabular-nums;
}
.next-revisit {
  display: grid;
  gap: 0.45rem;
  padding-top: 1.25rem;
  border-top: 1px solid var(--border);
}
.next-revisit span,
.next-revisit a {
  color: var(--muted);
  font-size: 0.78rem;
}
.next-revisit strong {
  color: var(--text);
  font: 1.08rem / 1.6 var(--font-serif);
}
.next-revisit a {
  color: var(--primary);
}

.memorial-switcher {
  display: flex;
  gap: 0.55rem;
  overflow-x: auto;
  margin: 1.2rem 0 2.75rem;
  padding-bottom: 0.25rem;
}
.memorial-switcher button {
  flex: 0 0 auto;
  padding: 0.7rem 1rem;
  border: 1px solid var(--border);
  border-radius: 0.45rem;
  background: transparent;
  color: var(--muted);
  transition:
    color 0.2s ease,
    border-color 0.2s ease,
    background 0.2s ease;
}
.memorial-switcher button[aria-pressed='true'] {
  border-color: color-mix(in srgb, var(--primary) 55%, var(--border));
  background: rgb(216 190 136 / 10%);
  color: var(--primary);
}

.memory-routes {
  display: grid;
  grid-template-columns: 1.28fr 0.72fr;
  gap: 1px;
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: 1.1rem;
  background: var(--border);
}
.memory-route {
  display: grid;
  align-content: start;
  gap: 0.65rem;
  min-height: 12.25rem;
  padding: clamp(1.3rem, 3vw, 2.25rem);
  background: var(--canvas);
  color: inherit;
  transition:
    background 0.24s ease,
    transform 0.24s cubic-bezier(0.22, 1, 0.36, 1);
}
.memory-route:hover {
  background: color-mix(in srgb, var(--surface) 88%, var(--primary));
  transform: translateY(-2px);
}
.memory-route span {
  color: var(--primary);
  font-size: 0.76rem;
}
.memory-route strong {
  max-width: 28rem;
  font: clamp(1.3rem, 2.4vw, 2rem) / 1.45 var(--font-serif);
  font-weight: 500;
}
.memory-route small {
  color: var(--muted);
  font-size: 0.78rem;
}
.memory-route.route-main {
  grid-row: span 2;
  min-height: 25.5rem;
  background:
    radial-gradient(circle at 12% 78%, rgb(216 190 136 / 15%), transparent 23rem), var(--canvas);
}
.memory-route.route-habitat {
  grid-column: 1 / -1;
  min-height: 10.75rem;
  background: linear-gradient(135deg, rgb(87 110 103 / 14%), transparent 60%), var(--canvas);
}

.all-memorials {
  margin-top: clamp(4rem, 9vw, 8rem);
}
.all-memorials-heading {
  display: flex;
  justify-content: space-between;
  align-items: end;
  gap: 1.5rem;
  margin-bottom: 2rem;
}
.all-memorials h2 {
  margin-bottom: 0;
  font-size: clamp(1.85rem, 3vw, 2.65rem);
}
.account-card {
  display: grid;
  grid-template-columns: minmax(8rem, 0.55fr) minmax(0, 1fr);
  min-height: 15rem;
}
.account-cover {
  height: auto;
  min-height: 100%;
}
.account-card-copy {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}
.account-card h3 {
  margin: 0.35rem 0 0.75rem;
  font-size: 2rem;
}
.account-card blockquote {
  margin: 0 0 1.25rem;
  color: var(--muted);
  font: 0.96rem / 1.8 var(--font-serif);
}
.card-actions {
  margin-top: auto;
}
.account-loading {
  min-height: 22rem;
}

@media (max-width: 760px) {
  .deletion-confirmation,
  .memory-home {
    grid-template-columns: 1fr;
    gap: 2rem;
    margin-top: 1.7rem;
  }
  .memory-home-aside {
    padding: 1.5rem 0 0;
    border-top: 1px solid var(--border);
    border-left: 0;
  }
  .memory-routes {
    grid-template-columns: 1fr;
  }
  .memory-route.route-main {
    grid-row: auto;
    min-height: 15rem;
  }

  .memory-route.route-habitat {
    grid-column: auto;
    min-height: 10.5rem;
  }
  .memory-route {
    min-height: 10.5rem;
  }
  .all-memorials-heading {
    display: grid;
    align-items: start;
  }
  .account-card {
    grid-template-columns: 7.5rem minmax(0, 1fr);
  }
}

@media (max-width: 480px) {
  .account-actions {
    align-items: flex-start;
    flex-direction: column;
  }
  .revisit-actions,
  .deletion-actions {
    align-items: stretch;
    flex-direction: column;
    width: 100%;
  }
  .revisit-actions .button,
  .deletion-actions .button {
    width: 100%;
  }
  .account-card {
    grid-template-columns: 1fr;
  }
  .account-cover {
    min-height: 12rem;
  }
}
</style>
