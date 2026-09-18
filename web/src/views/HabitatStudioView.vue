<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import PixelHabitat from '@/components/PixelHabitat.vue'
import { useAuth } from '@/stores/auth'
import {
  addHabitatItem,
  addHabitatNote,
  deleteHabitatItem,
  deleteHabitatNote,
  getMemorial,
  getMemorialExperience,
  getMemorialHabitat,
  saveMemorialHabitat,
  type Memorial,
  type MemorialExperience,
} from '@/api/memorials'

type Keepsake = { id: string; kind: 'light' | 'flower' | 'stone'; x: number; y: number }
type Note = { id: string; text: string; date: string; sourceLabel?: string | null }
type Studio = {
  scene: 'forest' | 'companion'
  title: string
  name: string
  light: number
  objects: Keepsake[]
  notes: Note[]
}
type MemorySource = {
  id: string | null
  type: 'DAY_MOMENT' | 'LIFE_DETAIL' | 'KEEPSAKE'
  label: string
  text: string
  date: string
}

const route = useRoute()
const { state: auth, hydrate } = useAuth()
const memorialId = computed(() =>
  typeof route.query.memorial === 'string' ? route.query.memorial : '',
)
const storageKey = 'digitalnest:habitat-studio:v2'
const studio = ref<Studio>({
  scene: 'forest',
  title: '留在光里的日子',
  name: '',
  light: 80,
  objects: [],
  notes: [],
})
const memorial = ref<Memorial | null>(null)
const experience = ref<MemorialExperience | null>(null)
const panel = ref('space')
const selected = ref<Keepsake['kind'] | null>(null)
const selectedSource = ref<MemorySource | null>(null)
const preview = ref(false)
const status = ref('每一个小物件，都可以有自己的故事。')
const saveStatus = ref('仅保存在此浏览器')
const noteText = ref('')
const noteDate = ref(new Date().toLocaleDateString('en-CA'))
const ready = ref(false)
const exporting = ref(false)
const pixelScene = ref<InstanceType<typeof PixelHabitat>>()
const stage = ref<HTMLElement>()
const paused = ref(false)
const interacting = ref(false)
const cloudError = ref('')
let interactionTimer: ReturnType<typeof setTimeout> | undefined
let settingsTimer: ReturnType<typeof setTimeout> | undefined
const symbols = { light: '◉', flower: '✿', stone: '◈' }
const labels = { light: '记忆灯', flower: '纪念花', stone: '溪石' }
const lastNote = computed(() => studio.value.notes[0])
const linkedSources = computed<MemorySource[]>(() => {
  if (!experience.value) return []
  return [
    ...experience.value.dayMoments.map((item) => ({
      id: item.id,
      type: 'DAY_MOMENT' as const,
      label: `${item.momentTime} · ${item.title}`,
      text: item.story || item.title,
      date: noteDate.value,
    })),
    ...experience.value.keepsakes.map((item) => ({
      id: item.id,
      type: 'KEEPSAKE' as const,
      label: `旧物 · ${item.title}`,
      text: item.story,
      date: noteDate.value,
    })),
    ...experience.value.lifeDetails
      .filter((item) => item.answer.trim())
      .map((item) => ({
        id: item.id,
        type: 'LIFE_DETAIL' as const,
        label: `生命指纹 · ${item.detailKey}`,
        text: item.answer,
        date: noteDate.value,
      })),
  ].slice(0, 8)
})

onUnmounted(() => {
  clearTimeout(interactionTimer)
  clearTimeout(settingsTimer)
})

function interact() {
  interacting.value = true
  status.value =
    studio.value.scene === 'forest'
      ? '鱼食落进水里，小鱼慢慢游了过来。'
      : '轻轻摸了摸它，小猫靠近了一点。'
  clearTimeout(interactionTimer)
  interactionTimer = setTimeout(() => {
    interacting.value = false
  }, 4500)
}

function loadLocal() {
  try {
    const saved = JSON.parse(localStorage.getItem(storageKey) ?? 'null')
    if (!saved || typeof saved !== 'object') return
    studio.value.scene = saved.scene === 'companion' ? 'companion' : 'forest'
    if (typeof saved.title === 'string') studio.value.title = saved.title.slice(0, 24)
    if (typeof saved.name === 'string') studio.value.name = saved.name.slice(0, 16)
    if (typeof saved.light === 'number')
      studio.value.light = Math.min(100, Math.max(30, saved.light))
    if (Array.isArray(saved.objects))
      studio.value.objects = saved.objects
        .filter(
          (o: Keepsake) =>
            o &&
            ['light', 'flower', 'stone'].includes(o.kind) &&
            typeof o.id === 'string' &&
            Number.isFinite(o.x) &&
            Number.isFinite(o.y) &&
            o.x >= 5 &&
            o.x <= 95 &&
            o.y >= 15 &&
            o.y <= 85,
        )
        .slice(0, 18)
    if (Array.isArray(saved.notes))
      studio.value.notes = saved.notes
        .filter(
          (n: Note) =>
            n &&
            typeof n.id === 'string' &&
            typeof n.text === 'string' &&
            n.text.length <= 180 &&
            /^\d{4}-\d{2}-\d{2}$/.test(n.date),
        )
        .slice(0, 30)
  } catch {
    saveStatus.value = '上次的布置无法读取，可重新布置。'
  }
}

function persistLocal() {
  try {
    localStorage.setItem(storageKey, JSON.stringify(studio.value))
    saveStatus.value = '已保存到此浏览器'
  } catch {
    saveStatus.value = '保存失败，请下载备份后再离开。'
  }
}

async function loadLinkedMemorial() {
  if (!memorialId.value) {
    loadLocal()
    return
  }
  saveStatus.value = '正在连接纪念空间…'
  try {
    await hydrate()
    if (!auth.user) throw new Error('请登录后，从自己的纪念空间进入这处角落。')
    const [space, loadedMemorial, loadedExperience] = await Promise.all([
      getMemorialHabitat(memorialId.value),
      getMemorial(memorialId.value),
      getMemorialExperience(memorialId.value),
    ])
    memorial.value = loadedMemorial
    experience.value = loadedExperience
    studio.value = {
      scene: space.scene === 'COMPANION' ? 'companion' : 'forest',
      title: space.title,
      name: loadedMemorial.petName,
      light: space.light,
      objects: space.items.map((item) => ({
        id: item.id,
        kind: item.kind.toLowerCase() as Keepsake['kind'],
        x: item.xPercent,
        y: item.yPercent,
      })),
      notes: space.notes.map((note) => ({
        id: note.id,
        text: note.text,
        date: note.memoryDate,
        sourceLabel: note.sourceLabel,
      })),
    }
    saveStatus.value = '已与此纪念空间同步'
  } catch (caught) {
    cloudError.value = caught instanceof Error ? caught.message : '暂时无法读取这处纪念空间。'
    saveStatus.value = '未能连接账号空间'
  }
}

onMounted(async () => {
  await loadLinkedMemorial()
  ready.value = true
})

watch(
  studio,
  () => {
    if (ready.value && !memorialId.value) persistLocal()
  },
  { deep: true },
)
watch(
  () => [studio.value.scene, studio.value.title, studio.value.light],
  () => {
    if (!ready.value || !memorialId.value) return
    clearTimeout(settingsTimer)
    settingsTimer = setTimeout(() => {
      void syncSettings()
    }, 450)
  },
)

async function syncSettings() {
  if (!memorialId.value) return
  saveStatus.value = '正在保存到纪念空间…'
  try {
    await saveMemorialHabitat(memorialId.value, {
      scene: studio.value.scene === 'forest' ? 'FOREST' : 'COMPANION',
      title: studio.value.title,
      light: studio.value.light,
    })
    saveStatus.value = '已与此纪念空间同步'
  } catch (caught) {
    saveStatus.value = '保存没有完成，请稍后重试。'
    status.value = caught instanceof Error ? caught.message : '空间设置没有保存。'
  }
}

function changeScene(value: Studio['scene']) {
  if (studio.value.scene !== value) studio.value.scene = value
}

function openMemoryPanel() {
  panel.value = 'memory'
  preview.value = false
}

async function place(x = 50, y = 70) {
  if (!selected.value || preview.value) return
  if (studio.value.objects.length >= 18) {
    status.value = '空间已放置 18 件物品，可先移除一件。'
    return
  }
  const kind = selected.value
  const point = {
    x: Math.round(Math.max(5, Math.min(95, x))),
    y: Math.round(Math.max(15, Math.min(85, y))),
  }
  selected.value = null
  try {
    if (memorialId.value) {
      const saved = await addHabitatItem(memorialId.value, {
        kind: kind.toUpperCase() as 'LIGHT' | 'FLOWER' | 'STONE',
        xPercent: point.x,
        yPercent: point.y,
      })
      studio.value.objects.push({ id: saved.id, kind, x: saved.xPercent, y: saved.yPercent })
    } else studio.value.objects.push({ id: crypto.randomUUID(), kind, ...point })
    status.value = `已安放${labels[kind]}。点击物件可收回。`
  } catch (caught) {
    status.value = caught instanceof Error ? caught.message : '这件物品没有安放成功。'
  }
}

function placeAt(event: MouseEvent) {
  if (!stage.value) return
  const rect = stage.value.getBoundingClientRect()
  void place(
    ((event.clientX - rect.left) / rect.width) * 100,
    ((event.clientY - rect.top) / rect.height) * 100,
  )
}

async function remove(id: string) {
  if (preview.value) return
  try {
    if (memorialId.value) await deleteHabitatItem(memorialId.value, id)
    studio.value.objects = studio.value.objects.filter((item) => item.id !== id)
    status.value = '物件已收回，可以重新安放。'
  } catch (caught) {
    status.value = caught instanceof Error ? caught.message : '物件暂时无法收回。'
  }
}

function undoLatest() {
  const latest = studio.value.objects.at(-1)
  if (latest) void remove(latest.id)
}

function useMemorySource(source: MemorySource) {
  selectedSource.value = source
  noteText.value = source.text.slice(0, 180)
  noteDate.value = source.date
  status.value = `已带入「${source.label}」，你可以再补充自己的话。`
}

async function addNote() {
  if (!noteText.value.trim() || !/^\d{4}-\d{2}-\d{2}$/.test(noteDate.value)) return
  if (studio.value.notes.length >= (memorialId.value ? 50 : 30)) {
    status.value = '已经收好了很多便笺，可以先整理已有内容。'
    return
  }
  try {
    if (memorialId.value) {
      const source = selectedSource.value
      const saved = await addHabitatNote(memorialId.value, {
        memoryDate: noteDate.value,
        text: noteText.value.trim().slice(0, 180),
        sourceType: source?.type ?? 'MANUAL',
        sourceId: source?.id ?? null,
        sourceLabel: source?.label ?? null,
      })
      studio.value.notes.unshift({
        id: saved.id,
        text: saved.text,
        date: saved.memoryDate,
        sourceLabel: saved.sourceLabel,
      })
    } else
      studio.value.notes.unshift({
        id: crypto.randomUUID(),
        text: noteText.value.trim().slice(0, 180),
        date: noteDate.value,
      })
    noteText.value = ''
    selectedSource.value = null
    status.value = '这段记忆已收好，也会出现在纪念卡中。'
  } catch (caught) {
    status.value = caught instanceof Error ? caught.message : '这段记忆暂时无法保存。'
  }
}

async function removeNote(id: string) {
  try {
    if (memorialId.value) await deleteHabitatNote(memorialId.value, id)
    studio.value.notes = studio.value.notes.filter((note) => note.id !== id)
  } catch (caught) {
    status.value = caught instanceof Error ? caught.message : '这张便笺暂时无法删除。'
  }
}

function download(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = name
  anchor.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

function backup() {
  download(
    new Blob([JSON.stringify(studio.value, null, 2)], { type: 'application/json' }),
    '我的纪念生态空间.json',
  )
  status.value = '空间数据已下载，包含全部便笺和物件位置。'
}

async function postcard() {
  const sceneCanvas = pixelScene.value?.getCanvas()
  if (!sceneCanvas || exporting.value) return
  exporting.value = true
  try {
    const canvas = document.createElement('canvas')
    canvas.width = 1536
    canvas.height = 1240
    const context = canvas.getContext('2d')
    if (!context) throw new Error('canvas unavailable')
    context.fillStyle = '#101e26'
    context.fillRect(0, 0, canvas.width, canvas.height)
    context.imageSmoothingEnabled = false
    context.drawImage(sceneCanvas, 0, 0, 1536, 1024)
    context.font = '42px serif'
    context.textAlign = 'center'
    for (const object of studio.value.objects) {
      context.fillStyle =
        object.kind === 'light' ? '#f3cf7f' : object.kind === 'flower' ? '#e8c2ac' : '#b8bba8'
      context.fillText(symbols[object.kind], (object.x / 100) * 1536, (object.y / 100) * 1024 + 14)
    }
    context.textAlign = 'left'
    context.fillStyle = '#e8dcc5'
    context.font = '36px serif'
    context.fillText(studio.value.title || '我的纪念空间', 64, 1090)
    context.fillStyle = '#b3bdb5'
    context.font = '22px sans-serif'
    const text = lastNote.value
      ? `${lastNote.value.date} · ${lastNote.value.text}`
      : '今天，也为想念留了一点时间。'
    const characters = Array.from(text)
    let line = ''
    let y = 1138
    for (const character of characters) {
      if (context.measureText(line + character).width > 1408) {
        context.fillText(line, 64, y)
        y += 30
        line = ''
      }
      if (y > 1198) break
      line += character
    }
    context.fillText(line, 64, y)
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/png'))
    if (!blob) throw new Error('export failed')
    download(blob, '光里的纪念卡.png')
    status.value = '纪念卡已下载，没有自动公开或分享。'
  } catch {
    status.value = '纪念卡暂时没有导出成功，请重试或下载空间备份。'
  } finally {
    exporting.value = false
  }
}
</script>

<template>
  <div class="studio" :class="{ 'preview-mode': preview, 'motion-paused': paused }">
    <header class="studio-heading">
      <div>
        <span class="studio-kicker">{{
          memorial ? `${memorial.petName} 的记忆角落` : '数字小窝 / 口袋生态'
        }}</span>
        <h1>{{ memorial ? '把真实的日子，留成一处风景。' : '今天，也来坐一会儿。' }}</h1>
      </div>
      <div class="heading-tools">
        <span class="local-save">{{ saveStatus }}</span
        ><button type="button" @click="preview = !preview">
          {{ preview ? '返回编辑' : '静心预览' }}
        </button>
      </div>
    </header>
    <p v-if="cloudError" class="studio-error" role="alert">
      {{ cloudError }} <RouterLink to="/login">去登录</RouterLink>
    </p>
    <div class="studio-workspace">
      <section class="artboard-wrap" aria-label="纪念场景编辑器">
        <div
          ref="stage"
          class="artboard"
          :class="{ placing: selected && !preview }"
          tabindex="0"
          :aria-label="
            selected ? `点击位置安放${labels[selected]}，或按回车在中央安放` : '纪念场景'
          "
          @click="placeAt"
          @keydown.enter.self.prevent="place()"
          @keydown.esc="selected = null"
        >
          <PixelHabitat
            ref="pixelScene"
            :scene="studio.scene"
            :light="studio.light"
            :paused="paused"
            :interacting="interacting"
          />
          <div class="scene-label">
            <span>{{ studio.scene === 'forest' ? '01 / 苔岸森林' : '02 / 窗边旧时光' }}</span>
            <h2>{{ studio.title || '我的纪念空间' }}</h2>
            <p>
              {{
                studio.name ? `为 ${studio.name} 留的一处风景` : '光落下来的地方，记忆也有了形状。'
              }}
            </p>
          </div>
          <button
            v-for="o in studio.objects"
            :key="o.id"
            type="button"
            class="keepsake"
            :class="o.kind"
            :style="{ left: o.x + '%', top: o.y + '%' }"
            :aria-label="preview ? labels[o.kind] : `收回${labels[o.kind]}`"
            :disabled="preview"
            @click.stop="remove(o.id)"
          >
            {{ symbols[o.kind] }}
          </button>
          <div v-if="selected && !preview" class="placement-hint">
            点击场景，安放{{ labels[selected] }} · Esc 取消
          </div>
          <div class="scene-bottom">
            <span>像素小世界 · 随时可以回来</span
            ><span>{{ studio.objects.length }} 件记忆物件</span>
          </div>
        </div>
        <div class="artboard-toolbar">
          <div class="scene-tabs" aria-label="切换场景">
            <button
              type="button"
              :aria-pressed="studio.scene === 'forest'"
              @click="changeScene('forest')"
            >
              苔岸森林</button
            ><button
              type="button"
              :aria-pressed="studio.scene === 'companion'"
              @click="changeScene('companion')"
            >
              窗边旧时光
            </button>
          </div>
          <button type="button" :disabled="interacting" @click="interact">
            {{
              interacting ? '陪它一会儿' : studio.scene === 'forest' ? '撒一点鱼食' : '摸摸小猫'
            }}</button
          ><button type="button" :disabled="!ready || exporting" @click="postcard">
            {{ exporting ? '正在制作…' : '导出纪念卡 ↗' }}
          </button>
        </div>
        <p class="studio-status" role="status">{{ status }}</p>
      </section>
      <aside v-if="!preview" class="inspector" aria-label="空间设置">
        <div class="inspector-heading"><span>属于你的空间</span><small>STUDIO</small></div>
        <nav class="inspector-tabs" aria-label="编辑工具">
          <button
            v-for="t in [
              { id: 'space', label: '布景' },
              { id: 'memory', label: '记忆' },
              { id: 'settings', label: '氛围' },
            ]"
            :key="t.id"
            type="button"
            :aria-pressed="panel === t.id"
            @click="panel = t.id"
          >
            {{ t.label }}
          </button>
        </nav>
        <div v-if="panel === 'space'" class="panel-body">
          <p class="panel-caption">从一件小物开始</p>
          <h3>安放一份记得</h3>
          <p>选一件物品，再点场景里的位置。<br />它会留在你亲手安放的地方。</p>
          <div class="object-palette">
            <button
              v-for="kind in ['light', 'flower', 'stone'] as const"
              :key="kind"
              type="button"
              :aria-pressed="selected === kind"
              @click="selected = selected === kind ? null : kind"
            >
              <span :class="kind">{{ symbols[kind] }}</span
              ><strong>{{ labels[kind] }}</strong
              ><small>{{
                kind === 'light'
                  ? '让一束光留下'
                  : kind === 'flower'
                    ? '给记忆一点颜色'
                    : '记住走过的地方'
              }}</small>
            </button>
          </div>
          <button
            type="button"
            class="undo-action"
            :disabled="!studio.objects.length"
            @click="undoLatest"
          >
            撤回最近一件
          </button>
          <div class="inspector-note">
            <span>你也许想记住</span>
            <p>TA 最爱晒太阳的地方，<br />或一起走过的那条小路。</p>
            <button type="button" @click="panel = 'memory'">把它写下来 ↗</button>
          </div>
        </div>
        <div v-else-if="panel === 'memory'" class="panel-body">
          <p class="panel-caption">把普通的一天收好</p>
          <h3>留一张记忆便笺</h3>
          <div v-if="linkedSources.length" class="source-tray">
            <span>从生命档案带入</span
            ><button
              v-for="source in linkedSources"
              :key="`${source.type}-${source.id}`"
              type="button"
              :class="{ selected: selectedSource?.id === source.id }"
              @click="useMemorySource(source)"
            >
              {{ source.label }}
            </button>
          </div>
          <form @submit.prevent="addNote">
            <label for="memory-name">这处风景，为谁而留</label
            ><input
              id="memory-name"
              v-model="studio.name"
              maxlength="16"
              :readonly="Boolean(memorial)"
              :placeholder="memorial ? memorial.petName : 'TA 的名字（选填）'"
            /><label for="memory-date">记忆发生的日期</label
            ><input id="memory-date" v-model="noteDate" type="date" required /><label
              for="memory-text"
              >想起的那个瞬间</label
            ><textarea
              id="memory-text"
              v-model="noteText"
              maxlength="180"
              required
              rows="5"
              placeholder="那天下午，你在窗边睡了很久…"
            ></textarea
            ><small class="character-count">{{ noteText.length }} / 180</small
            ><button class="primary-action" type="submit" :disabled="!noteText.trim()">
              收好这段记忆
            </button>
          </form>
          <p class="privacy-note">
            {{
              memorial
                ? '只同步到这一个纪念空间，不会进入公开社区。'
                : '只存在你的浏览器里，不会进入公开社区。'
            }}
          </p>
        </div>
        <div v-else class="panel-body">
          <p class="panel-caption">按照你的节奏</p>
          <h3>调整此刻的光</h3>
          <label for="scene-title">空间名称</label
          ><input id="scene-title" v-model="studio.title" maxlength="24" /><label for="scene-light"
            >光线 · {{ studio.light }}%</label
          ><input id="scene-light" v-model.number="studio.light" type="range" min="30" max="100" />
          <div class="light-presets">
            <button type="button" @click="studio.light = 100">日光</button
            ><button type="button" @click="studio.light = 70">黄昏</button
            ><button type="button" @click="studio.light = 40">夜灯</button>
          </div>
          <button
            class="wide-action"
            type="button"
            :aria-pressed="paused"
            @click="paused = !paused"
          >
            {{ paused ? '继续灯光动态' : '暂停灯光动态' }}</button
          ><button class="wide-action" type="button" @click="backup">下载空间与便笺备份</button>
          <p class="privacy-note">没有声音自动播放。照片场景不会模拟 TA 的意识或身份。</p>
        </div>
      </aside>
    </div>
    <section class="memory-shelf">
      <div class="shelf-heading">
        <div>
          <span class="studio-kicker">THE THINGS WE KEEP</span>
          <h2>风景里，藏着这些记忆。</h2>
        </div>
        <button type="button" @click="openMemoryPanel">写一张便笺 ↗</button>
      </div>
      <div v-if="!studio.notes.length" class="shelf-empty">
        <span>01</span>
        <p>
          一声呼噜，一次散步，一块被晒暖的地板。<br /><strong>不用写得特别，只要是真的。</strong>
        </p>
      </div>
      <div v-else class="note-grid">
        <article v-for="n in studio.notes" :key="n.id">
          <time>{{ n.date }}</time
          ><small v-if="n.sourceLabel">{{ n.sourceLabel }}</small>
          <p>{{ n.text }}</p>
          <button type="button" :aria-label="`删除 ${n.date} 的便笺`" @click="removeNote(n.id)">
            删除这张
          </button>
        </article>
      </div>
    </section>
    <div class="studio-links">
      <p>想和小伙伴玩一会儿？<RouterLink to="/habitat/play">打开轻量互动模式 ↗</RouterLink></p>
      <RouterLink v-if="memorialId" :to="`/editor/${memorialId}/room`"
        >回到 {{ memorial?.petName || '这处' }} 的纪念空间 ↗</RouterLink
      ><RouterLink v-else to="/account">回到我的纪念空间 ↗</RouterLink>
    </div>
  </div>
</template>

<style scoped>
.studio {
  max-width: 1200px;
}
.studio h1,
.studio h2,
.studio h3 {
  font-family: 'Microsoft YaHei', 'PingFang SC', sans-serif;
  font-weight: 500;
  letter-spacing: 0.01em;
}
.scene-label {
  max-width: 75%;
}
.scene-label h2 {
  font-size: 25px !important;
}
.studio-workspace {
  border-radius: 4px !important;
}
.scene-photo {
  display: none;
}
.artboard:after {
  background: linear-gradient(
    180deg,
    #09131940,
    transparent 32%,
    transparent 88%,
    #09131970
  ) !important;
}
.preview-mode .artboard {
  max-height: none !important;
}
.studio .keepsake {
  font-family: monospace;
}
.studio-heading h1 {
  font-size: 32px !important;
}
@media (max-width: 760px) {
  .studio-heading h1 {
    font-size: 22px !important;
  }
  .scene-label h2 {
    font-size: 16px !important;
  }
  .scene-label {
    top: 12px !important;
    left: 14px !important;
  }
  .scene-label > span {
    font-size: 8px;
  }
  .scene-label p {
    display: none;
  }
  .panel-body h3 {
    font-size: 20px;
  }
  .shelf-heading h2 {
    font-size: 19px !important;
  }
}
.studio {
  --studio-bg: #101d25;
  --studio-panel: #1c2931;
  --studio-text: #e8e3d9;
  --studio-muted: #a9b3b4;
  --studio-gold: #d1b684;
  --studio-line: #ffffff15;
  width: min(1440px, calc(100% - 64px));
  margin: auto;
  padding: 36px 0 64px;
  color: var(--studio-text);
}
.studio button {
  font: inherit;
  cursor: pointer;
  background: transparent;
  color: inherit;
  border: 1px solid var(--studio-line);
  border-radius: 5px;
  min-height: 40px;
  padding: 10px 14px;
  font-size: 12px;
  transition:
    background 0.2s,
    border-color 0.2s;
}
.studio button:hover:not(:disabled) {
  background: #ffffff0b;
  border-color: #d1b68470;
}
.studio button:disabled {
  opacity: 0.42;
  cursor: default;
}
.studio-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24px;
  margin-bottom: 27px;
}
.studio-kicker {
  font-size: 10px;
  letter-spacing: 0.16em;
  color: var(--studio-gold);
}
.studio-heading h1 {
  font-size: clamp(26px, 3vw, 40px);
  letter-spacing: -0.035em;
  margin: 12px 0 0;
}
.heading-tools {
  display: flex;
  gap: 20px;
  align-items: center;
}
.local-save {
  font-size: 10px;
  color: var(--studio-muted);
}
.studio-error {
  border-left: 2px solid #d1b684;
  padding: 10px 14px;
  background: #d1b68412;
  font-size: 11px;
  color: #d9d0c1;
  margin: -10px 0 18px;
}
.studio-error a {
  color: var(--studio-gold);
}
.studio-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 292px;
  border: 1px solid var(--studio-line);
  border-radius: 10px;
  overflow: hidden;
  background: #0c171e;
}
.artboard-wrap {
  min-width: 0;
}
.artboard {
  position: relative;
  isolation: isolate;
  aspect-ratio: 3/2;
  background: #0b151c;
  overflow: hidden;
}
.scene-photo {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: filter 0.7s;
}
.artboard:after {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: linear-gradient(180deg, #0913198c, transparent 40%, transparent 75%, #091319a3);
  z-index: 0;
}
.scene-label {
  position: absolute;
  left: 30px;
  top: 28px;
  pointer-events: none;
  z-index: 1;
  text-shadow: 0 2px 15px #000;
}
.scene-label > span {
  font-size: 10px;
  letter-spacing: 0.12em;
  color: #d2bc95;
}
.scene-label h2 {
  font-size: clamp(22px, 2.3vw, 34px);
  margin: 14px 0 8px;
}
.scene-label p {
  font-size: 12px;
  color: #c8cec8;
}
.scene-bottom {
  position: absolute;
  bottom: 18px;
  left: 24px;
  right: 24px;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 9px;
  color: #c4c9bf;
  z-index: 1;
  pointer-events: none;
}
.scene-loading {
  position: absolute;
  inset: 0;
  background: #17252d;
  display: grid;
  place-items: center;
  color: #b7c0b9;
  font-size: 13px;
  z-index: 2;
}
.placing {
  cursor: crosshair;
}
.placement-hint {
  position: absolute;
  left: 50%;
  bottom: 60px;
  transform: translateX(-50%);
  padding: 12px 18px;
  background: #132630e8;
  color: #e3cc9f;
  white-space: nowrap;
  font-size: 12px;
  pointer-events: none;
  border: 1px solid #d1b68455;
  z-index: 2;
}
.studio .keepsake {
  position: absolute;
  transform: translate(-50%, -50%);
  border: 0;
  font-size: 32px;
  width: 44px;
  height: 44px;
  padding: 0;
  line-height: 44px;
  z-index: 2;
  background: transparent;
}
.keepsake.light {
  color: #ffdb83;
  text-shadow:
    0 0 12px #e4bb72,
    0 0 32px #e4bb72;
  animation: lamplight 5s ease-in-out infinite;
}
.keepsake.flower {
  color: #e8c2ac;
  text-shadow: 0 2px 6px #101a20;
}
.keepsake.stone {
  color: #b8bba8;
  text-shadow: 0 2px 6px #101a20;
}
.studio .keepsake:disabled {
  opacity: 1;
}
.artboard-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 20px;
  gap: 12px;
  background: #142129;
  border-top: 1px solid var(--studio-line);
}
.scene-tabs {
  display: flex;
  gap: 6px;
}
.scene-tabs button {
  border: 0;
  color: #aab8b8;
}
.studio button[aria-pressed='true'] {
  color: var(--studio-gold);
  background: #d1b6840d;
  border-color: #d1b68455;
}
.studio-status {
  padding: 12px 22px;
  font-size: 11px;
  margin: 0;
  min-height: 48px;
  color: #aebbb7;
}
.inspector {
  background: var(--studio-panel);
  border-left: 1px solid var(--studio-line);
}
.inspector-heading {
  padding: 24px 22px 18px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
}
.inspector-heading small {
  font-size: 8px;
  letter-spacing: 0.18em;
  color: #849292;
}
.inspector-tabs {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  padding: 0 18px 16px;
  border-bottom: 1px solid var(--studio-line);
  gap: 5px;
}
.inspector-tabs button {
  border: 0;
}
.panel-body {
  padding: 24px 22px;
}
.panel-caption {
  font-size: 10px;
  color: var(--studio-gold);
  margin-bottom: 10px;
}
.panel-body h3 {
  font-size: 23px;
  margin-bottom: 12px;
}
.panel-body p {
  font-size: 11px;
  line-height: 1.9;
  color: #aab9b9;
}
.object-palette {
  display: grid;
  gap: 8px;
  margin: 22px 0 12px;
}
.object-palette button {
  display: grid;
  grid-template-columns: 40px 1fr;
  text-align: left;
  column-gap: 9px;
  padding: 14px 10px;
}
.object-palette span {
  grid-row: span 2;
  font-size: 29px;
  line-height: 40px;
  text-align: center;
  color: #d6ba8a;
}
.object-palette strong {
  font-size: 12px;
  font-weight: 500;
  align-self: end;
}
.object-palette small {
  font-size: 10px;
  color: #91a2a1;
  margin-top: 5px;
}
.studio .undo-action {
  width: 100%;
  font-size: 10px;
  border: 0;
  color: #abb7b7;
}
.inspector-note {
  border-top: 1px solid var(--studio-line);
  padding-top: 23px;
  margin-top: 20px;
}
.inspector-note > span {
  font-size: 10px;
  color: #d3be97;
}
.inspector-note p {
  margin: 10px 0;
}
.inspector-note button {
  border: 0;
  padding-left: 0;
  color: var(--studio-gold);
}
.source-tray {
  display: grid;
  gap: 6px;
  border-top: 1px solid var(--studio-line);
  border-bottom: 1px solid var(--studio-line);
  padding: 13px 0;
  margin: 16px 0;
}
.source-tray > span {
  font-size: 9px;
  color: #cdb78d;
}
.source-tray button {
  min-height: 31px;
  padding: 7px 9px;
  text-align: left;
  font-size: 10px;
  color: #c3ceca;
}
.source-tray button.selected {
  color: var(--studio-gold);
  border-color: #d1b68470;
  background: #d1b6840d;
}
.panel-body label {
  display: block;
  font-size: 11px;
  color: #b8c3c1;
  margin: 18px 0 8px;
}
.panel-body input:not([type='range']),
.panel-body textarea {
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  border: 1px solid #ffffff20;
  background: #13222b;
  color: #e6e3da;
  padding: 10px;
  font: inherit;
  font-size: 12px;
  border-radius: 5px;
}
.panel-body input[readonly] {
  opacity: 0.68;
}
.panel-body textarea {
  resize: vertical;
  line-height: 1.8;
}
.panel-body input[type='range'] {
  width: 100%;
  accent-color: #cfb681;
}
.character-count {
  display: block;
  text-align: right;
  font-size: 9px;
  color: #93a5a5;
  margin: 6px 0 14px;
}
.studio .primary-action {
  background: #d1b684;
  color: #182731;
  border-color: #d1b684;
  width: 100%;
}
.panel-body .privacy-note {
  font-size: 10px;
  margin-top: 20px;
  color: #96a7a7;
}
.light-presets {
  display: flex;
  gap: 7px;
  margin: 16px 0 24px;
}
.light-presets button {
  flex: 1;
  padding: 8px;
}
.wide-action {
  width: 100%;
  margin-top: 10px;
}
.memory-shelf {
  margin-top: 46px;
}
.shelf-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}
.shelf-heading h2 {
  font-size: 27px;
  margin: 12px 0 0;
}
.shelf-heading button {
  border: 0;
  color: var(--studio-gold);
}
.shelf-empty {
  display: flex;
  gap: 28px;
  align-items: center;
  border-top: 1px solid var(--studio-line);
  border-bottom: 1px solid var(--studio-line);
  padding: 28px 0;
  margin-top: 24px;
}
.shelf-empty > span {
  font:
    italic 40px Georgia,
    serif;
  color: #81908e;
}
.shelf-empty p {
  font-size: 13px;
  margin: 0;
  color: #aab8b4;
}
.shelf-empty strong {
  font-weight: 400;
  color: #d3d3c7;
}
.note-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 28px;
  margin-top: 28px;
}
.note-grid article {
  border-top: 1px solid #d1b6846b;
  padding: 20px 0;
  min-width: 0;
}
.note-grid time,
.note-grid small {
  display: block;
  font-size: 10px;
  color: #cdb78d;
}
.note-grid small {
  margin-top: 8px;
  color: #879491;
}
.note-grid p {
  font-size: 14px;
  color: #d0d7cf;
  margin: 16px 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.note-grid button {
  font-size: 10px;
  padding: 5px 0;
  border: 0;
  color: #9caaa4;
}
.studio-links {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  margin-top: 36px;
  font-size: 11px;
  color: #c5b38f;
}
.studio-links p {
  font-size: 11px;
  margin: 0;
}
.studio-links a {
  margin-left: 10px;
  color: #ccb68a;
}
.preview-mode .studio-workspace {
  grid-template-columns: 1fr;
}
.preview-mode .artboard {
  max-height: 78dvh;
}
.motion-paused * {
  animation-play-state: paused !important;
}
@keyframes lamplight {
  50% {
    text-shadow:
      0 0 16px #e4bb72,
      0 0 42px #e4bb7280;
  }
}
@media (prefers-reduced-motion: reduce) {
  .studio * {
    animation: none !important;
    transition: none !important;
  }
}
@media (min-width: 1600px) {
  .studio-workspace {
    grid-template-columns: minmax(0, 1fr) 330px;
  }
}
@media (max-width: 1020px) {
  .studio {
    width: calc(100% - 32px);
  }
  .studio-workspace {
    grid-template-columns: minmax(0, 1fr) 260px;
  }
  .local-save {
    display: none;
  }
  .panel-body {
    padding: 20px 16px;
  }
  .scene-label {
    top: 18px;
    left: 20px;
  }
  .scene-label p {
    display: none;
  }
  .artboard-toolbar {
    padding: 10px;
    flex-wrap: wrap;
  }
  .artboard-toolbar button {
    font-size: 10px;
    padding: 8px;
  }
  .studio-heading h1 {
    font-size: 30px;
  }
}
@media (max-width: 760px) {
  .studio {
    padding-top: 22px;
    width: calc(100% - 28px);
  }
  .studio-heading {
    gap: 12px;
    margin-bottom: 22px;
  }
  .studio-heading h1 {
    font-size: 25px;
    line-height: 1.4;
  }
  .studio-kicker {
    font-size: 8px;
  }
  .heading-tools button {
    white-space: nowrap;
    padding: 8px;
    font-size: 10px;
  }
  .studio-workspace {
    grid-template-columns: 1fr;
  }
  .artboard {
    aspect-ratio: 3/2;
  }
  .scene-label h2 {
    font-size: 22px;
    margin-top: 8px;
  }
  .scene-label > span {
    font-size: 8px;
  }
  .scene-bottom {
    font-size: 8px;
    left: 16px;
    right: 16px;
    bottom: 12px;
  }
  .inspector {
    border-left: 0;
    border-top: 1px solid var(--studio-line);
  }
  .inspector-heading {
    padding: 18px 20px 12px;
  }
  .inspector-tabs {
    padding-bottom: 12px;
  }
  .panel-body {
    padding: 20px;
  }
  .object-palette {
    grid-template-columns: repeat(3, 1fr);
    gap: 8px;
    margin-top: 16px;
  }
  .object-palette button {
    display: flex;
    flex-direction: column;
    text-align: center;
    align-items: center;
    padding: 12px 5px;
  }
  .object-palette strong {
    align-self: auto;
  }
  .object-palette small {
    font-size: 8px;
  }
  .inspector-note {
    margin-top: 10px;
    padding-top: 15px;
  }
  .inspector-note p br {
    display: none;
  }
  .note-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }
  .shelf-heading h2 {
    font-size: 23px;
  }
  .shelf-heading button {
    font-size: 10px;
    white-space: nowrap;
    padding: 0;
  }
  .studio-links {
    flex-direction: column;
    align-items: start;
  }
  .studio-links > a {
    margin-left: 0;
  }
  .studio-status {
    padding: 10px 14px;
    font-size: 10px;
  }
  .placement-hint {
    font-size: 10px;
    bottom: 40px;
    padding: 8px;
  }
  .studio .keepsake {
    font-size: 24px;
  }
  .memory-shelf {
    margin-top: 32px;
  }
}
</style>
