<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { habitatConfig as config } from '@/config/pocketHabitat'
import paludariumImage from '@/assets/pocket-paludarium-v1.png'

const state = reactive({
  pet: 'cat',
  name: '麦芽',
  landscape: 'forest',
  night: false,
  plants: 5,
  journal: [] as string[],
})
const pet = computed(() => config.pets.find((p) => p.id === state.pet) ?? config.pets[0])
const landscape = computed(
  () => config.landscapes.find((p) => p.id === state.landscape) ?? config.landscapes[0],
)
const paused = ref(false)
const reduced = ref(false)
const hidden = ref(false)
const action = ref('')
const fishFeeding = ref(false)
const notice = ref('挑一个小伙伴，今天就在这里坐一会儿。')
const saveStatus = ref('布置保存在当前浏览器')
const nameDraft = ref('')
const editing = ref(false)
let actionTimer: ReturnType<typeof setTimeout> | undefined
let fishTimer: ReturnType<typeof setTimeout> | undefined
let media: MediaQueryList | undefined
const stopMotion = computed(() => paused.value || reduced.value || hidden.value)
function syncMotion() {
  reduced.value = media?.matches ?? false
}
function visibility() {
  hidden.value = document.hidden
}
onMounted(() => {
  media = window.matchMedia('(prefers-reduced-motion: reduce)')
  syncMotion()
  media.addEventListener('change', syncMotion)
  document.addEventListener('visibilitychange', visibility)
  try {
    const saved = JSON.parse(localStorage.getItem(config.storageKey) ?? 'null')
    if (saved && typeof saved === 'object') {
      if (config.pets.some((p) => p.id === saved.pet)) state.pet = saved.pet
      if (typeof saved.name === 'string' && saved.name.trim()) state.name = saved.name.slice(0, 12)
      if (config.landscapes.some((p) => p.id === saved.landscape)) state.landscape = saved.landscape
      if (typeof saved.night === 'boolean') state.night = saved.night
      if (Number.isInteger(saved.plants)) state.plants = Math.max(3, Math.min(8, saved.plants))
      if (Array.isArray(saved.journal))
        state.journal = saved.journal.filter((s: unknown) => typeof s === 'string').slice(0, 8)
    }
  } catch {
    saveStatus.value = '暂时无法读取布置，你仍可在这里体验。'
  }
})
watch(
  state,
  () => {
    try {
      localStorage.setItem(config.storageKey, JSON.stringify(state))
      saveStatus.value = '布置已保存在当前浏览器'
    } catch {
      saveStatus.value = '浏览器未能保存，刷新后可能丢失本次布置。'
    }
  },
  { deep: true },
)
onUnmounted(() => {
  clearTimeout(actionTimer)
  clearTimeout(fishTimer)
  media?.removeEventListener('change', syncMotion)
  document.removeEventListener('visibilitychange', visibility)
})
function record(text: string) {
  notice.value = text
  state.journal.unshift(
    `${new Date().toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })} · ${text}`,
  )
  state.journal = state.journal.slice(0, 8)
}
function interact(kind: string) {
  clearTimeout(actionTimer)
  action.value = kind
  record(
    kind === 'feed'
      ? `${state.name}吃了点零食，满足地眯起眼睛。`
      : kind === 'play'
        ? `${state.name}发现了小球，朝你凑了过来。`
        : `${state.name}安静地靠在你身边。`,
  )
  actionTimer = setTimeout(() => {
    action.value = ''
  }, 4000)
}
function choosePet(id: string) {
  const choice = config.pets.find((p) => p.id === id)
  if (!choice) return
  state.pet = choice.id
  state.name = choice.name
  action.value = ''
  editing.value = false
  notice.value = `${choice.name}在这里等你，随时可以给它起个新名字。`
}
function rename() {
  if (!nameDraft.value.trim()) return
  state.name = nameDraft.value.trim().slice(0, 12)
  editing.value = false
  record(`从今天起，小伙伴叫${state.name}。`)
}
function toggleRename() {
  editing.value = !editing.value
  nameDraft.value = state.name
}
function feedFish() {
  if (fishFeeding.value) return
  fishFeeding.value = true
  record('撒下一点鱼食，小鱼们向水面聚拢。')
  fishTimer = setTimeout(() => {
    fishFeeding.value = false
  }, 4500)
}
function addPlant() {
  if (state.plants >= 8) return
  state.plants += 1
  record('种下一株水草，水下又多了一点绿色。')
}
function removePlant() {
  if (state.plants <= 3) return
  state.plants -= 1
  record('疏开一点水草，给小鱼留出游动的空间。')
}
function clearJournal() {
  state.journal = []
  notice.value = '相处记录已清空，伙伴和布景仍然保留。'
}
</script>

<template>
  <div class="habitat section" :class="{ 'is-night': state.night, 'is-paused': stopMotion }">
    <header class="habitat-intro">
      <div>
        <p class="eyebrow">POCKET HABITAT / 口袋生态</p>
        <h1>给日常，<em>留一点生机。</em></h1>
        <p>摸摸小伙伴，看一会儿水草。没有需要赶上的进度，只有刚好属于你的片刻。</p>
      </div>
      <div class="habitat-switches">
        <button type="button" :aria-pressed="state.night" @click="state.night = !state.night">
          {{ state.night ? '月光 · 切换日光' : '日光 · 切换月光' }}</button
        ><button type="button" :aria-pressed="paused" @click="paused = !paused">
          {{ paused ? '继续动态' : '暂停动态' }}
        </button>
      </div>
    </header>
    <section class="habitat-showcase" aria-label="生态缸造景灵感">
      <img
        :src="paludariumImage"
        width="1536"
        height="1024"
        loading="lazy"
        decoding="async"
        fetchpriority="low"
        alt="暖金灯光下的玻璃生态缸，苔藓、沉木与溪石围绕一湾浅水的概念造景图"
      />
      <div class="showcase-copy">
        <span>微景观 / 01</span>
        <h2>把一座森林，<br />安放在桌边。</h2>
        <p>从一束光、一株水草开始，<br />布置你和小伙伴的日常。</p>
        <a href="#habitat-playground" class="button">开始布置 ↘</a>
      </div>
      <span class="showcase-caption">苔岸 · 概念造景图</span>
    </section>
    <div id="habitat-playground" class="playground-heading">
      <h2>你的陪伴角落</h2>
      <span>选一个伙伴，慢慢布置</span>
    </div>
    <div class="habitat-layout">
      <section class="pet-room" aria-labelledby="pet-heading">
        <div class="scene-heading"><span>01 / 云养小伙伴</span><span>虚拟陪伴</span></div>
        <div class="pet-art" :class="'action-' + action" :style="{ '--pet-color': pet.color }">
          <svg viewBox="0 0 500 390" role="img" :aria-label="`${state.name}坐在窗边的软垫上`">
            <defs>
              <linearGradient id="pocket-window" x2="0" y2="1">
                <stop stop-color="#809b97" />
                <stop offset="1" stop-color="#d2bd91" />
              </linearGradient>
              <radialGradient id="pocket-rug">
                <stop stop-color="#526269" />
                <stop offset="1" stop-color="#293b45" />
              </radialGradient>
            </defs>
            <rect x="112" y="18" width="270" height="258" rx="120" fill="#263d47" />
            <path
              d="M130 252V143a117 117 0 0 1 234 0v109Z"
              fill="url(#pocket-window)"
              class="window-light"
            />
            <circle cx="297" cy="87" r="26" fill="#f1d79e" />
            <path d="M130 225q65-80 133-32t101-26v85H130" fill="#698681" opacity=".7" />
            <path d="M247 29v228M129 156h236" stroke="#30454e" stroke-width="8" />
            <ellipse cx="250" cy="329" rx="191" ry="44" fill="url(#pocket-rug)" />
            <g class="little-pet">
              <circle
                v-if="state.pet === 'rabbit'"
                cx="318"
                cy="294"
                r="20"
                fill="var(--pet-color)"
              />
              <path
                v-else
                d="M307 300q83-18 37-65"
                fill="none"
                stroke="var(--pet-color)"
                stroke-width="23"
                stroke-linecap="round"
                class="pet-tail"
              />
              <ellipse cx="250" cy="275" rx="75" ry="61" fill="var(--pet-color)" />
              <ellipse cx="246" cy="286" rx="44" ry="43" fill="#eee0c8" />
              <path
                v-if="state.pet === 'cat'"
                d="m185 191-1-74 53 43m29 0 49-43-1 75"
                fill="var(--pet-color)"
                stroke="#b68e66"
                stroke-width="4"
              />
              <g v-if="state.pet === 'rabbit'" fill="var(--pet-color)">
                <ellipse cx="214" cy="135" rx="21" ry="67" transform="rotate(-10 214 135)" />
                <ellipse cx="277" cy="135" rx="21" ry="67" transform="rotate(10 277 135)" />
              </g>
              <ellipse cx="250" cy="203" rx="73" ry="65" fill="var(--pet-color)" />
              <g v-if="state.pet === 'dog'" fill="#805e49">
                <ellipse cx="181" cy="196" rx="22" ry="53" transform="rotate(16 181 196)" />
                <ellipse cx="318" cy="196" rx="22" ry="53" transform="rotate(-16 318 196)" />
              </g>
              <g class="pet-eyes" stroke="#343a39" stroke-width="5" stroke-linecap="round">
                <path d="M215 202v6m68-6v6" />
              </g>
              <path
                d="m244 220 6 5 6-5m-6 5v8m0 0q-10 8-17 0m17 0q10 8 17 0"
                fill="none"
                stroke="#765d52"
                stroke-width="3"
                stroke-linecap="round"
              />
              <ellipse cx="215" cy="323" rx="29" ry="13" fill="var(--pet-color)" />
              <ellipse cx="284" cy="323" rx="29" ry="13" fill="var(--pet-color)" />
            </g>
            <g v-if="action === 'feed'">
              <ellipse cx="250" cy="351" rx="43" ry="12" fill="#cfae76" />
              <path d="m214 351 10 18h52l10-18" fill="#8e795a" />
              <ellipse cx="250" cy="350" rx="25" ry="6" fill="#876243" />
            </g>
            <circle
              v-if="action === 'play'"
              class="play-ball"
              cx="368"
              cy="330"
              r="19"
              fill="#d3a778"
            />
            <g v-if="action === 'pet'" fill="#d8b77e" class="pet-affection">
              <path d="M344 174c-22-19-35 6 0 23 35-17 22-42 0-23" />
            </g>
          </svg>
        </div>
        <div class="pet-description">
          <div>
            <h2 id="pet-heading">{{ state.name }}</h2>
            <button type="button" class="rename-link" @click="toggleRename">起个名字</button>
          </div>
          <p>{{ pet.description }}</p>
        </div>
        <form v-if="editing" class="rename-form" @submit.prevent="rename">
          <label for="pet-name">小伙伴的名字</label
          ><input id="pet-name" v-model="nameDraft" maxlength="12" required /><button type="submit">
            保存名字
          </button>
        </form>
        <div class="pet-choices" aria-label="选择虚拟伙伴">
          <button
            v-for="p in config.pets"
            :key="p.id"
            type="button"
            :aria-pressed="state.pet === p.id"
            @click="choosePet(p.id)"
          >
            {{ p.label }}
          </button>
        </div>
        <div class="care-actions">
          <button type="button" @click="interact('pet')">摸摸它</button
          ><button type="button" @click="interact('feed')">喂点零食</button
          ><button type="button" @click="interact('play')">一起玩球</button>
        </div>
      </section>
      <section class="aquarium-room" aria-labelledby="tank-heading">
        <div class="scene-heading"><span>02 / 桌面生态缸</span><span>数字造景</span></div>
        <div class="tank-title">
          <h2 id="tank-heading">一小片水下森林。</h2>
          <p>{{ landscape.description }}</p>
        </div>
        <div
          class="tank-art"
          :class="{ feeding: fishFeeding }"
          :style="{ '--plant-color': landscape.plant }"
        >
          <svg viewBox="0 0 740 460" role="img" aria-label="玻璃生态缸里，小鱼在水草和溪石之间游动">
            <defs>
              <linearGradient id="tank-water" x2="0" y2="1">
                <stop stop-color="#497477" stop-opacity=".7" />
                <stop offset="1" stop-color="#152f36" />
              </linearGradient>
              <linearGradient id="tank-glass">
                <stop stop-color="#b2d6cc" stop-opacity=".26" />
                <stop offset=".5" stop-color="#d0e6d7" stop-opacity=".04" />
                <stop offset="1" stop-color="#aecbbb" stop-opacity=".23" />
              </linearGradient>
            </defs>
            <ellipse cx="372" cy="420" rx="301" ry="23" fill="#0b1920" opacity=".7" />
            <path
              d="M62 83 130 42h546l-64 41Z"
              fill="url(#tank-glass)"
              stroke="#8caeaa"
              stroke-opacity=".45"
            />
            <path
              d="m612 83 64-41v317l-64 40Z"
              fill="#25434b"
              stroke="#8caeaa"
              stroke-opacity=".4"
            />
            <rect
              x="62"
              y="83"
              width="550"
              height="316"
              rx="5"
              fill="url(#tank-water)"
              stroke="#9cbbb3"
              stroke-opacity=".55"
            />
            <path d="M63 118h548l63-39H128Z" fill="#9bb6a5" opacity=".14" />
            <path d="M64 346q95-42 178-12t167 9 202-6v60H64Z" fill="#746e57" />
            <path d="M64 368q89-15 150-7t159 8 238-13v42H64" fill="#a19a78" opacity=".5" />
            <g :opacity="state.landscape === 'river' ? 0.55 : 1">
              <g
                v-for="n in state.plants"
                :key="n"
                :transform="`translate(${98 + n * 53} ${365 - (n % 3) * 8})`"
              >
                <g
                  class="water-plant"
                  :style="{ '--delay': `${-n * 1.3}s` }"
                  fill="var(--plant-color)"
                >
                  <path d="M0 0Q-20-93 0-157Q16-77 0 0" />
                  <path
                    d="M0-35Q-66-60-45-116Q-8-95 0-35M0-64Q50-85 39-140Q4-121 0-64M0-6Q-51-25-54-65Q-8-59 0-6"
                    opacity=".8"
                  />
                </g>
              </g>
            </g>
            <path d="M192 361q35-52 83-41l45 44Z" fill="#384d4a" />
            <path d="m239 327 29-9 38 37-49-14Z" fill="#60736b" />
            <path d="M417 363q30-82 74-52l47 57Z" fill="#556660" />
            <path d="m451 319 32-15 24 36-38-12Z" fill="#7b8980" />
            <g
              v-for="n in 5"
              :key="n"
              :transform="`translate(${120 + n * 76} ${150 + (n % 3) * 47})`"
            >
              <g
                class="swimming-fish"
                :style="{ '--delay': `${-n * 2.7}s`, '--duration': `${12 + n}s` }"
              >
                <path d="M-20 0-38-15v30Z" :fill="n % 2 ? '#d9a47a' : '#8bbeb8'" />
                <ellipse rx="24" ry="12" :fill="n % 2 ? '#e7bc8c' : '#bad8cc'" />
                <circle cx="13" cy="-2" r="2" fill="#243b3e" />
                <path d="m-5-9 6-9 8 9" fill="#dfc49c" opacity=".7" />
              </g>
            </g>
            <g v-if="fishFeeding" fill="#dac29a" class="fish-food">
              <circle v-for="n in 9" :key="n" :cx="238 + n * 15" :cy="125 + (n % 3) * 9" r="2.5" />
            </g>
            <path
              d="M76 100v282M595 98v285M83 92h505"
              fill="none"
              stroke="#d7ece0"
              stroke-opacity=".17"
              stroke-width="3"
            />
            <path d="m108 120 40 0-35 195H83Z" fill="#cfdec0" opacity=".04" />
          </svg>
        </div>
        <div class="landscape-choices" aria-label="生态缸造景">
          <button
            v-for="l in config.landscapes"
            :key="l.id"
            type="button"
            :aria-pressed="state.landscape === l.id"
            @click="state.landscape = l.id"
          >
            {{ l.label }}
          </button>
        </div>
        <div class="tank-actions">
          <button type="button" :disabled="fishFeeding" @click="feedFish">
            {{ fishFeeding ? '小鱼正在吃饭' : '投喂小鱼' }}</button
          ><button type="button" :disabled="state.plants >= 8" @click="addPlant">添一株水草</button
          ><button type="button" :disabled="state.plants <= 3" @click="removePlant">
            疏一疏水草
          </button>
        </div>
        <div class="tank-caption">
          <span>水草 {{ state.plants }} 株 · 小鱼 5 尾</span><span>离开后，它们也会好好生活</span>
        </div>
      </section>
    </div>
    <div class="habitat-feedback" role="status">
      <span class="quiet-dot" aria-hidden="true"></span>{{ notice }}<small>{{ saveStatus }}</small>
    </div>
    <section class="habitat-journal">
      <div>
        <p class="eyebrow">LITTLE MOMENTS</p>
        <h2>一些小小的相处。</h2>
        <p>没有签到、饥饿惩罚或离线倒计时。<br />想回来时，再回来就好。</p>
        <RouterLink class="text-link" to="/account">回到我的纪念空间 ↗</RouterLink>
      </div>
      <div>
        <ul v-if="state.journal.length">
          <li v-for="(entry, i) in state.journal" :key="i">{{ entry }}</li>
        </ul>
        <p v-else class="journal-empty">第一次摸摸它、第一次投喂，<br />都会成为这里的小记录。</p>
        <button
          v-if="state.journal.length"
          type="button"
          class="clear-journal"
          @click="clearJournal"
        >
          清空相处记录
        </button>
      </div>
    </section>
    <p class="habitat-note">
      这里的小伙伴是独立的虚拟角色，不是对纪念对象的生成或复现。当前布置仅存于此浏览器，尚不跨设备同步。生态缸为艺术化互动场景，不提供真实养宠参数。
    </p>
  </div>
</template>

<style scoped>
.habitat-layout {
  align-items: start;
}
.habitat-showcase {
  position: relative;
  isolation: isolate;
  overflow: hidden;
  border-radius: 16px;
  min-height: 480px;
  background: #0d1a21;
  margin-bottom: 46px;
}
.habitat-showcase img {
  position: absolute;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center;
  inset: 0;
}
.habitat-showcase::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, #0e1c28e6, transparent 67%);
  z-index: 0;
}
.showcase-copy {
  position: relative;
  z-index: 1;
  padding: 74px 40px;
  max-width: 450px;
}
.showcase-copy > span {
  font-size: 11px;
  letter-spacing: 0.14em;
  color: #cdb78e;
}
.showcase-copy h2 {
  margin: 24px 0;
  font-size: clamp(34px, 4vw, 49px);
  line-height: 1.3;
}
.showcase-copy p {
  color: #c3cbc7;
  font-size: 14px;
}
.showcase-copy .button {
  margin-top: 12px;
}
.showcase-caption {
  position: absolute;
  z-index: 1;
  bottom: 22px;
  right: 24px;
  color: #c6cabe;
  font-size: 10px;
}
.playground-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
  scroll-margin-top: 24px;
}
.playground-heading h2 {
  font-size: 28px;
  margin: 0;
}
.playground-heading > span {
  font-size: 12px;
  color: #a7b8ad;
}
.is-night .habitat-showcase img {
  filter: brightness(0.65);
}
@media (max-width: 760px) {
  .habitat-showcase {
    min-height: 450px;
    margin-bottom: 30px;
  }
  .habitat-showcase img {
    object-position: 61% center;
  }
  .showcase-copy {
    padding: 34px 22px;
    max-width: 280px;
  }
  .showcase-copy h2 {
    font-size: 34px;
  }
  .habitat-showcase::after {
    background: linear-gradient(90deg, #0e1c28df, #0e1c2820);
  }
  .showcase-caption {
    bottom: 16px;
    right: 16px;
  }
  .playground-heading > span {
    font-size: 10px;
  }
  .playground-heading h2 {
    font-size: 24px;
  }
}
.habitat {
  --habitat-gold: #d7b980;
  --habitat-line: #ffffff16;
  padding-top: 48px;
  color: #eee9df;
}
.habitat-intro {
  display: flex;
  justify-content: space-between;
  align-items: end;
  gap: 30px;
  margin-bottom: 34px;
}
.habitat-intro h1 {
  font-size: clamp(34px, 4vw, 55px);
  letter-spacing: -0.035em;
}
.habitat-intro h1 em {
  color: var(--habitat-gold);
}
.habitat-intro p {
  max-width: 610px;
  font-size: 14px;
  margin-bottom: 0;
}
.habitat-intro .eyebrow {
  font-size: 10px;
  margin-bottom: 18px;
}
.habitat button {
  border: 1px solid var(--habitat-line);
  background: transparent;
  color: #d4dcd8;
  border-radius: 8px;
  min-height: 42px;
  padding: 9px 15px;
  font-size: 12px;
  transition:
    background 0.2s,
    transform 0.2s;
}
.habitat button:hover:not(:disabled) {
  background: #ffffff0d;
  transform: translateY(-1px);
}
.habitat button[aria-pressed='true'] {
  color: var(--habitat-gold);
  border-color: #d7b98066;
  background: #d7b9800c;
}
.habitat-switches {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
.habitat-layout {
  display: grid;
  grid-template-columns: minmax(0, 0.85fr) minmax(0, 1.45fr);
  gap: 22px;
}
.pet-room,
.aquarium-room {
  min-width: 0;
  border: 1px solid var(--habitat-line);
  border-radius: 18px;
  overflow: hidden;
  background: #1a2b33;
}
.pet-room {
  background: linear-gradient(145deg, #283c41, #1b2c35 70%);
  padding: 24px;
}
.aquarium-room {
  padding: 24px 28px;
  background: radial-gradient(ellipse at 55% 45%, #28484b, #182c34 72%);
}
.scene-heading {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 10px;
  letter-spacing: 0.09em;
  color: #acbcb6;
}
.scene-heading span:last-child {
  color: #849c98;
}
.pet-art {
  margin: 0 -15px;
}
.pet-art svg {
  display: block;
  width: 100%;
  max-height: 312px;
}
.pet-description > div {
  display: flex;
  align-items: center;
  gap: 15px;
}
.pet-description h2 {
  font-size: 30px;
  margin: 6px 0;
}
.habitat .rename-link {
  min-height: 32px;
  border: 0;
  color: #bcc7bf;
  font-size: 11px;
  padding: 0;
}
.pet-description p {
  font-size: 12px;
  margin-bottom: 18px;
  color: #b4c4be;
}
.pet-choices,
.care-actions,
.landscape-choices,
.tank-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.pet-choices button {
  flex: 1;
}
.care-actions {
  margin-top: 12px;
}
.care-actions button {
  flex: 1;
  background: #d7b98015;
  color: #ead1a4;
  border-color: transparent;
  white-space: nowrap;
  padding: 10px;
}
.tank-title {
  margin: 28px 0 0;
}
.tank-title h2 {
  font-size: 32px;
  margin-bottom: 9px;
}
.tank-title p {
  font-size: 12px;
  color: #b4c4be;
}
.tank-art {
  margin: -10px -22px 0;
}
.tank-art svg {
  display: block;
  width: 100%;
}
.landscape-choices {
  border-bottom: 1px solid var(--habitat-line);
  padding-bottom: 18px;
}
.landscape-choices button {
  border: 0;
  border-radius: 4px;
}
.tank-actions {
  margin-top: 18px;
}
.tank-actions button:first-child {
  background: var(--habitat-gold);
  color: #203238;
  border-color: var(--habitat-gold);
}
.tank-caption {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  color: #a7b9b0;
  font-size: 10px;
  margin-top: 20px;
}
.habitat-feedback {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 0;
  font-size: 12px;
  color: #d2dace;
  min-height: 64px;
}
.quiet-dot {
  height: 6px;
  width: 6px;
  background: #a2bfa1;
  border-radius: 50%;
  flex-shrink: 0;
}
.habitat-feedback small {
  margin-left: auto;
  color: #9aaea5;
  font-size: 10px;
}
.habitat-journal {
  display: grid;
  grid-template-columns: 0.85fr 1.45fr;
  gap: 44px;
  border-top: 1px solid var(--habitat-line);
  padding: 45px 0 25px;
}
.habitat-journal h2 {
  font-size: 30px;
}
.habitat-journal p {
  font-size: 13px;
}
.habitat-journal ul {
  list-style: none;
  padding: 0;
  margin: 0;
}
.habitat-journal li {
  font-size: 12px;
  padding: 14px 0;
  border-bottom: 1px solid var(--habitat-line);
  color: #bac6bf;
  line-height: 1.7;
}
.journal-empty {
  padding: 20px 0;
}
.habitat .clear-journal {
  margin-top: 16px;
  border: 0;
  padding-left: 0;
  color: #a7b8ae;
}
.habitat-note {
  font-size: 11px;
  margin: 20px 0 0;
  max-width: 820px;
  color: #93a69d;
}
.rename-form {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin: 12px 0;
}
.rename-form label {
  width: 100%;
  font-size: 12px;
}
.rename-form input {
  width: 65%;
  min-width: 0;
  background: #152630;
  color: #eee;
  border: 1px solid #ffffff30;
  border-radius: 6px;
  padding: 8px;
}
.window-light {
  transition: opacity 1s;
}
.is-night .window-light {
  opacity: 0.32;
}
.is-night .tank-art {
  filter: brightness(0.68);
}
.tank-art {
  transition: filter 1s;
}
.water-plant {
  transform-origin: 0 0;
  animation: plant-drift 7s ease-in-out infinite;
  animation-delay: var(--delay);
}
.swimming-fish {
  animation: fish-drift var(--duration) ease-in-out infinite;
  animation-delay: var(--delay);
}
.pet-tail {
  transform-origin: 307px 300px;
  animation: tail-drift 5s ease-in-out infinite;
}
.pet-eyes {
  transform-origin: 250px 205px;
  animation: pet-blink 7s infinite;
}
.action-pet .little-pet {
  transform: rotate(-3deg);
  transform-origin: 250px 330px;
}
.action-feed .little-pet {
  transform: translateY(6px);
}
.little-pet {
  transition: transform 0.5s;
}
.play-ball {
  animation: ball-play 1s ease-in-out infinite alternate;
}
.fish-food {
  animation: food-drop 4.5s linear forwards;
}
.feeding .swimming-fish {
  animation: fish-eat 1s ease-in-out infinite alternate;
}
.is-paused *,
.is-paused *::before,
.is-paused *::after {
  animation-play-state: paused !important;
}
.is-paused .little-pet {
  transition: none;
}
@keyframes plant-drift {
  50% {
    transform: skewX(5deg);
  }
}
@keyframes fish-drift {
  0%,
  100% {
    transform: translate(-32px, 0);
  }
  50% {
    transform: translate(35px, -12px);
  }
}
@keyframes tail-drift {
  50% {
    transform: rotate(9deg);
  }
}
@keyframes pet-blink {
  0%,
  43%,
  47%,
  100% {
    transform: scaleY(1);
  }
  45% {
    transform: scaleY(0.1);
  }
}
@keyframes ball-play {
  to {
    transform: translate(-65px, -17px);
  }
}
@keyframes food-drop {
  to {
    transform: translateY(110px);
    opacity: 0;
  }
}
@keyframes fish-eat {
  to {
    transform: translate(8px, -24px);
  }
}
@media (prefers-reduced-motion: reduce) {
  .habitat * {
    animation: none !important;
    transition: none !important;
  }
}
@media (max-width: 1050px) {
  .habitat-intro {
    align-items: start;
    flex-direction: column;
  }
  .habitat-layout {
    grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
  }
  .tank-title h2 {
    font-size: 27px;
  }
  .tank-caption {
    flex-direction: column;
    gap: 8px;
  }
  .pet-room {
    padding: 20px;
  }
  .aquarium-room {
    padding: 20px;
  }
}
@media (max-width: 760px) {
  .habitat {
    padding-top: 30px;
  }
  .habitat-layout,
  .habitat-journal {
    grid-template-columns: 1fr;
  }
  .habitat-intro {
    gap: 20px;
  }
  .habitat-intro h1 {
    font-size: 36px;
  }
  .pet-art svg {
    max-height: 300px;
  }
  .tank-art {
    margin: 0 -15px;
  }
  .habitat-feedback {
    flex-wrap: wrap;
    line-height: 1.7;
  }
  .habitat-feedback small {
    width: 100%;
    margin-left: 18px;
  }
  .habitat-journal {
    gap: 10px;
  }
  .tank-caption {
    flex-direction: row;
    font-size: 9px;
  }
  .habitat-intro p {
    font-size: 13px;
  }
  .tank-actions button {
    padding: 9px 11px;
  }
  .habitat-journal {
    padding-top: 32px;
  }
}
</style>
