<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  addDayMoment, addKeepsake, addSoundMemory, deleteDayMoment, deleteKeepsake, deleteSoundMemory,
  getMemorial, getMemorialExperience, listGallery, saveBurialRecord, saveInterviewAnswer,
  saveLifeDetail, submitBurialRecord, updateDayMoment, uploadAudio,
  type BurialRecord, type DayMoment, type GalleryItem, type Memorial, type MemorialExperience,
} from '@/api/memorials'
import { memoryInterviewLabel, memoryInterviewPrompts } from '@/config/memoryInterview'
import { themeClass, themeStyleVars } from '@/config/memorialThemes'

const route = useRoute()
const id = computed(() => String(route.params.id))
const memorial = ref<Memorial | null>(null)
const experience = ref<MemorialExperience>({ dayMoments: [], lifeDetails: [], sounds: [], interviewAnswers: [], keepsakes: [], burial: null })
const gallery = ref<GalleryItem[]>([])
const loading = ref(true)
const busy = ref('')
const notice = ref('')
const error = ref('')
const audioProgress = ref(0)
const roomMode = ref<'SCENE' | 'LIST'>('SCENE')
const memoryIntent = ref<'LOOK' | 'RECORD' | 'FAMILY' | 'RITUAL'>('LOOK')
const memoryIntents = [
  { id: 'LOOK', label: '我想静静看看', note: '不需要完成任何事，先在熟悉的物件之间停一会儿。', href: '#room-space' },
  { id: 'RECORD', label: '我想记录一件小事', note: '今天只补一段动作、一种声音或一个位置就够了。', href: '#room-interview' },
  { id: 'FAMILY', label: '我想和家人一起补充', note: '把链接发给亲友前，先决定哪些内容愿意分享。', href: '#room-interview' },
  { id: 'RITUAL', label: '我想做一次纪念仪式', note: '仪式有开始和收束，不要求持续停留或分享。', href: '' },
] as const
const activeMemoryIntent = computed(() => memoryIntents.find((item) => item.id === memoryIntent.value) ?? memoryIntents[0])
const activeInterviewKey = ref(memoryInterviewPrompts[0]!.key)
const sound = reactive({ file: null as File | null, title: '', story: '' })
const dayMoment = reactive({ momentTime: '08:00', title: '', placeName: '', story: '', mediaId: '' })
const editingMomentId = ref('')
const keepsake = reactive({ title: '', story: '' })
const interviews = reactive<Record<string, string>>({})
const lifeDetails = reactive<Record<string, string>>({})
const burial = reactive({ dispositionType: 'CREMATION' as BurialRecord['dispositionType'], occurredOn: '', region: '', placeName: '', remembranceText: '' })
const lifeFields = [
  ['NICKNAMES', '家人才懂的昵称', '例如：小宝、煤球、主任'],
  ['FAVORITE_FOOD', '最喜欢的食物', '一听见包装袋就跑来的那一种'],
  ['FAVORITE_SPOT', '最常待的位置', '例如：阳台最左边的光斑'],
  ['QUIRK', '最有辨识度的小习惯', '这是公开社区优先展示的一句话'],
  ['FEAR', '最害怕的声音或事情', '例如：吹风机、打雷'],
  ['COMES_RUNNING_FOR', '听见什么会立刻跑来', '钥匙声、开罐头、叫某个昵称'],
  ['FAVORITE_PERSON', '最喜欢黏着谁', '可以只写称呼，不必写真名'],
  ['FAMILIAR_SOUND', '最熟悉的声音', '呼噜、铃铛、脚步或叫声'],
  ['SPECIAL_MARK', '身上最特别的记号', '花纹、耳朵、尾巴或一颗小痣'],
  ['TAUGHT_ME', 'TA 教会我的事', '一句最想长久留下的话'],
] as const
const galleryPhotos = computed(() => gallery.value.filter((item) => item.contentType.startsWith('image/')))
const allInterviewPrompts = computed(() => {
  const configured = [...memoryInterviewPrompts]
  const configuredKeys = new Set(configured.map((prompt) => prompt.key))
  const legacy = experience.value.interviewAnswers
    .filter((answer) => !configuredKeys.has(answer.promptKey))
    .map((answer) => ({ key: answer.promptKey, label: '已经收好的问题', question: memoryInterviewLabel(answer.promptKey), helper: '这是之前保存的回忆，仍会完整保留。', acceptedNow: ['TEXT'] as const }))
  return [...configured, ...legacy]
})
const activeInterviewIndex = computed(() => Math.max(0, allInterviewPrompts.value.findIndex((prompt) => prompt.key === activeInterviewKey.value)))
const activeInterviewPrompt = computed(() => allInterviewPrompts.value[activeInterviewIndex.value] ?? allInterviewPrompts.value[0])
const dispositions = [
  ['PROFESSIONAL_HARMLESS', '由专业机构妥善处理'], ['CREMATION', '火化'], ['ASHES_KEPT', '骨灰由家人留存'],
  ['ASHES_PLACED', '骨灰已妥善安放'], ['OTHER_LAWFUL', '其他合规方式'],
] as const
const statusText: Record<string, string> = { PRIVATE: '仅自己可见', PENDING: '等待平台审核', APPROVED: '已审核公开', REJECTED: '已退回，可修改后重提' }
const memoryMilestones = computed(() => [
  { label: '一天有 3 个时刻', complete: experience.value.dayMoments.length >= 3 },
  { label: '留下 3 条生命指纹', complete: experience.value.lifeDetails.length >= 3 },
  { label: '收好 3 份影像', complete: gallery.value.length >= 3 },
  { label: '留住一种声音', complete: experience.value.sounds.length > 0 },
  { label: '回答一个回忆问题', complete: experience.value.interviewAnswers.length > 0 },
  { label: '记下一件旧物', complete: experience.value.keepsakes.length > 0 },
])
const memoryProgress = computed(() =>
  Math.round((memoryMilestones.value.filter((item) => item.complete).length / memoryMilestones.value.length) * 100),
)
const nextMemoryStep = computed(() => {
  if (experience.value.dayMoments.length < 3) return { href: '#room-day', label: '再补一个生活时刻' }
  if (experience.value.lifeDetails.length < 3) return { href: '#room-life', label: '再留下一条生命指纹' }
  if (!experience.value.sounds.length) return { href: '#room-sound', label: '留住一种熟悉的声音' }
  if (!experience.value.interviewAnswers.length) return { href: '#room-interview', label: '回答一个回忆问题' }
  if (!experience.value.keepsakes.length) return { href: '#room-keepsake', label: '记下一件熟悉的旧物' }
  return { href: '#room-day', label: '继续丰富 TA 的一天' }
})
const roomObjects = computed(() => {
  const favouriteSpot = lifeDetails.FAVORITE_SPOT || experience.value.dayMoments.find((moment) => moment.placeName)?.placeName
  const quirk = lifeDetails.QUIRK || experience.value.lifeDetails.find((detail) => detail.detailKey === 'QUIRK')?.answer
  return [
    { id: 'frame', icon: '▣', label: '相框', title: galleryPhotos.value[0]?.caption || '一张真正像 TA 的照片', detail: galleryPhotos.value[0] ? '点开照片与那天的故事' : '哪一张照片最像 TA？', href: '#room-day', imageUrl: galleryPhotos.value[0]?.mediaUrl || '' },
    { id: 'window', icon: '▤', label: '窗户', title: favouriteSpot || 'TA 常待的位置', detail: favouriteSpot ? '这里记得它的日常' : '它最爱靠在哪里？', href: '#room-life', imageUrl: '' },
    { id: 'door', icon: '⌑', label: '门', title: experience.value.dayMoments[0]?.title || '散步、回家与迎接', detail: experience.value.dayMoments[0] ? '一段属于一天的时刻' : '它听见你回来会怎样？', href: '#room-day', imageUrl: '' },
    { id: 'bed', icon: '⌁', label: '小床', title: quirk || '安静时刻', detail: quirk ? '家人才知道的小习惯' : '它睡着后喜欢把头靠在哪里？', href: '#room-life', imageUrl: '' },
    { id: 'bowl', icon: '◡', label: '饭碗', title: lifeDetails.FAVORITE_FOOD || '喜欢的食物', detail: lifeDetails.FAVORITE_FOOD ? '一想到就会跑来的味道' : '什么声音会让它立刻跑来？', href: '#room-life', imageUrl: '' },
    { id: 'collar', icon: '⌇', label: '项圈', title: experience.value.sounds[0]?.title || '声音记忆', detail: experience.value.sounds[0] ? '这一小段声音被收好了' : '最先想起哪种声音？', href: '#room-sound', imageUrl: '' },
    { id: 'toy', icon: '✶', label: '玩具', title: experience.value.keepsakes[0]?.title || '一件旧物', detail: experience.value.keepsakes[0] ? '物件也有自己的故事' : '哪件东西还没有舍得收起？', href: '#room-keepsake', imageUrl: '' },
    { id: 'mailbox', icon: '✉', label: '信箱', title: experience.value.interviewAnswers[0] ? '家人的一段回答' : '一封还没写的信', detail: experience.value.interviewAnswers[0] ? '从一个问题重新想起' : '不想面对空白纸，就从问题开始', href: '#room-interview', imageUrl: '' },
    { id: 'lamp', icon: '✦', label: '灯', title: '一次纪念仪式', detail: '先回看一段真实记忆', href: `/editor/${id.value}/ritual`, imageUrl: '' },
    { id: 'calendar', icon: '□', label: '日历', title: '相遇日与时间胶囊', detail: '提醒始终由你决定是否开启', href: `/editor/${id.value}/calendar`, imageUrl: '' },
  ]
})

async function load() {
  loading.value = true
  try {
    ;[memorial.value, experience.value, gallery.value] = await Promise.all([
      getMemorial(id.value), getMemorialExperience(id.value), listGallery(id.value),
    ])
    for (const answer of experience.value.interviewAnswers) interviews[answer.promptKey] = answer.answer
    for (const detail of experience.value.lifeDetails) lifeDetails[detail.detailKey] = detail.answer
    if (experience.value.burial) Object.assign(burial, {
      dispositionType: experience.value.burial.dispositionType, occurredOn: experience.value.burial.occurredOn ?? '',
      region: experience.value.burial.region ?? '', placeName: experience.value.burial.placeName ?? '', remembranceText: experience.value.burial.remembranceText ?? '',
    })
  } catch (caught) { error.value = caught instanceof Error ? caught.message : '纪念小屋暂时无法打开。' }
  finally { loading.value = false }
}
function resetDayMoment() {
  editingMomentId.value = ''
  Object.assign(dayMoment, { momentTime: '08:00', title: '', placeName: '', story: '', mediaId: '' })
}
async function saveDayMoment() {
  await act('day-moment', async () => {
    const payload = {
      momentTime: dayMoment.momentTime, title: dayMoment.title.trim(),
      placeName: dayMoment.placeName.trim() || null, story: dayMoment.story.trim() || null,
      mediaId: dayMoment.mediaId || null,
    }
    const saved = editingMomentId.value
      ? await updateDayMoment(id.value, editingMomentId.value, payload)
      : await addDayMoment(id.value, payload)
    const others = experience.value.dayMoments.filter((item) => item.id !== saved.id)
    experience.value.dayMoments = [...others, saved].sort((a, b) => a.momentTime.localeCompare(b.momentTime))
    resetDayMoment()
  }, editingMomentId.value ? '这个生活片段已经更新。' : '这个生活片段已经放进 TA 的一天。')
}
function editDayMoment(item: DayMoment) {
  editingMomentId.value = item.id
  Object.assign(dayMoment, {
    momentTime: item.momentTime, title: item.title, placeName: item.placeName ?? '',
    story: item.story ?? '', mediaId: item.mediaId ?? '',
  })
  document.querySelector('#room-day')?.scrollIntoView({ behavior: 'smooth' })
}
async function removeDayMoment(momentId: string) {
  await act(`day-${momentId}`, async () => {
    await deleteDayMoment(id.value, momentId)
    experience.value.dayMoments = experience.value.dayMoments.filter((item) => item.id !== momentId)
    if (editingMomentId.value === momentId) resetDayMoment()
  }, '这个生活片段已经移除。')
}
async function saveLifeDetailField(key: string) {
  await act(`life-${key}`, async () => {
    experience.value = await saveLifeDetail(id.value, key, lifeDetails[key] ?? '')
  }, lifeDetails[key]?.trim() ? '这条生命指纹已经保存。' : '这条生命指纹已经清空。')
}
async function act(key: string, action: () => Promise<void>, success: string) {
  busy.value = key; error.value = ''; notice.value = ''
  try { await action(); notice.value = success } catch (caught) { error.value = caught instanceof Error ? caught.message : '操作没有完成。' }
  finally { busy.value = '' }
}
async function addSound() {
  if (!sound.file) { error.value = '请先选择 MP3 或 WAV 文件。'; return }
  await act('sound', async () => {
    const media = await uploadAudio(sound.file!, (p) => (audioProgress.value = p))
    experience.value.sounds.push(await addSoundMemory(id.value, media.id, sound.title, sound.story || null))
    sound.file = null; sound.title = ''; sound.story = ''; audioProgress.value = 0
  }, '这段声音已经放进记忆盒。')
}
async function removeSound(soundId: string) { await act(`sound-${soundId}`, async () => { await deleteSoundMemory(id.value, soundId); experience.value.sounds = experience.value.sounds.filter((x) => x.id !== soundId) }, '声音已移出记忆盒。') }
async function saveInterview(key: string) { await act(`interview-${key}`, async () => { experience.value = await saveInterviewAnswer(id.value, key, interviews[key] ?? '') }, '这段回忆已经保存。') }
async function createKeepsake() { await act('keepsake', async () => { experience.value.keepsakes.push(await addKeepsake(id.value, keepsake.title, keepsake.story)); keepsake.title = ''; keepsake.story = '' }, '旧物故事已经收好。') }
async function removeKeepsake(keepsakeId: string) { await act(`keepsake-${keepsakeId}`, async () => { await deleteKeepsake(id.value, keepsakeId); experience.value.keepsakes = experience.value.keepsakes.filter((x) => x.id !== keepsakeId) }, '旧物故事已删除。') }
async function saveBurial() { await act('burial', async () => { experience.value.burial = await saveBurialRecord(id.value, { ...burial, occurredOn: burial.occurredOn || null, region: burial.region || null, placeName: burial.placeName || null, remembranceText: burial.remembranceText || null }) }, '归处记录已私密保存。') }
async function submitBurial() {
  if (!window.confirm('提交后由人工审核是否适合公开展示；审核不代表对线下机构或处置资质的认证。继续吗？')) return
  await act('submit-burial', async () => { experience.value.burial = await submitBurialRecord(id.value) }, '已提交人工审核。')
}
function selectAudio(event: Event) { sound.file = (event.target as HTMLInputElement).files?.[0] ?? null }
function selectInterview(offset: number) {
  const prompts = allInterviewPrompts.value
  if (!prompts.length) return
  const nextIndex = (activeInterviewIndex.value + offset + prompts.length) % prompts.length
  activeInterviewKey.value = prompts[nextIndex]!.key
}
watch(allInterviewPrompts, (prompts) => {
  if (!prompts.some((prompt) => prompt.key === activeInterviewKey.value)) activeInterviewKey.value = prompts[0]?.key ?? ''
})
onMounted(load)
</script>

<template>
  <main class="room-page section-narrow" :class="themeClass(memorial?.theme)" :style="themeStyleVars(memorial?.theme)">
    <div v-if="loading" class="account-empty">正在打开纪念小屋…</div>
    <template v-else-if="memorial">
      <header class="room-hero">
        <div><p class="eyebrow">{{ memorial.petName }} 的纪念小屋</p><h1>把想念，安放在看得见的地方。</h1><p>六个入口都保存真实内容；你可以从最容易的一件事开始。</p></div>
        <RouterLink class="button secondary-button" :to="`/editor/${id}`">返回页面编辑</RouterLink>
      </header>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p><p v-if="notice" class="form-success" role="status">{{ notice }}</p>
      <section class="memory-intent-panel" aria-label="此刻想怎样纪念">
        <div><p class="eyebrow">此刻的选择</p><h2>先按你的方式靠近这份记忆。</h2><p>{{ activeMemoryIntent.note }}</p></div>
        <div class="memory-intent-options" role="group" aria-label="纪念意图">
          <button v-for="intent in memoryIntents" :key="intent.id" type="button" :class="{ active: memoryIntent === intent.id }" @click="memoryIntent = intent.id">{{ intent.label }}</button>
        </div>
        <RouterLink v-if="activeMemoryIntent.id === 'RITUAL'" class="text-link" :to="`/editor/${id}/ritual`">进入一次有收束的纪念仪式 →</RouterLink>
        <a v-else class="text-link" :href="activeMemoryIntent.href">{{ activeMemoryIntent.id === 'LOOK' ? '从场景慢慢看看 →' : '只完成这一件小事 →' }}</a>
        <button class="text-button" type="button" @click="memoryIntent = 'LOOK'">今天先不写</button>
      </section>
      <section id="room-space" class="room-space" aria-labelledby="room-space-title">
        <div class="room-space-heading"><div><p class="eyebrow">从物件走进一段真实记忆</p><h2 id="room-space-title">{{ memorial.petName }} 的生活，不是一张功能清单。</h2><p>每件物件都连着已经保存的内容；空着的地方只留一个温和的问题。</p></div><div class="room-mode-toggle" role="group" aria-label="纪念小屋浏览方式"><button type="button" :class="{ active: roomMode === 'SCENE' }" @click="roomMode = 'SCENE'">场景</button><button type="button" :class="{ active: roomMode === 'LIST' }" @click="roomMode = 'LIST'">列表模式</button></div></div>
        <div v-if="roomMode === 'SCENE'" class="room-scene" aria-label="可浏览的纪念小屋场景">
          <div class="scene-window-light" aria-hidden="true"></div><div class="scene-floor" aria-hidden="true"></div>
          <a v-for="object in roomObjects" :key="object.id" :href="object.href" :class="['room-object', `object-${object.id}`, { filled: object.imageUrl || object.title !== '一件旧物' && object.title !== '喜欢的食物' && object.title !== '声音记忆' && object.title !== '一封还没写的信' && object.title !== 'TA 常待的位置' && object.title !== '安静时刻' && object.title !== '散步、回家与迎接' }]">
            <span v-if="object.imageUrl" class="object-photo" :style="{ backgroundImage: `url(${object.imageUrl})` }"></span><i v-else aria-hidden="true">{{ object.icon }}</i><span class="object-copy"><small>{{ object.label }}</small><strong>{{ object.title }}</strong><em>{{ object.detail }}</em></span>
          </a>
          <p class="scene-note">点击物件，回到它所保存的那段记忆。</p>
        </div>
        <nav v-else class="room-object-list" aria-label="纪念小屋内容列表">
          <a v-for="object in roomObjects" :key="object.id" :href="object.href"><span aria-hidden="true">{{ object.icon }}</span><div><small>{{ object.label }}</small><strong>{{ object.title }}</strong><em>{{ object.detail }}</em></div><b aria-hidden="true">→</b></a>
        </nav>
        <div class="room-secondary-paths" aria-label="更多纪念路径"><RouterLink :to="`/editor/${id}/archive`">生命档案：地点、关系与来源 →</RouterLink><RouterLink :to="`/editor/${id}/yearbook`">年度纪念册：把这一年收好 →</RouterLink><RouterLink :to="`/habitat?memorial=${id}`">记忆角落：把内容留成私密风景 →</RouterLink><RouterLink :to="`/editor/${id}/digital-life`">数字记忆：只基于授权资料 →</RouterLink></div>
      </section>

      <section class="memory-progress" aria-labelledby="memory-progress-title">
        <div class="memory-progress-ring" :style="{ '--memory-progress': `${memoryProgress}%` }"><strong>{{ memoryProgress }}</strong><span>%</span></div>
        <div class="memory-progress-copy">
          <p class="eyebrow">MEMORY TRAIL / 记忆正在慢慢亮起来</p>
          <h2 id="memory-progress-title">不必一次完成，下一次回来还有地方继续。</h2>
          <div class="memory-milestones" aria-label="纪念内容完成情况"><span v-for="item in memoryMilestones" :key="item.label" :class="{ complete: item.complete }"><i aria-hidden="true">{{ item.complete ? '✦' : '·' }}</i>{{ item.label }}</span></div>
          <a class="text-link" :href="nextMemoryStep.href">{{ nextMemoryStep.label }} →</a>
        </div>
      </section>

      <section id="room-day" class="room-section day-section"><p class="eyebrow">招牌体验 · TA 的一天</p><h2>把最普通、也最舍不得忘记的一天还原出来</h2><p class="section-note">按一天中的时间记录 TA 的动作、位置和声音。公开页会随时间从清晨走到夜晚。</p>
        <div v-if="experience.dayMoments.length" class="day-strip" aria-label="已经记录的生活片段"><button v-for="item in experience.dayMoments" :key="item.id" type="button" @click="editDayMoment(item)"><time>{{ item.momentTime }}</time><strong>{{ item.title }}</strong><small>{{ item.placeName || '熟悉的日常' }}</small></button></div>
        <form class="room-form" @submit.prevent="saveDayMoment"><label>一天中的时间<input v-model="dayMoment.momentTime" type="time" required /></label><label>这一刻 TA 在做什么<input v-model="dayMoment.title" maxlength="80" required placeholder="例如：在门口等我回家" /></label><label>TA 常待的位置<input v-model="dayMoment.placeName" maxlength="80" placeholder="例如：阳台最左边" /></label><label>关联一张相册照片<select v-model="dayMoment.mediaId"><option value="">暂不关联</option><option v-for="photo in galleryPhotos" :key="photo.mediaId" :value="photo.mediaId">{{ photo.caption || `相册照片 ${photo.position + 1}` }}</option></select></label><label>这一刻的细节<textarea v-model="dayMoment.story" maxlength="500" rows="3" placeholder="动作、声音、表情，越具体越像 TA。"></textarea></label><div class="form-inline-actions"><button class="button" :disabled="busy === 'day-moment'">{{ editingMomentId ? '更新生活片段' : '放进 TA 的一天' }}</button><button v-if="editingMomentId" class="button button-quiet" type="button" @click="resetDayMoment">取消编辑</button></div></form>
        <div class="memory-list day-list"><article v-for="item in experience.dayMoments" :key="item.id"><div><time>{{ item.momentTime }}</time><span v-if="item.placeName">{{ item.placeName }}</span></div><h3>{{ item.title }}</h3><p v-if="item.story">{{ item.story }}</p><img v-if="item.mediaUrl" :src="item.mediaUrl" :alt="item.title" /><div class="item-actions"><button class="text-button" type="button" @click="editDayMoment(item)">编辑</button><button class="text-button danger" type="button" :disabled="busy === `day-${item.id}`" @click="removeDayMoment(item.id)">删除</button></div></article></div>
      </section>

      <section id="room-life" class="room-section life-section"><p class="eyebrow">生命指纹</p><h2>留下只有家人才知道的 TA</h2><p class="section-note">不做性格测试，也不让 AI 编故事。每一条都来自你真实的记得。</p><div class="life-grid"><form v-for="([key, label, placeholder]) in lifeFields" :key="key" @submit.prevent="saveLifeDetailField(key)"><label>{{ label }}<textarea v-model="lifeDetails[key]" maxlength="300" rows="3" :placeholder="placeholder"></textarea></label><button class="text-button" :disabled="busy === `life-${key}`">保存这一条</button></form></div></section>

      <section id="room-sound" class="room-section"><p class="eyebrow">声音记忆盒</p><h2>有些声音，一听就能认出来</h2>
        <form class="room-form" @submit.prevent="addSound"><label>MP3 或 WAV（不超过 10MB）<input type="file" accept="audio/mpeg,audio/wav,.mp3,.wav" required @change="selectAudio" /></label><label>声音标题<input v-model="sound.title" maxlength="80" required placeholder="例如：回家时的小铃铛" /></label><label>这段声音的故事<textarea v-model="sound.story" maxlength="500" rows="3"></textarea></label><button class="button" :disabled="busy === 'sound'">{{ busy === 'sound' ? `上传中 ${audioProgress}%` : '放进声音盒' }}</button></form>
        <div class="memory-list"><article v-for="item in experience.sounds" :key="item.id"><h3>{{ item.title }}</h3><p v-if="item.story">{{ item.story }}</p><audio controls preload="metadata" :src="item.mediaUrl"></audio><button class="text-button danger" :disabled="busy === `sound-${item.id}`" @click="removeSound(item.id)">删除</button></article></div>
      </section>

      <section id="room-interview" class="room-section"><p class="eyebrow">回忆采访</p><h2>不用面对一张空白纸</h2><p class="section-note">问题只负责引导，答案始终由你书写。不会由 AI 以 TA 的口吻补写不存在的经历。</p>
        <div class="interview-deck"><div class="interview-tabs" role="tablist" aria-label="记忆采访问题"><button v-for="(prompt, index) in allInterviewPrompts" :key="prompt.key" type="button" role="tab" :aria-selected="activeInterviewPrompt?.key === prompt.key" :class="{ active: activeInterviewPrompt?.key === prompt.key, answered: Boolean(interviews[prompt.key]?.trim()) }" @click="activeInterviewKey = prompt.key"><span>{{ String(index + 1).padStart(2, '0') }}</span>{{ prompt.label }}</button></div><form v-if="activeInterviewPrompt" class="interview-card" @submit.prevent="saveInterview(activeInterviewPrompt.key)"><div><span>{{ String(activeInterviewIndex + 1).padStart(2, '0') }}</span><small>{{ activeInterviewPrompt.label }}</small></div><label>{{ activeInterviewPrompt.question }}<em>{{ activeInterviewPrompt.helper }}</em><textarea v-model="interviews[activeInterviewPrompt.key]" maxlength="1000" rows="5" placeholder="想到哪里就写到哪里；一句话也很好。"></textarea></label><p v-if="activeInterviewPrompt.acceptedNow.includes('AUDIO') || activeInterviewPrompt.acceptedNow.includes('PHOTO')" class="interview-media-note">{{ activeInterviewPrompt.acceptedNow.includes('AUDIO') ? '可以把原始录音收进声音记忆盒；' : '' }}{{ activeInterviewPrompt.acceptedNow.includes('PHOTO') ? '可以把原始照片收进相册；' : '' }}回答和原始素材都只保存用户真实提供的内容。</p><div class="interview-actions"><button class="button button-quiet" type="button" @click="selectInterview(-1)">← 上一个</button><button class="text-button" :disabled="busy === `interview-${activeInterviewPrompt.key}`">保存这一段</button><button class="button button-quiet" type="button" @click="selectInterview(1)">下一个 →</button></div></form></div>
      </section>

      <section id="room-keepsake" class="room-section"><p class="eyebrow">旧物故事</p><h2>项圈、饭碗、玩具，也记得 TA</h2><form class="room-form compact" @submit.prevent="createKeepsake"><label>旧物名称<input v-model="keepsake.title" maxlength="80" required /></label><label>它的故事<textarea v-model="keepsake.story" maxlength="800" rows="4" required></textarea></label><button class="button" :disabled="busy === 'keepsake'">收进小屋</button></form><div class="memory-list keepsakes"><article v-for="item in experience.keepsakes" :key="item.id"><h3>{{ item.title }}</h3><p>{{ item.story }}</p><button class="text-button danger" @click="removeKeepsake(item.id)">删除</button></article></div></section>

      <section id="room-burial" class="room-section burial-section"><p class="eyebrow">安葬与归处 · 默认私密</p><h2>记录 TA 被妥善安放的地方</h2><div class="compliance-note"><strong>这里只保存纪念档案。</strong><p>不提供遗体买卖、运输、墓地中介或线下代办。请按所在地主管部门或正规机构指引处理；公开信息只显示省市级地区。</p></div>
        <form class="room-form" @submit.prevent="saveBurial"><label>妥善处理方式<select v-model="burial.dispositionType"><option v-for="([value, label]) in dispositions" :key="value" :value="value">{{ label }}</option></select></label><label>日期（选填）<input v-model="burial.occurredOn" type="date" :max="new Date().toISOString().slice(0, 10)" /></label><label>省 / 市（选填）<input v-model="burial.region" maxlength="64" placeholder="例如：上海市；不要填写门牌" /></label><label>纪念场所称呼（选填）<input v-model="burial.placeName" maxlength="80" placeholder="例如：家中的纪念角" /></label><label>纪念文字<textarea v-model="burial.remembranceText" maxlength="500" rows="4"></textarea></label><div class="burial-actions"><button class="button" :disabled="busy === 'burial'">私密保存</button><button v-if="experience.burial && ['PRIVATE','REJECTED'].includes(experience.burial.reviewStatus)" class="button secondary-button" type="button" :disabled="busy === 'submit-burial'" @click="submitBurial">申请公开</button></div></form>
        <p v-if="experience.burial" class="review-status"><strong>{{ statusText[experience.burial.reviewStatus] }}</strong><span v-if="experience.burial.reviewNote">审核说明：{{ experience.burial.reviewNote }}</span></p>
      </section>
    </template>
    <div v-else class="account-empty"><h1>暂时无法打开纪念小屋。</h1><p>{{ error || '请重新登录后再试。' }}</p><RouterLink class="button" to="/login">重新登录</RouterLink></div>
  </main>
</template>

<style scoped>
.room-page{--room-panel:color-mix(in srgb,var(--surface-soft) 70%,var(--surface));padding-block:clamp(2rem,5vw,5rem);font-family:var(--font-body)}.memory-intent-panel{display:grid;grid-template-columns:minmax(0,1fr) minmax(290px,.9fr);gap:18px;align-items:end;margin:0 0 28px;padding:clamp(20px,4vw,38px);border-top:1px solid var(--border);border-bottom:1px solid var(--border);background:linear-gradient(125deg,color-mix(in srgb,var(--primary) 8%,var(--surface)),var(--surface))}.memory-intent-panel h2{margin:.3rem 0 .6rem;font:clamp(27px,3vw,42px)/1.2 var(--font-serif)}.memory-intent-panel>div>p:last-child{max-width:560px;margin:0;color:var(--muted);line-height:1.7}.memory-intent-options{display:flex;flex-wrap:wrap;gap:8px}.memory-intent-options button{padding:.6rem .75rem;border:1px solid var(--border);border-radius:0;background:transparent;color:var(--text);font:inherit;font-size:.85rem}.memory-intent-options button.active{border-color:var(--primary);background:color-mix(in srgb,var(--primary) 13%,var(--surface));color:var(--primary)}.memory-intent-panel>.text-link,.memory-intent-panel>.text-button{align-self:end;justify-self:start}.room-page.theme-album .room-space{border-radius:2px;background:repeating-linear-gradient(0deg,color-mix(in srgb,var(--primary) 5%,transparent) 0 1px,transparent 1px 4px),var(--surface)}.room-page.theme-album .room-scene{transform:rotate(-.25deg)}.room-page.theme-home .room-space{background:radial-gradient(circle at 18% 9%,color-mix(in srgb,var(--primary) 18%,transparent),transparent 25%),var(--surface-soft)}.room-page.theme-home .room-scene{box-shadow:inset 0 0 80px rgb(0 0 0 / 24%)}.room-page.theme-garden .room-space{border-radius:40% 2% 30% 2%}.room-page.theme-meadow .room-space{border-radius:0;background:linear-gradient(180deg,color-mix(in srgb,var(--primary) 12%,var(--surface)),var(--surface))}.room-page.theme-sunny .room-space{background:linear-gradient(120deg,color-mix(in srgb,var(--primary) 8%,var(--surface)),var(--surface))}
.room-hero{display:flex;justify-content:space-between;gap:2rem;align-items:end;margin-bottom:2rem}.room-hero h1{max-width:760px}.room-map{display:grid;grid-template-columns:repeat(3,1fr);gap:1rem;margin:2rem 0 4rem}.room-map a{display:grid;gap:.65rem;min-height:126px;padding:1.4rem;border:1px solid var(--color-border);border-radius:var(--radius-lg);background:var(--room-panel);color:inherit;text-decoration:none;transition:transform .2s,border-color .2s}.room-map a:hover{transform:translateY(-3px);border-color:var(--color-accent)}.room-map span,.room-map small{color:var(--color-text-muted)}.room-map .room-map-feature{border-color:color-mix(in srgb,var(--color-accent) 66%,var(--color-border));background:color-mix(in srgb,var(--color-accent) 9%,var(--room-panel))}
.room-space{margin:2rem 0 4rem;border:1px solid color-mix(in srgb,var(--primary) 30%,var(--border));background:var(--surface);box-shadow:0 28px 72px rgb(0 0 0 / 12%)}.room-space-heading{display:flex;justify-content:space-between;gap:2rem;align-items:end;padding:clamp(20px,4vw,44px);border-bottom:1px solid var(--border)}.room-space-heading h2{max-width:720px;margin:.35rem 0 .55rem;font-size:clamp(28px,4vw,48px)}.room-space-heading p:last-child{max-width:650px;margin:0;color:var(--muted);line-height:1.75}.room-mode-toggle{display:inline-flex;flex:0 0 auto;padding:3px;border:1px solid var(--border)}.room-mode-toggle button{padding:.45rem .72rem;border:0;background:transparent;color:var(--muted)}.room-mode-toggle button.active{background:var(--primary);color:var(--button-text)}.room-scene{position:relative;min-height:660px;overflow:hidden;background:linear-gradient(155deg,color-mix(in srgb,var(--surface-soft) 92%,#324a5e),var(--canvas) 67%)}.room-scene::before{position:absolute;inset:0;background:linear-gradient(90deg,transparent 0 32%,color-mix(in srgb,var(--primary) 8%,transparent) 32% 32.4%,transparent 32.7%),linear-gradient(0deg,transparent 0 54%,color-mix(in srgb,var(--border) 52%,transparent) 54.2% 54.5%,transparent 54.8%);content:'';opacity:.55}.scene-window-light{position:absolute;top:-21%;right:6%;width:43%;aspect-ratio:1;border-radius:50%;background:radial-gradient(circle,color-mix(in srgb,var(--primary) 26%,transparent),transparent 66%);filter:blur(3px);pointer-events:none}.scene-floor{position:absolute;z-index:0;right:-13%;bottom:-28%;left:-13%;height:52%;border-top:1px solid color-mix(in srgb,var(--primary) 25%,var(--border));background:repeating-linear-gradient(90deg,color-mix(in srgb,var(--primary) 6%,transparent) 0 1px,transparent 1px 72px);transform:perspective(620px) rotateX(56deg);transform-origin:top}.room-object{position:absolute;z-index:2;display:grid;grid-template-columns:auto minmax(0,1fr);gap:.55rem;align-items:start;max-width:min(30%,270px);padding:.65rem;border:1px solid transparent;color:var(--text);text-decoration:none;transition:transform .25s ease,border-color .25s ease,background .25s ease}.room-object:hover,.room-object:focus-visible{z-index:4;border-color:var(--primary);background:color-mix(in srgb,var(--surface) 92%,transparent);transform:translateY(-4px)}.room-object i{display:grid;width:38px;height:38px;place-items:center;border:1px solid color-mix(in srgb,var(--primary) 36%,var(--border));background:color-mix(in srgb,var(--canvas) 72%,transparent);color:var(--primary);font-family:var(--font-serif);font-size:1.35rem;font-style:normal}.room-object.filled i,.room-object.filled .object-photo{box-shadow:0 0 24px color-mix(in srgb,var(--primary) 17%,transparent)}.object-photo{display:block;width:52px;height:52px;border:2px solid color-mix(in srgb,var(--primary) 55%,var(--border));background:center/cover no-repeat}.object-copy{display:grid;gap:.1rem;min-width:0}.object-copy small{color:var(--primary);font-size:.68rem;letter-spacing:.08em}.object-copy strong,.object-copy em{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.object-copy strong{font-family:var(--font-serif);font-weight:500}.object-copy em{color:var(--muted);font-size:.71rem;font-style:normal}.object-frame{top:11%;left:8%}.object-window{top:12%;right:7%;max-width:31%}.object-door{top:39%;left:39%}.object-bed{bottom:18%;left:10%}.object-bowl{bottom:9%;left:35%}.object-collar{bottom:8%;left:56%}.object-toy{bottom:19%;right:6%}.object-mailbox{top:38%;right:7%}.object-lamp{top:54%;right:35%}.object-calendar{top:27%;left:39%}.scene-note{position:absolute;z-index:3;right:1rem;bottom:1rem;margin:0;color:var(--muted);font-size:.75rem}.room-object-list{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:1px;background:var(--border)}.room-object-list a{display:grid;grid-template-columns:30px minmax(0,1fr) auto;gap:.85rem;align-items:center;padding:1.1rem;background:var(--surface);color:inherit;text-decoration:none}.room-object-list a:hover,.room-object-list a:focus-visible{background:color-mix(in srgb,var(--primary) 8%,var(--surface))}.room-object-list>a>span{color:var(--primary);font-family:var(--font-serif);font-size:1.35rem}.room-object-list div{display:grid;gap:.16rem}.room-object-list small,.room-object-list em{color:var(--muted);font-size:.75rem;font-style:normal}.room-object-list strong{font-family:var(--font-serif);font-weight:500}.room-object-list b{color:var(--primary);font-weight:400}.room-secondary-paths{display:flex;flex-wrap:wrap;gap:.75rem 1.3rem;padding:1rem clamp(20px,4vw,44px);border-top:1px solid var(--border);font-size:.8rem}.room-secondary-paths a{color:var(--muted);text-decoration:none}.room-secondary-paths a:hover,.room-secondary-paths a:focus-visible{color:var(--primary)}
.room-section{scroll-margin-top:2rem;padding:clamp(1.4rem,4vw,3rem);margin:1.4rem 0;border:1px solid var(--color-border);border-radius:var(--radius-xl);background:var(--room-panel)}.section-note{color:var(--color-text-muted)}.room-form{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:1rem;margin:1.5rem 0}.room-form label{display:grid;gap:.45rem}.room-form label:has(textarea),.room-form .button{grid-column:1/-1}.room-form input,.room-form textarea,.room-form select{width:100%}.room-form .button{justify-self:start}.memory-list{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:1rem}.memory-list article,.interview-grid form{padding:1.2rem;border-radius:var(--radius-lg);background:color-mix(in srgb,var(--color-background) 76%,transparent)}.memory-list audio{width:100%;margin:.8rem 0}.memory-list p{white-space:pre-wrap}.interview-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:1rem}.interview-grid form{display:grid;gap:.75rem}.interview-grid form>span{color:var(--color-accent);font-family:serif;font-size:1.4rem}.interview-grid label{display:grid;gap:.6rem}.interview-grid textarea{width:100%}.compliance-note{padding:1rem 1.2rem;border-left:3px solid var(--color-accent);background:color-mix(in srgb,var(--color-accent) 8%,transparent)}.compliance-note p{margin:.3rem 0 0}.burial-actions{grid-column:1/-1;display:flex;gap:.8rem}.review-status{display:flex;flex-direction:column;gap:.4rem;padding:1rem;border-radius:var(--radius-md);background:color-mix(in srgb,var(--color-accent) 10%,transparent)}.danger{color:#a34d4d}
.interview-grid label small,.interview-media-note{color:var(--muted);font-size:.77rem;line-height:1.55}.interview-media-note{margin:0;padding-top:.55rem;border-top:1px solid var(--border)}
.interview-deck{display:grid;grid-template-columns:minmax(180px,.36fr) minmax(0,1fr);gap:1px;margin-top:1.5rem;border:1px solid var(--border);background:var(--border)}.interview-tabs{display:grid;align-content:start;gap:1px;background:var(--border)}.interview-tabs button{display:flex;gap:.7rem;align-items:center;padding:.75rem 1rem;border:0;background:var(--surface);color:var(--muted);text-align:left}.interview-tabs button:hover,.interview-tabs button:focus-visible,.interview-tabs button.active{color:var(--text);background:color-mix(in srgb,var(--primary) 10%,var(--surface))}.interview-tabs button span{color:var(--primary);font-family:var(--font-serif);font-size:.78rem}.interview-tabs button.answered::after{width:6px;height:6px;margin-left:auto;border-radius:50%;background:var(--primary);content:''}.interview-card{display:grid;gap:1rem;align-content:start;padding:clamp(20px,4vw,42px);background:var(--surface)}.interview-card>div:first-child{display:flex;gap:.75rem;align-items:baseline}.interview-card>div:first-child span{color:var(--primary);font-family:var(--font-serif);font-size:2.1rem}.interview-card>div:first-child small{color:var(--muted)}.interview-card label{display:grid;gap:.75rem;font-family:var(--font-serif);font-size:clamp(21px,3vw,31px);line-height:1.5}.interview-card label em{max-width:620px;color:var(--muted);font-family:var(--font-sans);font-size:.82rem;font-style:normal;line-height:1.65}.interview-card textarea{width:100%;font-family:var(--font-sans);font-size:1rem}.interview-actions{display:flex;flex-wrap:wrap;gap:.75rem;align-items:center}.interview-actions .text-button{margin-inline:auto}.interview-actions .button{grid-column:auto}
.day-section{background:linear-gradient(145deg,color-mix(in srgb,var(--color-accent) 8%,var(--room-panel)),var(--room-panel))}.day-strip{display:flex;gap:.7rem;overflow-x:auto;padding:.8rem 0 1.2rem}.day-strip button{display:grid;flex:0 0 170px;gap:.3rem;padding:1rem;text-align:left;border:1px solid var(--color-border);border-radius:var(--radius-md);background:color-mix(in srgb,var(--color-background) 70%,transparent);color:inherit}.day-strip time,.day-list time{font-family:var(--font-serif);font-size:1.25rem;color:var(--color-accent)}.day-strip small,.day-list span{color:var(--color-text-muted)}.form-inline-actions{grid-column:1/-1;display:flex;gap:.8rem;flex-wrap:wrap}.form-inline-actions .button{grid-column:auto}.day-list article{position:relative;overflow:hidden}.day-list article>div:first-child{display:flex;justify-content:space-between;gap:1rem}.day-list img{width:100%;max-height:220px;object-fit:cover;border-radius:var(--radius-md);margin-top:.7rem}.item-actions{display:flex;gap:1rem;margin-top:.8rem}.life-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:1rem;margin-top:1.5rem}.life-grid form{display:grid;gap:.7rem;padding:1.15rem;border:1px solid var(--color-border);border-radius:var(--radius-lg);background:color-mix(in srgb,var(--color-background) 72%,transparent)}.life-grid label{display:grid;gap:.55rem}.life-grid textarea{width:100%}
.memory-progress{display:grid;grid-template-columns:170px minmax(0,1fr);gap:clamp(28px,5vw,64px);align-items:center;margin:-1rem 0 4rem;padding:clamp(26px,4vw,46px);border:1px solid color-mix(in srgb,var(--color-accent) 28%,var(--color-border));border-radius:var(--radius-xl);background:radial-gradient(circle at 8% 50%,color-mix(in srgb,var(--color-accent) 17%,transparent),transparent 26%),linear-gradient(135deg,color-mix(in srgb,var(--color-background-soft) 95%,var(--color-accent)),var(--room-panel));box-shadow:0 24px 70px rgba(0,0,0,.12)}.memory-progress-ring{--memory-progress:0%;display:flex;width:140px;aspect-ratio:1;align-items:baseline;justify-content:center;padding-top:43px;border-radius:50%;background:radial-gradient(circle,var(--color-background-soft) 54%,transparent 56%),conic-gradient(var(--color-accent) var(--memory-progress),color-mix(in srgb,var(--color-border) 78%,transparent) 0);box-shadow:0 0 0 1px color-mix(in srgb,var(--color-accent) 18%,transparent),0 0 52px color-mix(in srgb,var(--color-accent) 15%,transparent)}.memory-progress-ring strong{font:2.4rem var(--font-serif);color:var(--color-accent)}.memory-progress-ring span{color:var(--color-text-muted)}.memory-progress-copy h2{max-width:740px;margin:.45rem 0 1.2rem;font-size:clamp(26px,3.4vw,40px)}.memory-milestones{display:flex;flex-wrap:wrap;gap:.55rem;margin-bottom:1.25rem;color:var(--color-text-muted);font-size:.82rem}.memory-milestones span{display:inline-flex;gap:.4rem;align-items:center;padding:.42rem .72rem;border:1px solid color-mix(in srgb,var(--color-border) 72%,transparent);border-radius:999px;background:color-mix(in srgb,var(--color-background) 42%,transparent)}.memory-milestones i{display:grid;width:18px;height:18px;place-items:center;border:1px solid var(--color-border);border-radius:50%;font-style:normal}.memory-milestones .complete{border-color:color-mix(in srgb,var(--color-accent) 34%,var(--color-border));color:var(--color-text)}.memory-milestones .complete i{border-color:var(--color-accent);color:var(--color-accent)}
.memory-progress-ring{border:2px solid rgba(226,195,130,.28);background:radial-gradient(circle,var(--color-background-soft) 54%,transparent 56%),conic-gradient(#e2c486 var(--memory-progress),rgba(129,154,172,.2) 0);box-shadow:inset 0 0 24px rgba(226,195,130,.08),0 0 52px rgba(226,195,130,.13)}
@media(max-width:760px){.memory-intent-panel{grid-template-columns:1fr;align-items:start}.memory-intent-panel>.text-link,.memory-intent-panel>.text-button{align-self:auto}.room-hero,.room-space-heading{display:grid}.room-map,.memory-list,.interview-grid,.room-form,.life-grid,.room-object-list,.interview-deck{grid-template-columns:1fr}.room-map{grid-template-columns:repeat(2,1fr)}.room-map a{min-height:110px}.memory-progress{grid-template-columns:1fr}.memory-progress-ring{width:110px;padding-top:32px}.memory-progress-ring strong{font-size:2rem}.room-form label:has(textarea),.room-form .button,.form-inline-actions{grid-column:auto}.room-space-heading{gap:1rem}.room-mode-toggle{justify-self:start}.room-scene{display:grid;gap:.7rem;min-height:0;padding:1rem}.scene-window-light,.scene-floor,.room-scene::before,.scene-note{display:none}.room-object{position:relative;top:auto;right:auto;bottom:auto;left:auto;max-width:none;min-height:72px;padding:.8rem;background:color-mix(in srgb,var(--surface) 88%,transparent)}.room-object:focus-visible{transform:none}.room-secondary-paths{display:grid;gap:.6rem}.interview-tabs{grid-template-columns:repeat(5,minmax(0,1fr));overflow-x:auto}.interview-tabs button{justify-content:center;min-width:54px;padding:.65rem .35rem;font-size:0}.interview-tabs button span{font-size:.78rem}.interview-tabs button.answered::after{display:none}.interview-card{padding:1.25rem}.interview-actions{justify-content:space-between}.interview-actions .text-button{margin-inline:0}}
</style>
