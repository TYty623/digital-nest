<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ApiRequestError } from '@/api/client'
import {
  addTribute,
  lightMemorial,
  publicMemorial,
  reportTribute,
  getPublicMemorialExperience,
  unlockMemorial,
  type GalleryItem,
  type Memorial,
  type MemorialLetter,
  type TimelineEntry,
  type Tribute,
  type MemorialExperience,
} from '@/api/memorials'
import { themeClass, themeStyleVars } from '@/config/memorialThemes'
import { memoryInterviewLabel } from '@/config/memoryInterview'

const route = useRoute()
const memorial = ref<Memorial | null>(null)
const tributes = ref<Tribute[]>([])
const timeline = ref<TimelineEntry[]>([])
const letter = ref<MemorialLetter | null>(null)
const gallery = ref<GalleryItem[]>([])
const experience = ref<MemorialExperience>({ dayMoments: [], lifeDetails: [], sounds: [], interviewAnswers: [], keepsakes: [], burial: null })
const activeMomentId = ref('')
const loading = ref(true)
const errorMessage = ref('')
const authorName = ref('')
const message = ref('')
const sending = ref(false)
const tributeNotice = ref('')
const locked = ref(false)
const accessCode = ref('')
const unlocking = ref(false)
const reportingId = ref<string | null>(null)
const lightCount = ref(0)
const lighting = ref(false)
const lit = ref(false)
const coverUnavailable = ref(false)
const imageDialog = ref<HTMLDialogElement | null>(null)
const selectedPhoto = ref(0)
const photos = computed(() => gallery.value.filter((item) => !isVideo(item)))
const activePhoto = computed(() => photos.value[selectedPhoto.value])
const activeDayMoment = computed(() =>
  experience.value.dayMoments.find((item) => item.id === activeMomentId.value) ?? experience.value.dayMoments[0],
)
const dayPhase = computed(() => {
  const hour = Number(activeDayMoment.value?.momentTime.slice(0, 2) ?? 12)
  if (hour < 9) return 'morning'
  if (hour < 17) return 'afternoon'
  if (hour < 21) return 'evening'
  return 'night'
})
const rememberedToday = computed(() => {
  const candidates = [
    ...experience.value.dayMoments.map((item) => ({ label: item.momentTime, text: item.story || item.title })),
    ...experience.value.lifeDetails.map((item) => ({ label: lifeDetailLabels[item.detailKey], text: item.answer })),
    ...experience.value.interviewAnswers.map((item) => ({ label: memoryInterviewLabel(item.promptKey), text: item.answer })),
  ].filter((item) => item.text)
  if (!candidates.length) return null
  const now = new Date()
  const dayIndex = Math.floor(Date.UTC(now.getFullYear(), now.getMonth(), now.getDate()) / 86400000)
  return candidates[dayIndex % candidates.length]
})
const heroDetail = computed(() => {
  const detail = experience.value.lifeDetails[0]
  if (detail) return { label: lifeDetailLabels[detail.detailKey] || '家人记得的事', text: detail.answer }
  const moment = experience.value.dayMoments[0]
  if (moment) return { label: moment.placeName || moment.momentTime, text: moment.story || moment.title }
  if (memorial.value?.aboutTa) return { label: '关于 TA', text: memorial.value.aboutTa }
  return { label: '先留下一件生活小事', text: memorial.value?.farewellMessage || '记住 TA 怎样生活过。' }
})
const entryIntents = [
  { label: '想安静看看', href: '#today-memory' },
  { label: '想写点什么', href: '#leave-a-greeting' },
  { label: '想和家人一起回忆', href: '#memory-album' },
  { label: '想做一次纪念仪式', href: '#ritual-action' },
] as const
function openPhoto(item: GalleryItem) {
  selectedPhoto.value = photos.value.findIndex((photo) => photo.id === item.id)
  imageDialog.value?.showModal()
}
function movePhoto(direction: number) {
  if (photos.value.length)
    selectedPhoto.value =
      (selectedPhoto.value + direction + photos.value.length) % photos.value.length
}

function validateCover(event: Event) {
  const image = event.currentTarget as HTMLImageElement
  coverUnavailable.value = image.naturalWidth < 32 || image.naturalHeight < 32
}

async function load() {
  loading.value = true
  memorial.value = null
  coverUnavailable.value = false
  locked.value = false
  errorMessage.value = ''
  try {
    const result = await publicMemorial(String(route.params.slug))
    memorial.value = result.memorial
    tributes.value = result.tributes
    timeline.value = result.timelineEntries
    letter.value = result.letter
    gallery.value = result.galleryItems
    lightCount.value = result.lightCount
    experience.value = await getPublicMemorialExperience(result.memorial.slug)
    activeMomentId.value = experience.value.dayMoments[0]?.id ?? ''
  } catch (error) {
    if (error instanceof ApiRequestError && error.code === 'MEMORIAL_ACCESS_CODE_REQUIRED') {
      locked.value = true
      return
    }
    errorMessage.value = error instanceof Error ? error.message : '这个纪念页暂时无法打开。'
  } finally {
    loading.value = false
  }
}

async function light() {
  if (!memorial.value || lit.value || lighting.value) return
  lighting.value = true
  errorMessage.value = ''
  try {
    const result = await lightMemorial(memorial.value.slug)
    lightCount.value = result.count
    lit.value = true
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '暂时无法点亮，请稍后再试。'
  } finally {
    lighting.value = false
  }
}

async function unlock() {
  unlocking.value = true
  errorMessage.value = ''
  try {
    await unlockMemorial(String(route.params.slug), accessCode.value)
    accessCode.value = ''
    await load()
  } catch (error) {
    locked.value = true
    errorMessage.value = error instanceof Error ? error.message : '暂时无法验证口令。'
  } finally {
    unlocking.value = false
  }
}

async function submitTribute() {
  if (!memorial.value) return
  sending.value = true
  errorMessage.value = ''
  tributeNotice.value = ''
  try {
    const tribute = await addTribute(memorial.value.slug, authorName.value, message.value)
    if (tribute.status === 'APPROVED') {
      tributes.value.push(tribute)
    } else {
      tributeNotice.value = '你的留言已提交，等待页面主人审核后显示。'
    }
    authorName.value = ''
    message.value = ''
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '留言没有送达，请稍后再试。'
  } finally {
    sending.value = false
  }
}

async function report(tribute: Tribute) {
  if (!memorial.value || !window.confirm('确定要举报这条留言吗？举报后它会立即从公开页隐藏。'))
    return
  reportingId.value = tribute.id
  errorMessage.value = ''
  try {
    await reportTribute(memorial.value.slug, tribute.id)
    tributes.value = tributes.value.filter((item) => item.id !== tribute.id)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '举报没有提交成功，请稍后再试。'
  } finally {
    reportingId.value = null
  }
}

function displayTimelineDate(entry: TimelineEntry) {
  if (!entry.eventDate || entry.datePrecision === 'UNKNOWN') return '记得那时'
  const [year, month, day] = entry.eventDate.split('-')
  if (entry.datePrecision === 'YEAR') return `${year} 年`
  if (entry.datePrecision === 'MONTH') return `${year} 年 ${Number(month)} 月`
  return `${year} 年 ${Number(month)} 月 ${Number(day)} 日`
}

function displayCompanionDate(value: string) {
  const [year, month, day] = value.split('-')
  return `${year} 年 ${Number(month)} 月 ${Number(day)} 日`
}

function isVideo(item: GalleryItem) {
  return item.contentType.startsWith('video/')
}

const lifeDetailLabels: Record<string, string> = {
  NICKNAMES: '家人才懂的昵称', FAVORITE_FOOD: '最喜欢的食物', FAVORITE_SPOT: '最常待的位置',
  QUIRK: '最有辨识度的小习惯', FEAR: '最害怕的声音或事情', COMES_RUNNING_FOR: '听见什么会立刻跑来',
  FAVORITE_PERSON: '最喜欢黏着谁', FAMILIAR_SOUND: '最熟悉的声音', SPECIAL_MARK: '身上最特别的记号',
  TAUGHT_ME: 'TA 教会我的事',
}
const burialLabels: Record<string, string> = {
  PROFESSIONAL_HARMLESS: '由专业机构妥善处理', CREMATION: '火化', ASHES_KEPT: '骨灰由家人留存',
  ASHES_PLACED: '骨灰已妥善安放', OTHER_LAWFUL: '其他合规方式',
}

onMounted(load)
</script>

<template>
  <section
    class="public-memorial section-narrow"
    :class="themeClass(memorial?.theme)"
    :style="themeStyleVars(memorial?.theme)"
  >
    <div v-if="loading" class="account-empty"><p>正在打开这间小窝…</p></div>
    <div v-else-if="locked" class="account-empty access-gate">
      <span aria-hidden="true">⌘</span>
      <h1>这间小窝有一把小锁。</h1>
      <p>请向分享链接的主人索取访问口令。</p>
      <form class="tribute-form" @submit.prevent="unlock">
        <label
          ><span>分享口令</span
          ><input
            v-model="accessCode"
            type="password"
            minlength="6"
            maxlength="64"
            required
            autocomplete="current-password"
        /></label>
        <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
        <button class="button" :disabled="unlocking">
          {{ unlocking ? '正在验证…' : '打开这间小窝' }}
        </button>
      </form>
    </div>
    <div v-else-if="!memorial" class="account-empty">
      <span aria-hidden="true">⌂</span>
      <h1>暂时没有找到这间小窝。</h1>
      <p>{{ errorMessage || '它可能尚未公开，或链接已经失效。' }}</p>
      <RouterLink class="button" to="/">回到数字小窝</RouterLink>
    </div>
    <template v-else>
      <article id="today-memory" class="public-card public-hero">
        <div class="public-cover">
          <img
            v-if="memorial.coverImageUrl && !coverUnavailable"
            :src="memorial.coverImageUrl"
            alt=""
            @load="validateCover"
            @error="coverUnavailable = true"
          />
          <span v-else aria-hidden="true">⌁</span>
        </div>
        <div class="public-copy">
          <p class="eyebrow">{{ memorial.species }} · 一处真实生活角落</p>
          <h1>{{ memorial.petName }}</h1>
          <div class="hero-life-detail"><span>{{ heroDetail.label }}</span><p>“{{ heroDetail.text }}”</p></div>
          <p
            v-if="memorial.companionStartedOn || memorial.companionEndedOn"
            class="companion-dates"
          >
            {{
              memorial.companionStartedOn
                ? displayCompanionDate(memorial.companionStartedOn)
                : '从相遇开始'
            }}
            -
            {{
              memorial.companionEndedOn
                ? displayCompanionDate(memorial.companionEndedOn)
                : '一直想念'
            }}
          </p>
          <div v-if="memorial.farewellMessage" class="public-rule"></div>
          <blockquote v-if="memorial.farewellMessage">{{ `“${memorial.farewellMessage}”` }}</blockquote>
          <p class="public-date">
            由 TA 的家人保存 · {{ new Date(memorial.publishedAt || memorial.createdAt).toLocaleDateString('zh-CN') }}
          </p>
        </div>
      </article>
      <nav class="public-intents" aria-label="此刻想怎样进入这份纪念">
        <span>此刻想怎样进入？</span><a v-for="intent in entryIntents" :key="intent.label" :href="intent.href">{{ intent.label }} <i aria-hidden="true">→</i></a>
      </nav>
      <section v-if="experience.dayMoments.length" class="public-day" :class="`phase-${dayPhase}`">
        <div class="public-day-heading"><div><p class="eyebrow">{{ memorial.petName }} 的一天</p><h2>记住 TA 怎样生活过</h2></div><p>从清晨到夜晚，点开一段最普通、也最舍不得忘记的日常。</p></div>
        <div class="public-day-track" role="tablist" aria-label="一天中的生活片段"><button v-for="item in experience.dayMoments" :key="item.id" type="button" role="tab" :aria-selected="activeDayMoment?.id === item.id" :class="{ active: activeDayMoment?.id === item.id }" @click="activeMomentId = item.id"><time>{{ item.momentTime }}</time><span>{{ item.title }}</span></button></div>
        <Transition name="memory-scene" mode="out-in"><article v-if="activeDayMoment" :key="activeDayMoment.id" class="day-scene"><img v-if="activeDayMoment.mediaUrl" :src="activeDayMoment.mediaUrl" :alt="activeDayMoment.title" /><div><p class="day-place">{{ activeDayMoment.placeName || '熟悉的日常' }}</p><h3>{{ activeDayMoment.title }}</h3><p v-if="activeDayMoment.story">{{ activeDayMoment.story }}</p></div></article></Transition>
      </section>
      <section v-if="experience.lifeDetails.length" class="public-life"><div><p class="eyebrow">生命指纹</p><h2>这是独一无二的 {{ memorial.petName }}</h2><p>不是标签或测试，而是家人亲自留下的生活细节。</p></div><dl><div v-for="detail in experience.lifeDetails" :key="detail.id"><dt>{{ lifeDetailLabels[detail.detailKey] || detail.detailKey }}</dt><dd>{{ detail.answer }}</dd></div></dl></section>
      <section v-if="rememberedToday" class="remembered-today"><span aria-hidden="true">✦</span><div><p class="eyebrow">今天想起这件小事</p><blockquote>“{{ rememberedToday.text }}”</blockquote><small>{{ rememberedToday.label }}</small></div></section>
      <section v-if="memorial.aboutTa && heroDetail.label !== '关于 TA'" class="public-about">
        <p class="eyebrow">关于 TA</p>
        <h2>{{ memorial.petName }} 是怎样生活过的</h2>
        <p>{{ memorial.aboutTa }}</p>
      </section>

      <section v-if="gallery.length" id="memory-album" class="public-gallery">
        <p class="eyebrow">TA 的相册</p>
        <h2>还想再看看这些瞬间</h2>
        <div>
          <figure v-for="item in gallery" :key="item.id">
            <video
              v-if="isVideo(item)"
              :src="item.mediaUrl"
              controls
              playsinline
              preload="metadata"
              :aria-label="item.caption || `${memorial.petName} 的短视频`"
            ></video
            ><button
              v-else
              class="gallery-photo-button"
              type="button"
              :aria-label="`放大查看：${item.caption || memorial.petName + ' 的照片'}`"
              @click="openPhoto(item)"
            >
              <img
                :src="item.mediaUrl"
                loading="lazy"
                :alt="item.caption || `${memorial.petName} 的照片`"
              />
            </button>
            <figcaption v-if="item.caption">{{ item.caption }}</figcaption>
          </figure>
        </div>
      </section>
      <section v-if="experience.sounds.length || experience.keepsakes.length || experience.interviewAnswers.length" class="public-room">
        <p class="eyebrow">{{ memorial.petName }} 的纪念小屋</p>
        <h2>从一件熟悉的小事，再靠近 TA 一点</h2>
        <div v-if="experience.sounds.length" class="public-room-group"><h3>声音记忆盒</h3><div class="room-public-grid"><article v-for="sound in experience.sounds" :key="sound.id"><strong>{{ sound.title }}</strong><p v-if="sound.story">{{ sound.story }}</p><audio controls preload="metadata" :src="sound.mediaUrl"></audio></article></div></div>
        <div v-if="experience.keepsakes.length" class="public-room-group"><h3>旧物故事</h3><div class="room-public-grid"><article v-for="item in experience.keepsakes" :key="item.id"><strong>{{ item.title }}</strong><p>{{ item.story }}</p></article></div></div>
        <div v-if="experience.interviewAnswers.length" class="public-room-group"><h3>家人的回忆采访</h3><div class="room-public-grid"><article v-for="answer in experience.interviewAnswers" :key="answer.id"><span>{{ memoryInterviewLabel(answer.promptKey) }}</span><p>{{ answer.answer }}</p></article></div></div>
      </section>
      <section v-if="experience.burial" class="public-burial">
        <p class="eyebrow">安葬与归处 · 家人记录</p><h2>TA 被温柔地安放</h2>
        <p><strong>{{ burialLabels[experience.burial.dispositionType] }}</strong><span v-if="experience.burial.region"> · {{ experience.burial.region }}</span><span v-if="experience.burial.placeName"> · {{ experience.burial.placeName }}</span></p>
        <p v-if="experience.burial.remembranceText">{{ experience.burial.remembranceText }}</p>
        <small>平台审核仅表示内容符合公开展示规则，不构成对线下机构资质或处置合法性的认证。</small>
      </section>
      <section v-if="timeline.length" class="public-timeline">
        <p class="eyebrow">TA 的时间线</p>
        <h2>那些值得记得的日子</h2>
        <ol>
          <li v-for="entry in timeline" :key="entry.id">
            <p>{{ displayTimelineDate(entry) }}</p>
            <h3>{{ entry.title }}</h3>
            <p v-if="entry.body">{{ entry.body }}</p>
            <img
              v-if="entry.mediaId"
              class="public-timeline-image"
              :src="`/api/v1/media/${entry.mediaId}/content`"
              :alt="`${entry.title} 关联的照片`"
            />
          </li>
        </ol>
      </section>
      <section id="ritual-action" class="light-panel" :class="{ 'is-lit': lit }" aria-label="为纪念页点亮">
        <div class="light-burst" aria-hidden="true"><i></i><i></i><i></i><i></i><i></i><i></i></div>
        <p class="eyebrow">一盏小灯 · 纪念动作</p>
        <h2>为 {{ memorial.petName }} 留一束温柔的光</h2>
        <p>已有 {{ lightCount }} 盏灯静静亮着。这个动作不需要被分享，也不用被记分。</p>
        <button
          class="button light-button"
          type="button"
          :disabled="lighting || lit"
          @click="light"
        >
          {{ lighting ? '正在点亮…' : lit ? '今天已点亮' : '点亮一盏灯' }}
        </button>
      </section>
      <section v-if="letter" class="public-letter">
        <p class="eyebrow">写给 TA 的信</p>
        <h2>{{ letter.subject || '有些话，想慢慢告诉你。' }}</h2>
        <p>{{ letter.body }}</p>
      </section>
      <section id="leave-a-greeting" class="tribute-section">
        <div>
          <p class="eyebrow">亲友留言</p>
          <h2>把想说的话，留在这里。</h2>
        </div>
        <div class="tribute-list">
          <article v-for="tribute in tributes" :key="tribute.id" class="tribute-card">
            <strong>{{ tribute.authorName }}</strong>
            <p>{{ tribute.message }}</p>
            <button
              class="tribute-report"
              type="button"
              :disabled="reportingId === tribute.id"
              @click="report(tribute)"
            >
              {{ reportingId === tribute.id ? '正在处理…' : '举报' }}
            </button>
          </article>
          <p v-if="!tributes.length" class="empty-tributes">第一句想念，正在等你留下。</p>
        </div>
        <form class="tribute-form" @submit.prevent="submitTribute">
          <p class="field-hint">
            请勿发布违法、暴力、诈骗或侵犯他人隐私的内容；高风险留言会先交由页面主人审核。
          </p>
          <label
            ><span>你的称呼</span
            ><input
              v-model="authorName"
              maxlength="32"
              required
              placeholder="例如：小麦的姨姨" /></label
          ><label
            ><span>想对 TA 说的话</span
            ><textarea
              v-model="message"
              maxlength="280"
              rows="4"
              required
              placeholder="写下此刻最想说的一句话。"
            ></textarea>
          </label>
          <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
          <p v-if="tributeNotice" class="field-hint" role="status">{{ tributeNotice }}</p>
          <button class="button" :disabled="sending">
            {{ sending ? '正在送达…' : '留下这句话' }}
          </button>
        </form>
      </section>
      <dialog
        ref="imageDialog"
        class="photo-dialog"
        aria-label="相册大图"
        @keydown.left.prevent="movePhoto(-1)"
        @keydown.right.prevent="movePhoto(1)"
        @click="$event.target === imageDialog && imageDialog?.close()"
      >
        <div class="photo-dialog-toolbar">
          <span>{{ selectedPhoto + 1 }} / {{ photos.length }}</span
          ><button class="button button-quiet" type="button" @click="imageDialog?.close()">
            关闭
          </button>
        </div>
        <img
          v-if="activePhoto"
          :src="activePhoto.mediaUrl"
          :alt="activePhoto.caption || memorial.petName + ' 的照片'"
        />
        <p v-if="activePhoto?.caption">{{ activePhoto.caption }}</p>
        <div class="photo-dialog-toolbar">
          <button class="button button-quiet" :disabled="photos.length < 2" @click="movePhoto(-1)">
            ← 上一张</button
          ><button class="button button-quiet" :disabled="photos.length < 2" @click="movePhoto(1)">
            下一张 →
          </button>
        </div>
      </dialog>
    </template>
  </section>
</template>

<style scoped>
.public-hero{min-height:min(760px,calc(100dvh - 150px));isolation:isolate}.hero-life-detail{max-width:38rem;margin:1.1rem 0 1.4rem;padding:.95rem 0;border-top:1px solid color-mix(in srgb,var(--primary) 38%,var(--border));border-bottom:1px solid color-mix(in srgb,var(--primary) 20%,var(--border))}.hero-life-detail span{color:var(--primary);font-size:.75rem;letter-spacing:.08em}.hero-life-detail p{display:-webkit-box;overflow:hidden;margin:.4rem 0 0;font-family:var(--font-serif);font-size:clamp(18px,2vw,25px);line-height:1.7;-webkit-box-orient:vertical;-webkit-line-clamp:3}.public-intents{display:flex;flex-wrap:wrap;gap:.6rem 1.2rem;align-items:center;max-width:1080px;margin:18px auto 0;padding:0 4px;color:var(--muted);font-size:.84rem}.public-intents a{color:inherit;text-decoration:none}.public-intents a:hover,.public-intents a:focus-visible{color:var(--primary)}.public-intents i{color:var(--primary);font-style:normal}.public-memorial.theme-night .public-hero{grid-template-columns:minmax(0,1.14fr) minmax(290px,.86fr);border-radius:2px 2px 38px 2px}.public-memorial.theme-night .public-hero .public-cover{min-height:0}.public-memorial.theme-night .public-hero .public-copy{position:relative}.public-memorial.theme-night .public-hero .public-copy::before{position:absolute;top:10%;right:14%;width:7px;aspect-ratio:1;border-radius:50%;background:var(--primary);box-shadow:0 0 28px var(--primary);content:'';opacity:.5}.public-memorial.theme-sunny .public-hero{grid-template-columns:minmax(250px,.78fr) minmax(0,1.22fr);gap:0;border-radius:0;background:linear-gradient(115deg,var(--surface-soft),var(--surface))}.public-memorial.theme-sunny .public-hero .public-cover{min-height:0;margin:clamp(18px,3vw,42px);background:var(--surface-soft)}.public-memorial.theme-sunny .public-hero .public-copy{align-items:flex-start;padding-left:clamp(26px,6vw,88px)}.public-memorial.theme-sunny .public-hero .public-copy h1{color:var(--primary)}.public-memorial.theme-sunny .public-hero .hero-life-detail{border-style:dashed}.public-memorial.theme-garden .public-hero{display:grid;grid-template-columns:1fr;min-height:0;border-radius:44% 2% 32% 2%;background:radial-gradient(ellipse at 85% 6%,color-mix(in srgb,var(--primary) 22%,var(--surface)),transparent 30%),var(--surface)}.public-memorial.theme-garden .public-hero .public-cover{min-height:clamp(310px,43vw,510px);margin:clamp(18px,3vw,42px) clamp(18px,6vw,90px) 0;border-radius:42% 42% 4% 4%;background:color-mix(in srgb,var(--primary) 18%,var(--surface-soft))}.public-memorial.theme-garden .public-hero .public-copy{display:grid;grid-template-columns:minmax(0,1fr) minmax(170px,.44fr);column-gap:2rem;align-items:end;padding:clamp(26px,5vw,68px) clamp(30px,9vw,140px)}.public-memorial.theme-garden .public-hero .public-copy>.eyebrow,.public-memorial.theme-garden .public-hero .public-copy>h1,.public-memorial.theme-garden .public-hero .public-copy>.hero-life-detail{grid-column:1}.public-memorial.theme-garden .public-hero .companion-dates{grid-column:2;grid-row:1;align-self:end}.public-memorial.theme-garden .public-hero .public-rule,.public-memorial.theme-garden .public-hero blockquote,.public-memorial.theme-garden .public-hero .public-date{grid-column:2}.public-memorial.theme-garden .public-hero .hero-life-detail{border-radius:0 20px 0 20px;background:color-mix(in srgb,var(--primary) 9%,var(--surface));padding:1.1rem}
.public-day{--day-glow:#f2c879;max-width:1080px;margin:48px auto 0;padding:clamp(24px,5vw,56px);border:1px solid color-mix(in srgb,var(--day-glow) 40%,var(--border));border-radius:28px;background:radial-gradient(circle at 82% 8%,color-mix(in srgb,var(--day-glow) 22%,transparent),transparent 34%),var(--surface);transition:background .35s ease}.public-day.phase-morning{--day-glow:#f5cf91}.public-day.phase-afternoon{--day-glow:#dfb65c}.public-day.phase-evening{--day-glow:#d38166}.public-day.phase-night{--day-glow:#778cc8}.public-day-heading{display:flex;justify-content:space-between;gap:2rem;align-items:end}.public-day-heading h2{margin:.4rem 0 0;font-size:clamp(30px,5vw,52px)}.public-day-heading>p{max-width:430px;color:var(--muted);line-height:1.8}.public-day-track{display:flex;gap:.6rem;overflow-x:auto;margin:2rem 0;padding-bottom:.6rem}.public-day-track button{display:grid;flex:0 0 150px;gap:.35rem;padding:1rem;text-align:left;border:1px solid var(--border);border-radius:12px;background:color-mix(in srgb,var(--canvas) 70%,transparent);color:var(--text)}.public-day-track button.active{border-color:var(--day-glow);box-shadow:inset 0 0 0 1px var(--day-glow)}.public-day-track time{font-family:var(--font-serif);font-size:1.35rem;color:var(--day-glow)}.day-scene{display:grid;grid-template-columns:minmax(0,1.1fr) minmax(260px,.9fr);min-height:300px;overflow:hidden;border-radius:20px;background:color-mix(in srgb,var(--canvas) 74%,transparent)}.day-scene img{width:100%;height:100%;max-height:430px;object-fit:cover}.day-scene>div{display:flex;flex-direction:column;justify-content:center;padding:clamp(24px,5vw,52px)}.day-scene h3{margin:.5rem 0 1rem;font-size:clamp(26px,4vw,42px)}.day-scene p{white-space:pre-wrap;line-height:1.8}.day-place{color:var(--day-glow)}
.memory-scene-enter-active,.memory-scene-leave-active{transition:opacity .3s ease,transform .3s ease}.memory-scene-enter-from{opacity:0;transform:translateX(14px)}.memory-scene-leave-to{opacity:0;transform:translateX(-10px)}
.public-life{display:grid;grid-template-columns:minmax(240px,.72fr) minmax(0,1.28fr);gap:clamp(30px,6vw,80px);max-width:1080px;margin:48px auto 0;padding:clamp(24px,5vw,52px);border-top:1px solid var(--border);border-bottom:1px solid var(--border)}.public-life h2{margin:.5rem 0 1rem;font-size:clamp(30px,4vw,48px)}.public-life>div>p:last-child{color:var(--muted);line-height:1.8}.public-life dl{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:1px;margin:0;background:var(--border)}.public-life dl>div{padding:1.15rem;background:var(--canvas)}.public-life dt{margin-bottom:.45rem;color:var(--muted);font-size:.88rem}.public-life dd{margin:0;line-height:1.65}.remembered-today{display:flex;gap:1.2rem;max-width:760px;margin:48px auto 0;padding:28px;border-left:3px solid var(--primary);background:var(--surface)}.remembered-today>span{font-size:1.5rem;color:var(--primary)}.remembered-today blockquote{margin:.35rem 0 .7rem;font-family:var(--font-serif);font-size:clamp(22px,3vw,30px);line-height:1.6}.remembered-today small{color:var(--muted)}
.public-room,.public-burial{max-width:960px;margin:48px auto 0;padding:clamp(24px,4vw,44px);border:1px solid var(--border);border-radius:22px;background:var(--surface)}
.public-room>h2,.public-burial>h2{margin:8px 0 28px;font-size:clamp(28px,4vw,42px)}
.public-room-group{margin-top:28px}.public-room-group>h3{margin-bottom:12px}.room-public-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px}.room-public-grid article{padding:18px;border:1px solid var(--border);border-radius:14px}.room-public-grid p{white-space:pre-wrap}.room-public-grid audio{width:100%;margin-top:10px}.room-public-grid span,.public-burial small{color:var(--muted)}.public-burial>p{white-space:pre-wrap;line-height:1.8}.public-burial small{display:block;margin-top:20px;line-height:1.6}
@media(max-width:640px){.room-public-grid,.public-life,.day-scene{grid-template-columns:1fr}.public-day-heading{display:grid}.public-life dl{grid-template-columns:1fr}.day-scene img{max-height:260px}.public-day-track button{flex-basis:132px}.public-hero,.public-memorial.theme-night .public-hero,.public-memorial.theme-sunny .public-hero,.public-memorial.theme-garden .public-hero{display:grid;grid-template-columns:1fr;min-height:0;border-radius:18px}.public-memorial.theme-sunny .public-hero .public-cover,.public-memorial.theme-garden .public-hero .public-cover{min-height:300px;margin:0}.public-memorial.theme-garden .public-hero .public-copy{display:flex;padding:30px 24px}.public-memorial.theme-garden .public-hero .companion-dates{align-self:auto}.public-intents{display:grid;gap:.6rem;margin-top:14px}.public-intents span{font-size:.76rem}}
.gallery-photo-button {
  display: block;
  width: 100%;
  padding: 0;
  border: 0;
  border-radius: 12px;
  background: transparent;
  cursor: zoom-in;
}
.photo-dialog {
  width: min(960px, 94vw);
  max-height: 94dvh;
  padding: 22px;
  border: 1px solid var(--border);
  border-radius: 14px;
  background: var(--canvas);
  color: var(--text);
}
.photo-dialog::backdrop {
  background: rgb(0 0 0 / 80%);
}
.photo-dialog > img {
  display: block;
  width: 100%;
  max-height: 65dvh;
  object-fit: contain;
}
.photo-dialog-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin: 10px 0;
}
.light-panel {
  position: relative;
  overflow: hidden;
  margin: 48px auto 0;
  max-width: 760px;
  padding: 36px;
  border: 1px solid var(--border);
  border-radius: 22px;
  background: linear-gradient(135deg, color-mix(in srgb, var(--primary) 12%, var(--surface)), var(--surface));
  text-align: center;
}
.light-burst{position:absolute;inset:50%;pointer-events:none}.light-burst i{position:absolute;width:3px;height:3px;border-radius:50%;background:var(--primary);opacity:0}.light-panel.is-lit .light-burst i{animation:light-burst 1.15s ease-out both}.light-panel.is-lit .light-burst i:nth-child(1){--x:-125px;--y:-70px}.light-panel.is-lit .light-burst i:nth-child(2){--x:-76px;--y:-118px;animation-delay:.06s}.light-panel.is-lit .light-burst i:nth-child(3){--x:105px;--y:-88px;animation-delay:.12s}.light-panel.is-lit .light-burst i:nth-child(4){--x:132px;--y:38px;animation-delay:.03s}.light-panel.is-lit .light-burst i:nth-child(5){--x:-112px;--y:72px;animation-delay:.09s}.light-panel.is-lit .light-burst i:nth-child(6){--x:48px;--y:112px;animation-delay:.15s}@keyframes light-burst{0%{opacity:0;transform:translate(0,0) scale(.4)}28%{opacity:1}100%{opacity:0;transform:translate(var(--x),var(--y)) scale(1.8);box-shadow:0 0 16px var(--primary)}}
.public-about {
  max-width: 760px;
  margin: 48px auto 0;
  padding: 36px;
  border-left: 5px solid var(--primary);
  background: var(--surface);
}
.public-about h2 {
  margin: 8px 0 18px;
  font-size: clamp(28px, 4vw, 40px);
}
.public-about p:not(.eyebrow) {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.9;
}
.light-panel h2 {
  margin: 8px 0 10px;
  font-size: clamp(29px, 4vw, 42px);
}
.light-panel p:not(.eyebrow) {
  margin-bottom: 22px;
}
.light-button {
  min-width: 152px;
}

.public-cover {
  position: relative;
  overflow: hidden;
}
.public-cover img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.public-memorial.theme-night .public-cover {
  background:
    radial-gradient(circle at 70% 16%, color-mix(in srgb, var(--primary) 66%, var(--surface)) 0 2%, transparent 3%),
    radial-gradient(circle at 18% 31%, color-mix(in srgb, var(--muted) 64%, var(--surface)) 0 1%, transparent 2%),
    linear-gradient(145deg, var(--surface-soft), var(--canvas));
}
.public-memorial.theme-night .public-card,
.public-memorial.theme-night .tribute-card {
  box-shadow: 0 20px 50px rgb(0 0 0 / 24%);
}
.public-memorial.theme-night .light-panel {
  background: linear-gradient(135deg, var(--surface-soft), var(--canvas));
}

.public-memorial.theme-garden .public-cover {
  background:
    radial-gradient(ellipse at 20% 86%, color-mix(in srgb, var(--primary) 34%, var(--surface)) 0 13%, transparent 14%),
    radial-gradient(ellipse at 78% 73%, color-mix(in srgb, var(--surface-soft) 78%, var(--surface)) 0 16%, transparent 17%),
    linear-gradient(145deg, var(--surface-soft), color-mix(in srgb, var(--primary) 44%, var(--surface)));
}
.public-memorial.theme-garden .light-panel {
  background: linear-gradient(135deg, var(--surface), var(--surface-soft));
}
.public-memorial.theme-meadow .public-hero{grid-template-columns:minmax(0,1.35fr) minmax(260px,.65fr);min-height:0;border-radius:0;background:linear-gradient(180deg,color-mix(in srgb,var(--primary) 10%,var(--surface)),var(--surface))}.public-memorial.theme-meadow .public-cover{min-height:clamp(300px,42vw,510px);margin:clamp(16px,3vw,34px) 0 0 clamp(16px,3vw,34px);border-radius:4px 42px 4px 4px}.public-memorial.theme-meadow .public-copy{align-items:flex-start}.public-memorial.theme-album .public-hero{min-height:0;margin-top:clamp(14px,4vw,42px);padding:clamp(14px,3vw,34px);border:0;background:repeating-linear-gradient(0deg,color-mix(in srgb,var(--primary) 4%,transparent) 0 1px,transparent 1px 4px),var(--surface)}.public-memorial.theme-album .public-cover{min-height:380px;transform:rotate(-1.2deg);box-shadow:0 12px 24px rgb(61 53 44 / 18%)}.public-memorial.theme-album .public-copy{margin:18px 0 0;padding:clamp(24px,5vw,60px);background:var(--surface);transform:rotate(.6deg)}.public-memorial.theme-home .public-hero{grid-template-columns:minmax(260px,.88fr) minmax(0,1.12fr);min-height:0;border-radius:8px;background:radial-gradient(circle at 15% 17%,color-mix(in srgb,var(--primary) 25%,transparent),transparent 20%),var(--surface)}.public-memorial.theme-home .public-cover{min-height:440px;margin:clamp(18px,3vw,42px) 0 clamp(18px,3vw,42px) clamp(18px,3vw,42px);border-radius:8px 8px 46px 8px;box-shadow:0 14px 30px rgb(0 0 0 / 26%)}.public-memorial.theme-home .public-copy{align-items:flex-start}.public-memorial.theme-album .public-gallery figure{transform:rotate(var(--album-angle,0deg))}.public-memorial.theme-album .public-gallery figure:nth-child(odd){--album-angle:-1deg}.public-memorial.theme-home .light-panel{background:radial-gradient(circle at 50% 8%,color-mix(in srgb,var(--primary) 20%,transparent),transparent 36%),var(--surface-soft)}
@media(prefers-reduced-motion:reduce){.memory-scene-enter-active,.memory-scene-leave-active{transition:none}.light-panel.is-lit .light-burst i{animation:none}}
</style>
