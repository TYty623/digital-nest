<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import {
  createArchiveEntry,
  deleteArchiveEntry,
  getMemorial,
  getRitualSpace,
  listGallery,
  updateArchiveEntry,
  type ArchiveEntry,
  type ArchiveEntryPayload,
  type GalleryItem,
  type Memorial,
  type RitualSpace,
} from '@/api/memorials'

const route = useRoute()
const memorialId = computed(() => String(route.params.id))
const memorial = ref<Memorial | null>(null)
const space = ref<RitualSpace>({ archiveEntries: [], anniversaries: [], capsules: [], rituals: [] })
const gallery = ref<GalleryItem[]>([])
const loading = ref(true)
const busy = ref('')
const error = ref('')
const notice = ref('')
const editingId = ref<string | null>(null)
const deletingId = ref<string | null>(null)
const composerOpen = ref(false)
const activeType = ref<'ALL' | ArchiveEntry['entryType']>('ALL')
const form = reactive<ArchiveEntryPayload>({
  entryType: 'MOMENT',
  title: '',
  body: null,
  eventDate: null,
  placeLabel: null,
  sourceLabel: '我记得',
  verificationStatus: 'CONFIRMED',
  visibility: 'PRIVATE',
  mediaId: null,
})

const typeLabels: Record<ArchiveEntry['entryType'], string> = {
  PLACE: '地点',
  RELATION: '关系',
  OBJECT: '旧物',
  MOMENT: '瞬间',
  MEDIA: '声音或影像',
}
const entriesByRecency = computed(() =>
  [...space.value.archiveEntries].sort(
    (left, right) =>
      new Date(right.updatedAt || right.createdAt).getTime() -
      new Date(left.updatedAt || left.createdAt).getTime(),
  ),
)
const featuredEntry = computed(
  () =>
    entriesByRecency.value.find((entry) => entry.body || entry.mediaUrl) ||
    entriesByRecency.value[0] ||
    null,
)
const confirmedCount = computed(
  () =>
    space.value.archiveEntries.filter((entry) => entry.verificationStatus === 'CONFIRMED').length,
)
const composerVisible = computed(
  () => composerOpen.value || space.value.archiveEntries.length === 0,
)
const grouped = computed(() => {
  const result: { type: ArchiveEntry['entryType']; entries: ArchiveEntry[] }[] = []
  for (const type of Object.keys(typeLabels) as ArchiveEntry['entryType'][]) {
    const entries = entriesByRecency.value.filter(
      (entry) =>
        entry.entryType === type && (activeType.value === 'ALL' || activeType.value === type),
    )
    if (entries.length) result.push({ type, entries })
  }
  return result
})

function reset() {
  editingId.value = null
  Object.assign(form, {
    entryType: 'MOMENT',
    title: '',
    body: null,
    eventDate: null,
    placeLabel: null,
    sourceLabel: '我记得',
    verificationStatus: 'CONFIRMED',
    visibility: 'PRIVATE',
    mediaId: null,
  })
}
function edit(entry: ArchiveEntry) {
  editingId.value = entry.id
  composerOpen.value = true
  Object.assign(form, {
    entryType: entry.entryType,
    title: entry.title,
    body: entry.body,
    eventDate: entry.eventDate,
    placeLabel: entry.placeLabel,
    sourceLabel: entry.sourceLabel,
    verificationStatus: entry.verificationStatus,
    visibility: entry.visibility,
    mediaId: entry.mediaId,
  })
  document.querySelector('#archive-form')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
async function load() {
  loading.value = true
  try {
    const [current, currentSpace, currentGallery] = await Promise.all([
      getMemorial(memorialId.value),
      getRitualSpace(memorialId.value),
      listGallery(memorialId.value),
    ])
    memorial.value = current
    space.value = currentSpace
    gallery.value = currentGallery
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '暂时无法打开生命档案。'
  } finally {
    loading.value = false
  }
}
async function save() {
  busy.value = 'save'
  error.value = ''
  notice.value = ''
  const payload: ArchiveEntryPayload = {
    ...form,
    title: form.title.trim(),
    body: form.body?.trim() || null,
    placeLabel: form.placeLabel?.trim() || null,
    sourceLabel: form.sourceLabel?.trim() || null,
    eventDate: form.eventDate || null,
    mediaId: form.mediaId || null,
  }
  try {
    if (editingId.value) {
      const updated = await updateArchiveEntry(memorialId.value, editingId.value, payload)
      space.value.archiveEntries = space.value.archiveEntries.map((entry) =>
        entry.id === updated.id ? updated : entry,
      )
      notice.value = '这段记忆已经更新。'
    } else {
      const created = await createArchiveEntry(memorialId.value, payload)
      space.value.archiveEntries.push(created)
      notice.value = '这段记忆已经放进生命档案。'
    }
    reset()
    composerOpen.value = false
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '保存没有完成。'
  } finally {
    busy.value = ''
  }
}
async function remove(entry: ArchiveEntry) {
  busy.value = entry.id
  error.value = ''
  try {
    await deleteArchiveEntry(memorialId.value, entry.id)
    space.value.archiveEntries = space.value.archiveEntries.filter((item) => item.id !== entry.id)
    if (editingId.value === entry.id) reset()
    deletingId.value = null
    notice.value = '这段档案已经移除。'
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '移除没有完成。'
  } finally {
    busy.value = ''
  }
}
onMounted(load)
</script>

<template>
  <main class="archive-page section-narrow">
    <div v-if="loading" class="account-empty">正在打开生命档案…</div>
    <template v-else-if="memorial">
      <header class="archive-intro">
        <div>
          <p class="eyebrow">{{ memorial.petName }} 的生命档案</p>
          <h1>不是生平，<br />是 TA 的生活。</h1>
          <p>一个地点、一件旧物、一个只有家人才知道的动作，都会让 TA 更像 TA。</p>
        </div>
        <div class="archive-intro-actions">
          <button class="button" type="button" @click="composerOpen = true">收好一段记忆</button
          ><RouterLink class="text-link" :to="`/editor/${memorialId}/ritual`"
            >进入仪式中心</RouterLink
          >
        </div>
      </header>
      <section class="archive-portrait" aria-label="生命档案概览">
        <div class="portrait-copy">
          <p class="eyebrow">最近收好的片段</p>
          <template v-if="featuredEntry"
            ><span class="portrait-type"
              >{{ typeLabels[featuredEntry.entryType] }} ·
              {{ featuredEntry.verificationStatus === 'CONFIRMED' ? '已确认' : '待确认' }}</span
            >
            <h2>{{ featuredEntry.title }}</h2>
            <p>{{ featuredEntry.body || '这是一段还在慢慢补全的生活片段。' }}</p>
            <small
              >{{ featuredEntry.sourceLabel || '来自生命档案'
              }}<template v-if="featuredEntry.eventDate">
                · {{ featuredEntry.eventDate }}</template
              ></small
            ></template
          ><template v-else
            ><span class="portrait-type">从一件小事开始</span>
            <h2>TA 常待的地方，是什么样子？</h2>
            <p>不需要从最重要的故事开始。写下一个位置、一种声音或一个小动作，就够了。</p></template
          >
        </div>
        <div class="portrait-ledger">
          <div>
            <strong>{{ space.archiveEntries.length }}</strong
            ><span>段生活资料</span>
          </div>
          <div>
            <strong>{{ confirmedCount }}</strong
            ><span>段已确认</span>
          </div>
          <p><span aria-hidden="true">⌖</span> 地点只保存你愿意写下的称呼，不需要住址或坐标。</p>
        </div>
      </section>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <p v-if="notice" class="form-success" role="status">{{ notice }}</p>
      <section v-if="composerVisible" id="archive-form" class="archive-form-panel">
        <div>
          <p class="eyebrow">{{ editingId ? '编辑这段记忆' : '添一段真实资料' }}</p>
          <h2>{{ editingId ? '让它更接近你记得的样子' : '从最小的一件事开始' }}</h2>
          <p>来源、发生时间和确认状态会和这段记忆一起保存。</p>
        </div>
        <form class="archive-form" @submit.prevent="save">
          <label
            >这是什么<select v-model="form.entryType">
              <option v-for="(label, key) in typeLabels" :key="key" :value="key">
                {{ label }}
              </option>
            </select></label
          ><label
            >给它一个称呼<input
              v-model="form.title"
              required
              maxlength="80"
              placeholder="例如：阳台最左边的垫子" /></label
          ><label
            >发生的日期（选填）<input
              v-model="form.eventDate"
              type="date"
              :max="new Date().toISOString().slice(0, 10)" /></label
          ><label
            >地点或关系称呼（选填）<input
              v-model="form.placeLabel"
              maxlength="100"
              placeholder="例如：家里的窗边 / 小麦的姨姨" /></label
          ><label
            >记忆来源（选填）<input
              v-model="form.sourceLabel"
              maxlength="80"
              placeholder="例如：我记得" /></label
          ><label
            >确认状态<select v-model="form.verificationStatus">
              <option value="CONFIRMED">我确认过</option>
              <option value="PENDING">还想和家人核对</option>
            </select></label
          ><label
            >可见范围<select v-model="form.visibility">
              <option value="PRIVATE">仅自己可见</option>
              <option value="FAMILY">允许受邀亲友查看</option>
            </select></label
          ><label
            >关联相册媒体（选填）<select v-model="form.mediaId">
              <option :value="null">暂不关联</option>
              <option v-for="item in gallery" :key="item.mediaId" :value="item.mediaId">
                {{ item.caption || `相册媒体 ${item.position + 1}` }}
              </option>
            </select></label
          ><label class="wide"
            >这段记忆的细节<textarea
              v-model="form.body"
              rows="4"
              maxlength="1200"
              placeholder="动作、气味、声音、表情，越具体越像 TA。"
            ></textarea>
          </label>
          <div class="archive-actions">
            <button class="button" :disabled="busy === 'save'">
              {{ busy === 'save' ? '正在收好…' : editingId ? '保存修改' : '放进生命档案' }}</button
            ><button v-if="editingId" class="button button-quiet" type="button" @click="reset">
              取消编辑
            </button>
          </div>
        </form>
      </section>
      <section class="archive-map" aria-labelledby="archive-map-title">
        <div class="map-heading">
          <div>
            <p class="eyebrow">记忆地图</p>
            <h2 id="archive-map-title">
              {{
                space.archiveEntries.length
                  ? '每一处都指向 TA 的生活。'
                  : '地图会从第一段记忆开始出现。'
              }}
            </h2>
          </div>
          <div>
            <p>所有条目默认为私密，不会自动进入社区或公开页面。</p>
            <div
              v-if="space.archiveEntries.length"
              class="archive-filters"
              aria-label="筛选档案类型"
            >
              <button
                type="button"
                :aria-pressed="activeType === 'ALL'"
                @click="activeType = 'ALL'"
              >
                全部</button
              ><button
                v-for="(label, key) in typeLabels"
                :key="key"
                type="button"
                :aria-pressed="activeType === key"
                @click="activeType = key"
              >
                {{ label }}
              </button>
            </div>
          </div>
        </div>
        <div v-if="grouped.length" class="archive-groups">
          <section v-for="group in grouped" :key="group.type" class="archive-group">
            <header>
              <span>{{ typeLabels[group.type] }}</span
              ><small>{{ group.entries.length }} 条</small>
            </header>
            <article v-for="entry in group.entries" :key="entry.id" class="archive-entry">
              <img
                v-if="entry.mediaUrl && entry.entryType !== 'MEDIA'"
                :src="entry.mediaUrl"
                :alt="entry.title"
              />
              <div>
                <div class="entry-meta">
                  <span v-if="entry.verificationStatus === 'PENDING'">待确认</span
                  ><span>{{ entry.visibility === 'FAMILY' ? '亲友可见' : '仅自己可见' }}</span>
                </div>
                <h3>{{ entry.title }}</h3>
                <p v-if="entry.body">{{ entry.body }}</p>
                <small
                  >{{ entry.placeLabel || entry.sourceLabel || '来自生活里的一个坐标'
                  }}<template v-if="entry.eventDate"> · {{ entry.eventDate }}</template></small
                >
                <div class="entry-actions">
                  <button class="text-button" type="button" @click="edit(entry)">编辑</button
                  ><template v-if="deletingId === entry.id"
                    ><span class="delete-confirmation">确认移除？原始相册媒体不会被删除。</span
                    ><button
                      class="text-button danger"
                      type="button"
                      :disabled="busy === entry.id"
                      @click="remove(entry)"
                    >
                      确认移除</button
                    ><button class="text-button" type="button" @click="deletingId = null">
                      取消
                    </button></template
                  ><button
                    v-else
                    class="text-button danger"
                    type="button"
                    @click="deletingId = entry.id"
                  >
                    删除
                  </button>
                </div>
              </div>
            </article>
          </section>
        </div>
        <div v-else class="archive-empty">
          <span aria-hidden="true">⌖</span>
          <p>
            {{
              activeType === 'ALL'
                ? '不需要从最重要的故事开始。先写下 TA 常待的一个位置也很好。'
                : `还没有收好“${typeLabels[activeType]}”类型的记忆。`
            }}
          </p>
          <button
            v-if="activeType !== 'ALL'"
            class="text-button"
            type="button"
            @click="activeType = 'ALL'"
          >
            查看全部记忆
          </button>
        </div>
      </section>
    </template>
  </main>
</template>

<style scoped>
.archive-page {
  padding-block: clamp(2rem, 5vw, 5.5rem);
}
.archive-intro {
  display: flex;
  justify-content: space-between;
  align-items: end;
  gap: 2rem;
  margin-bottom: 2.25rem;
}
.archive-intro h1 {
  margin: 0.45rem 0 1rem;
  font: clamp(42px, 6vw, 76px) / 1.05 var(--font-serif);
  text-wrap: balance;
}
.archive-intro p:last-child {
  max-width: 570px;
  color: var(--muted);
  line-height: 1.85;
}
.archive-intro-actions {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding-bottom: 0.45rem;
  white-space: nowrap;
}
.archive-portrait {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(240px, 0.6fr);
  overflow: hidden;
  margin-bottom: clamp(2.5rem, 6vw, 4.75rem);
  border: 1px solid color-mix(in srgb, var(--primary) 28%, var(--border));
  border-radius: 26px;
  background:
    radial-gradient(circle at 81% 14%, rgb(216 190 136 / 16%), transparent 27%),
    linear-gradient(145deg, color-mix(in srgb, var(--surface) 92%, #101b28), var(--surface));
  box-shadow: var(--shadow);
}
.portrait-copy {
  min-height: 290px;
  padding: clamp(2rem, 5vw, 4.25rem);
}
.portrait-copy h2 {
  max-width: 670px;
  margin: 0.55rem 0 1rem;
  font: clamp(34px, 4.6vw, 58px) / 1.14 var(--font-serif);
  text-wrap: balance;
}
.portrait-copy > p:not(.eyebrow) {
  max-width: 610px;
  color: var(--muted);
  line-height: 1.85;
  white-space: pre-wrap;
}
.portrait-copy small {
  display: block;
  margin-top: 1.2rem;
  color: var(--primary);
}
.portrait-type {
  color: var(--primary);
  font-size: 0.78rem;
  letter-spacing: 0.08em;
}
.portrait-ledger {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 1.5rem;
  padding: clamp(2rem, 4vw, 3.5rem);
  border-left: 1px solid color-mix(in srgb, var(--border) 65%, transparent);
  background: rgb(9 18 28 / 18%);
}
.portrait-ledger > div {
  display: grid;
  gap: 0.2rem;
}
.portrait-ledger strong {
  font: 2.35rem / 1 var(--font-serif);
  color: var(--primary);
  font-variant-numeric: tabular-nums;
}
.portrait-ledger span {
  color: var(--muted);
  font-size: 0.82rem;
}
.portrait-ledger p {
  padding-top: 1.4rem;
  margin: 0;
  border-top: 1px solid color-mix(in srgb, var(--border) 72%, transparent);
  color: var(--muted);
  font-size: 0.8rem;
  line-height: 1.7;
}
.portrait-ledger p span {
  margin-right: 0.35rem;
  color: var(--primary);
}
.archive-form-panel {
  display: grid;
  grid-template-columns: minmax(220px, 0.65fr) minmax(0, 1.35fr);
  gap: clamp(30px, 6vw, 84px);
  margin-bottom: clamp(3rem, 7vw, 6rem);
  padding: clamp(24px, 5vw, 56px);
  border: 1px solid var(--border);
  border-radius: 24px;
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--surface) 92%, var(--primary)),
    var(--surface)
  );
}
.archive-form-panel h2 {
  margin: 0.45rem 0 0.8rem;
  font: clamp(28px, 3.4vw, 42px) / 1.2 var(--font-serif);
}
.archive-form-panel > div > p:last-child {
  color: var(--muted);
  line-height: 1.7;
}
.archive-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1rem;
}
.archive-form label {
  display: grid;
  gap: 0.45rem;
  color: var(--muted);
  font-size: 0.88rem;
}
.archive-form input,
.archive-form select,
.archive-form textarea {
  width: 100%;
  color: var(--text);
}
.archive-form .wide,
.archive-actions {
  grid-column: 1 / -1;
}
.archive-actions {
  display: flex;
  gap: 0.8rem;
  flex-wrap: wrap;
}
.archive-map {
  padding-top: 2rem;
  border-top: 1px solid var(--border);
}
.map-heading {
  display: grid;
  grid-template-columns: minmax(260px, 0.8fr) minmax(0, 1.2fr);
  gap: 2rem;
  align-items: end;
  margin-bottom: 2rem;
}
.map-heading h2 {
  margin: 0.4rem 0 0;
  font: clamp(32px, 4vw, 52px) / 1.16 var(--font-serif);
  text-wrap: balance;
}
.map-heading > div:last-child > p {
  max-width: 430px;
  margin: 0;
  color: var(--muted);
  line-height: 1.75;
}
.archive-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 0.55rem;
  margin-top: 1.25rem;
}
.archive-filters button {
  padding: 0.42rem 0.7rem;
  border: 1px solid color-mix(in srgb, var(--border) 85%, transparent);
  border-radius: 999px;
  background: transparent;
  color: var(--muted);
  font: inherit;
  font-size: 0.78rem;
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    background 0.2s ease,
    color 0.2s ease;
}
.archive-filters button[aria-pressed='true'] {
  border-color: color-mix(in srgb, var(--primary) 65%, var(--border));
  background: color-mix(in srgb, var(--primary) 12%, transparent);
  color: var(--primary);
}
.archive-groups {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: clamp(1rem, 3vw, 2rem);
}
.archive-group {
  border-top: 1px solid var(--border);
}
.archive-group > header {
  display: flex;
  justify-content: space-between;
  padding: 1rem 0;
  color: var(--primary);
}
.archive-group > header small {
  color: var(--muted);
}
.archive-entry {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr);
  gap: 1rem;
  padding: 1.3rem 0 1.6rem;
  border-top: 1px solid color-mix(in srgb, var(--border) 68%, transparent);
}
.archive-entry img {
  width: 96px;
  height: 96px;
  object-fit: cover;
  border-radius: 10px;
}
.entry-meta {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}
.entry-meta span {
  padding: 0.2rem 0.5rem;
  border: 1px solid var(--border);
  border-radius: 999px;
  color: var(--muted);
  font-size: 0.72rem;
}
.entry-meta span:first-child {
  color: var(--primary);
}
.archive-entry h3 {
  margin: 0.6rem 0;
  font-size: 1.12rem;
}
.archive-entry p {
  margin: 0.4rem 0;
  color: var(--muted);
  white-space: pre-wrap;
  line-height: 1.65;
}
.archive-entry small {
  color: var(--muted);
}
.entry-actions {
  display: flex;
  align-items: center;
  gap: 0.85rem;
  flex-wrap: wrap;
  margin-top: 0.8rem;
}
.delete-confirmation {
  color: var(--muted);
  font-size: 0.78rem;
}
.danger {
  color: #d99b96;
}
.archive-empty {
  padding: 3rem;
  border: 1px dashed var(--border);
  color: var(--muted);
  text-align: center;
}
.archive-empty span {
  display: block;
  margin-bottom: 0.8rem;
  color: var(--primary);
  font-size: 2rem;
}
@media (max-width: 780px) {
  .archive-intro,
  .archive-form-panel,
  .map-heading {
    display: grid;
  }
  .map-heading {
    grid-template-columns: 1fr;
  }
  .archive-intro-actions {
    padding: 0;
  }
  .archive-portrait {
    grid-template-columns: 1fr;
  }
  .portrait-ledger {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    border-top: 1px solid color-mix(in srgb, var(--border) 65%, transparent);
    border-left: 0;
  }
  .portrait-ledger p {
    grid-column: 1 / -1;
  }
  .archive-form,
  .archive-groups {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 480px) {
  .archive-intro-actions {
    align-items: flex-start;
    flex-direction: column;
  }
  .portrait-copy {
    min-height: 0;
  }
  .portrait-ledger {
    grid-template-columns: 1fr;
  }
  .portrait-ledger p {
    grid-column: auto;
  }
  .archive-form {
    grid-template-columns: 1fr;
  }
  .archive-entry {
    grid-template-columns: 1fr;
  }
  .archive-entry img {
    width: 100%;
    height: 180px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .archive-filters button {
    transition: none;
  }
}
</style>
