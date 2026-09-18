<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import {
  completeRitual,
  getMemorial,
  getRitualSpace,
  type Memorial,
  type RitualRecord,
  type RitualSpace,
} from '@/api/memorials'

const route = useRoute()
const memorialId = computed(() => String(route.params.id))
const memorial = ref<Memorial | null>(null)
const space = ref<RitualSpace>({ archiveEntries: [], anniversaries: [], capsules: [], rituals: [] })
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const notice = ref('')
const stage = ref<0 | 1 | 2 | 3>(0)
const ritualType = ref<RitualRecord['ritualType']>('SEASON')
const ritualAction = ref<RitualRecord['ritualAction']>('LIGHT')
const note = ref('')
const ambientEnabled = ref(false)
const memoryCursor = ref(0)

const recallableMemories = computed(() => {
  const confirmed = space.value.archiveEntries.filter(
    (entry) => entry.verificationStatus === 'CONFIRMED',
  )
  return confirmed.length ? confirmed : space.value.archiveEntries
})
const memory = computed(
  () =>
    recallableMemories.value[memoryCursor.value % Math.max(recallableMemories.value.length, 1)] ||
    null,
)
const title = computed(() => memorial.value?.petName || 'TA')
const stageLabel = computed(() => ['进入仪式', '回看记忆', '留下一束光', '安静收束'][stage.value])

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [current, currentSpace] = await Promise.all([
      getMemorial(memorialId.value),
      getRitualSpace(memorialId.value),
    ])
    memorial.value = current
    space.value = currentSpace
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '暂时无法打开仪式中心。'
  } finally {
    loading.value = false
  }
}

function start() {
  error.value = ''
  notice.value = ''
  memoryCursor.value = new Date().getDate() % Math.max(recallableMemories.value.length, 1)
  stage.value = 1
}

function recallAnother() {
  if (recallableMemories.value.length > 1)
    memoryCursor.value = (memoryCursor.value + 1) % recallableMemories.value.length
}

async function finish() {
  saving.value = true
  error.value = ''
  try {
    const created = await completeRitual(memorialId.value, {
      ritualType: ritualType.value,
      ritualAction: ritualAction.value,
      note: note.value.trim() || null,
      ambientEnabled: ambientEnabled.value,
    })
    space.value.rituals.unshift(created)
    stage.value = 3
    notice.value = '今天，我们又想起了 TA。仪式记录只保存在你的纪念空间里。'
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '这次仪式没有保存，请稍后重试。'
  } finally {
    saving.value = false
  }
}

function reset() {
  stage.value = 0
  note.value = ''
  ambientEnabled.value = false
}

const actionCopy: Record<
  RitualRecord['ritualAction'],
  { title: string; description: string; symbol: string }
> = {
  LIGHT: { title: '点亮一盏记忆灯', description: '为此刻留下一束安静的光。', symbol: '✦' },
  FLOWER: { title: '种下一朵纪念花', description: '让今天的想念有一个温柔的落点。', symbol: '✿' },
  LETTER: { title: '写下一句想说的话', description: '不必完整，一句话就够了。', symbol: '⌁' },
  SOUND: {
    title: '听一段熟悉的声音',
    description: '仪式不会自动播放音频，你可以自行选择。',
    symbol: '◌',
  },
  CAPSULE: { title: '收进一段时间胶囊', description: '把今天留给未来的自己再打开。', symbol: '◒' },
}

onMounted(load)
</script>

<template>
  <main class="ritual-page section-narrow" :class="{ 'is-in-ritual': stage > 0 }">
    <div v-if="loading" class="account-empty">正在整理一处安静的空间…</div>
    <template v-else-if="memorial">
      <header class="ritual-intro">
        <div>
          <p class="eyebrow">{{ memorial.petName }} 的纪念仪式</p>
          <h1>留一点时间，<br />只用来想起 TA。</h1>
          <p>这不是任务，也没有连续记录。每次回来，只完成一个你愿意做的小动作，然后回到日常。</p>
        </div>
        <RouterLink class="button button-quiet" :to="`/editor/${memorialId}/room`"
          >回到纪念小屋</RouterLink
        >
      </header>

      <nav class="ritual-links" aria-label="纪念工具">
        <span>仪式之外</span>
        <RouterLink :to="`/editor/${memorialId}/archive`"
          >生命档案 <small>{{ space.archiveEntries.length }} 段资料</small></RouterLink
        >
        <RouterLink :to="`/editor/${memorialId}/calendar`"
          >纪念日与胶囊 <small>{{ space.anniversaries.length }} 个日期</small></RouterLink
        >
        <RouterLink :to="`/editor/${memorialId}/yearbook`">年度纪念册</RouterLink>
        <RouterLink :to="`/editor/${memorialId}/digital-life`">数字记忆档案</RouterLink>
      </nav>

      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <p v-if="notice" class="form-success" role="status">{{ notice }}</p>

      <section class="ritual-stage" :data-stage="stage" aria-labelledby="ritual-title">
        <div class="ritual-light" aria-hidden="true"><i></i><i></i><i></i></div>
        <div class="ritual-progress" aria-label="仪式进度">
          <span v-for="index in 4" :key="index" :class="{ active: stage >= index - 1 }"
            >0{{ index }}</span
          >
          <small>{{ stageLabel }}</small>
        </div>

        <article v-if="stage === 0" class="stage-card stage-enter">
          <p class="eyebrow">先让周围慢一点</p>
          <span class="ritual-symbol" aria-hidden="true">✦</span>
          <h2 id="ritual-title">现在，想为 {{ title }} 留下几分钟吗？</h2>
          <p>进入后页面会安静下来。不会自动播放声音，也不会把任何内容公开；你随时可以离开。</p>
          <div class="ritual-type-row" aria-label="选择仪式主题">
            <button
              v-for="item in [
                ['SEASON', '四季回忆'],
                ['BIRTHDAY', '生日回望'],
                ['ADOPTION', '领养纪念'],
                ['FAREWELL', '离开周年'],
                ['FAMILY', '家庭共同纪念'],
              ] as const"
              :key="item[0]"
              type="button"
              :class="{ selected: ritualType === item[0] }"
              @click="ritualType = item[0]"
            >
              {{ item[1] }}
            </button>
          </div>
          <button class="button ritual-start" type="button" @click="start">安静地进入</button>
        </article>

        <article v-else-if="stage === 1" class="stage-card stage-memory">
          <p class="eyebrow">回看一段真实的记忆</p>
          <figure v-if="memory" class="ritual-memory-card">
            <img v-if="memory.mediaUrl" :src="memory.mediaUrl" :alt="memory.title" />
            <div>
              <span>{{
                memory.entryType === 'PLACE' ? memory.placeLabel || '熟悉的地方' : '生命档案'
              }}</span>
              <h2>{{ memory.title }}</h2>
              <p>{{ memory.body || '这是一段由家人确认过的记忆。' }}</p>
              <small
                >{{ memory.sourceLabel ? `来源：${memory.sourceLabel}` : '来自你的生命档案'
                }}<template v-if="memory.eventDate"> · {{ memory.eventDate }}</template
                ><template v-if="memory.verificationStatus === 'PENDING'">
                  · 待确认</template
                ></small
              >
            </div>
          </figure>
          <div v-else class="ritual-empty-memory">
            <span aria-hidden="true">⌁</span>
            <h2>还没有选中的片段。</h2>
            <p>可以先去生命档案，收好一个地点、一件旧物或一个小习惯。</p>
            <RouterLink class="text-link" :to="`/editor/${memorialId}/archive`"
              >去补一段记忆 →</RouterLink
            >
          </div>
          <div class="stage-actions">
            <button class="button button-quiet" type="button" @click="stage = 0">稍后再来</button
            ><button
              v-if="recallableMemories.length > 1"
              class="text-button"
              type="button"
              @click="recallAnother"
            >
              换一段记忆</button
            ><button class="button" type="button" @click="stage = 2">带着这段记忆继续</button>
          </div>
        </article>

        <article v-else-if="stage === 2" class="stage-card stage-action">
          <p class="eyebrow">留下一件小事</p>
          <h2>今天想怎样记住 {{ title }}？</h2>
          <div class="action-grid">
            <button
              v-for="(item, key) in actionCopy"
              :key="key"
              type="button"
              :class="{ selected: ritualAction === key }"
              @click="ritualAction = key"
            >
              <i aria-hidden="true">{{ item.symbol }}</i
              ><strong>{{ item.title }}</strong
              ><span>{{ item.description }}</span>
            </button>
          </div>
          <label class="ritual-note"
            >此刻想写的话（选填）<textarea
              v-model="note"
              rows="3"
              maxlength="500"
              placeholder="例如：今天路过那家店，又想起你坐在门口等我的样子。"
            ></textarea>
          </label>
          <label class="ambient-toggle"
            ><input
              v-model="ambientEnabled"
              type="checkbox"
            />保留“环境声音”偏好（不会自动播放）</label
          >
          <div class="stage-actions">
            <button class="button button-quiet" type="button" @click="stage = 1">
              回看刚才的记忆</button
            ><button
              class="button ritual-complete"
              type="button"
              :disabled="saving"
              @click="finish"
            >
              {{ saving ? '正在收好这次仪式…' : '完成这次纪念' }}
            </button>
          </div>
        </article>

        <article v-else class="stage-card stage-close">
          <span class="ritual-symbol bloom" aria-hidden="true">✦</span>
          <p class="eyebrow">今天，我们又想起了 {{ title }}</p>
          <h2>这次仪式已经收好。</h2>
          <p>
            这次{{
              actionCopy[ritualAction].title
            }}只会出现在你的年度纪念册和私密记录中。你可以回到日常，也可以再留一句话。
          </p>
          <div class="stage-actions">
            <button class="button button-quiet" type="button" @click="reset">回到仪式入口</button
            ><RouterLink class="button" :to="`/editor/${memorialId}/yearbook`"
              >看看年度纪念册</RouterLink
            >
          </div>
        </article>
      </section>

      <section class="ritual-history" aria-labelledby="ritual-history-title">
        <div>
          <p class="eyebrow">已经留下的仪式</p>
          <h2 id="ritual-history-title">不是连续天数，只是你曾经回来过。</h2>
        </div>
        <ol v-if="space.rituals.length">
          <li v-for="ritual in space.rituals.slice(0, 6)" :key="ritual.id">
            <time>{{ new Date(ritual.completedAt).toLocaleDateString('zh-CN') }}</time
            ><strong>{{ actionCopy[ritual.ritualAction].title }}</strong>
            <p>{{ ritual.note || '完成了一次安静的纪念。' }}</p>
          </li>
        </ol>
        <p v-else class="history-empty">第一次回来，不需要留下任何证明。</p>
      </section>
    </template>
  </main>
</template>

<style scoped>
.ritual-page {
  position: relative;
  min-height: calc(100dvh - 170px);
  padding-block: clamp(2rem, 5vw, 5rem);
  transition: filter 0.7s ease;
}
.ritual-page.is-in-ritual {
  isolation: isolate;
}
.ritual-page.is-in-ritual::before {
  position: fixed;
  z-index: 0;
  inset: 0;
  background: rgb(2 8 17 / 38%);
  content: '';
  pointer-events: none;
  animation: ritual-dim 0.8s ease both;
}
.ritual-page.is-in-ritual > :not(.ritual-stage) {
  position: relative;
  z-index: 1;
  opacity: 0.52;
  transition: opacity 0.5s ease;
}
.ritual-intro {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 2rem;
  margin-bottom: 2rem;
}
.ritual-intro h1 {
  margin: 0.35rem 0 1rem;
  font: clamp(42px, 6vw, 76px)/1.05 var(--font-serif);
}
.ritual-intro > div > p:last-child {
  max-width: 580px;
  color: var(--muted);
  line-height: 1.8;
}
.ritual-links {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 0.55rem 1.25rem;
  margin: 1.75rem 0 3.25rem;
  padding-bottom: 1.1rem;
  border-bottom: 1px solid var(--border);
}
.ritual-links a {
  color: var(--muted);
  font-size: 0.88rem;
  text-decoration: none;
  transition: color 0.2s ease;
}
.ritual-links a:hover {
  color: var(--primary);
}
.ritual-links span {
  color: var(--primary);
  font-size: 0.78rem;
  letter-spacing: 0.08em;
}
.ritual-links small {
  color: color-mix(in srgb, var(--muted) 72%, transparent);
  font-size: 0.74rem;
}
.ritual-stage {
  position: relative;
  z-index: 2;
  overflow: hidden;
  padding: clamp(24px, 6vw, 72px);
  border: 1px solid color-mix(in srgb, var(--primary) 38%, var(--border));
  border-radius: 30px;
  background:
    radial-gradient(circle at 80% 10%, rgb(225 191 126 / 15%), transparent 32%),
    linear-gradient(140deg, color-mix(in srgb, var(--canvas) 92%, #040811), var(--surface));
  box-shadow: 0 36px 90px rgb(0 0 0 / 18%);
}
.ritual-light {
  position: absolute;
  inset: 0;
  pointer-events: none;
  opacity: 0.55;
}
.ritual-light i {
  --orb-scale: 1;
  position: absolute;
  width: min(32vw, 320px);
  aspect-ratio: 1;
  border: 1px solid rgb(225 191 126 / 11%);
  border-radius: 50%;
  background: radial-gradient(circle, rgb(225 191 126 / 10%), transparent 62%);
  filter: blur(1px);
  transform: scale(var(--orb-scale));
  animation: ritual-float 8s ease-in-out infinite;
}
.ritual-light i:nth-child(1) {
  left: 17%;
  top: 24%;
  animation-delay: -1.2s;
}
.ritual-light i:nth-child(2) {
  right: 16%;
  top: 32%;
  animation-delay: -3.4s;
  --orb-scale: 0.55;
}
.ritual-light i:nth-child(3) {
  left: 63%;
  bottom: 18%;
  animation-delay: -0.4s;
  --orb-scale: 0.3;
}
.ritual-progress {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: clamp(2rem, 5vw, 4rem);
  color: var(--muted);
  font-size: 0.78rem;
  letter-spacing: 0.08em;
}
.ritual-progress span {
  display: grid;
  width: 29px;
  height: 29px;
  place-items: center;
  border: 1px solid var(--border);
  border-radius: 50%;
}
.ritual-progress span.active {
  border-color: var(--primary);
  background: color-mix(in srgb, var(--primary) 13%, transparent);
  color: var(--primary);
}
.ritual-progress small {
  margin-left: auto;
}
.stage-card {
  position: relative;
  z-index: 1;
  max-width: 920px;
  margin: auto;
  text-align: center;
}
.stage-card h2 {
  margin: 0.6rem auto 1.1rem;
  font: clamp(34px, 5vw, 60px)/1.18 var(--font-serif);
}
.stage-card > p:not(.eyebrow) {
  max-width: 620px;
  margin: 0 auto;
  color: var(--muted);
  line-height: 1.85;
}
.ritual-symbol {
  display: grid;
  width: 82px;
  aspect-ratio: 1;
  margin: 0 auto 1.25rem;
  place-items: center;
  border: 1px solid color-mix(in srgb, var(--primary) 38%, transparent);
  border-radius: 50%;
  background: radial-gradient(
    circle,
    color-mix(in srgb, var(--primary) 22%, transparent),
    transparent 70%
  );
  color: var(--primary);
  font-size: 2rem;
}
.ritual-type-row {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 0.7rem;
  max-width: 680px;
  margin: 2rem auto;
}
.ritual-type-row button,
.action-grid button {
  border: 1px solid var(--border);
  background: color-mix(in srgb, var(--canvas) 70%, transparent);
  color: var(--text);
  cursor: pointer;
}
.ritual-type-row button {
  padding: 0.65rem 0.9rem;
  border-radius: 999px;
}
.ritual-type-row button.selected,
.action-grid button.selected {
  border-color: var(--primary);
  background: color-mix(in srgb, var(--primary) 14%, var(--canvas));
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--primary) 35%, transparent);
}
.ritual-start {
  margin-top: 0.5rem;
}
.ritual-memory-card {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(280px, 1.1fr);
  overflow: hidden;
  margin: 2rem 0;
  border: 1px solid color-mix(in srgb, var(--primary) 22%, var(--border));
  border-radius: 20px;
  background: color-mix(in srgb, var(--canvas) 65%, transparent);
  text-align: left;
}
.ritual-memory-card img {
  width: 100%;
  height: 100%;
  min-height: 300px;
  object-fit: cover;
}
.ritual-memory-card > div {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: clamp(24px, 5vw, 56px);
}
.ritual-memory-card span,
.ritual-memory-card small {
  color: var(--primary);
}
.ritual-memory-card h2 {
  margin: 0.55rem 0 1rem;
  font-size: clamp(30px, 4vw, 44px);
}
.ritual-memory-card p {
  line-height: 1.8;
  white-space: pre-wrap;
}
.ritual-empty-memory {
  padding: 3rem;
  border: 1px dashed var(--border);
  border-radius: 18px;
}
.ritual-empty-memory > span {
  font-size: 2rem;
  color: var(--primary);
}
.stage-actions {
  display: flex;
  justify-content: center;
  gap: 0.8rem;
  flex-wrap: wrap;
  margin-top: 2rem;
}
.action-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 0.7rem;
  margin: 2rem 0;
  text-align: left;
}
.action-grid button {
  display: grid;
  gap: 0.55rem;
  min-height: 154px;
  padding: 1rem;
  border-radius: 14px;
}
.action-grid i {
  font-style: normal;
  color: var(--primary);
  font-size: 1.45rem;
}
.action-grid strong {
  font-size: 0.95rem;
}
.action-grid span {
  color: var(--muted);
  font-size: 0.78rem;
  line-height: 1.5;
}
.ritual-note {
  display: grid;
  max-width: 700px;
  gap: 0.5rem;
  margin: 0 auto;
  text-align: left;
}
.ritual-note textarea {
  width: 100%;
  resize: vertical;
}
.ambient-toggle {
  display: inline-flex;
  gap: 0.55rem;
  align-items: center;
  margin-top: 1rem;
  color: var(--muted);
  font-size: 0.88rem;
}
.ritual-complete {
  min-width: 180px;
}
.bloom {
  animation: ritual-bloom 1s cubic-bezier(0.2, 0.8, 0.2, 1) both;
}
.ritual-history {
  display: grid;
  grid-template-columns: minmax(260px, 0.8fr) minmax(0, 1.2fr);
  gap: clamp(30px, 6vw, 80px);
  margin-top: clamp(3rem, 8vw, 7rem);
  padding-top: 3rem;
  border-top: 1px solid var(--border);
}
.ritual-history h2 {
  margin: 0.5rem 0;
  font: clamp(30px, 4vw, 48px)/1.2 var(--font-serif);
}
.ritual-history ol {
  display: grid;
  gap: 1px;
  margin: 0;
  padding: 0;
  background: var(--border);
  list-style: none;
}
.ritual-history li {
  display: grid;
  grid-template-columns: 120px 1fr;
  column-gap: 1rem;
  padding: 1.2rem;
  background: var(--canvas);
}
.ritual-history li p {
  grid-column: 2;
  margin: 0.35rem 0 0;
  color: var(--muted);
  line-height: 1.65;
}
.ritual-history time {
  color: var(--primary);
  font: 1rem var(--font-serif);
}
.history-empty {
  color: var(--muted);
}
@keyframes ritual-dim {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}
@keyframes ritual-float {
  50% {
    transform: translateY(-16px) scale(var(--orb-scale));
    opacity: 0.42;
  }
}
@keyframes ritual-bloom {
  0% {
    transform: scale(0.45);
    opacity: 0;
  }
  65% {
    transform: scale(1.15);
  }
  100% {
    transform: scale(1);
    opacity: 1;
  }
}
@media (max-width: 820px) {
  .ritual-intro,
  .ritual-history {
    display: grid;
    grid-template-columns: 1fr;
  }
  .action-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .ritual-memory-card {
    grid-template-columns: 1fr;
  }
  .ritual-memory-card img {
    min-height: 220px;
    max-height: 310px;
  }
  .ritual-history li {
    grid-template-columns: 1fr;
  }
  .ritual-history li p {
    grid-column: auto;
  }
}
@media (max-width: 520px) {
  .action-grid {
    grid-template-columns: 1fr;
  }
  .ritual-stage {
    padding: 24px 16px;
  }
  .ritual-progress small {
    display: none;
  }
}
@media (prefers-reduced-motion: reduce) {
  .ritual-page,
  .ritual-links a,
  .ritual-page.is-in-ritual > :not(.ritual-stage) {
    transition: none;
  }
  .ritual-page.is-in-ritual::before,
  .ritual-light i,
  .bloom {
    animation: none;
  }
}
</style>
