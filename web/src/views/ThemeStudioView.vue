<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import {
  getAccountCapabilities,
  getLetter,
  getMemorial,
  getMemorialExperience,
  listGallery,
  updateMemorial,
  type AccountCapabilities,
  type GalleryItem,
  type Memorial,
  type MemorialExperience,
  type MemorialLetter,
} from '@/api/memorials'
import {
  hasThemeAccess,
  memorialThemeFor,
  memorialThemes,
  themeClass,
  themeStyleVars,
  type MemorialThemeCode,
} from '@/config/memorialThemes'

const route = useRoute()
const memorialId = computed(() => String(route.params.id))
const memorial = ref<Memorial | null>(null)
const experience = ref<MemorialExperience>({ dayMoments: [], lifeDetails: [], sounds: [], interviewAnswers: [], keepsakes: [], burial: null })
const gallery = ref<GalleryItem[]>([])
const letter = ref<MemorialLetter | null>(null)
const capabilities = ref<AccountCapabilities | null>(null)
const selectedTheme = ref<MemorialThemeCode>('NIGHT')
const entryTheme = ref<MemorialThemeCode>('NIGHT')
const previewDevice = ref<'DESKTOP' | 'MOBILE'>('DESKTOP')
const loading = ref(true)
const saving = ref(false)
const notice = ref('')
const error = ref('')

const selected = computed(() => memorialThemeFor(selectedTheme.value))
const cover = computed(() => memorial.value?.coverImageUrl || gallery.value.find((item) => item.contentType.startsWith('image/'))?.mediaUrl || '')
const firstMoment = computed(() => experience.value.dayMoments[0])
const lifeLine = computed(() => experience.value.lifeDetails[0]?.answer || memorial.value?.aboutTa || '')
const previewQuote = computed(() => firstMoment.value?.story || firstMoment.value?.title || lifeLine.value || memorial.value?.farewellMessage || '先放进一个只有你记得的生活细节。')
const previewLetter = computed(() => letter.value?.body || memorial.value?.farewellMessage || '有些话，可以慢慢写给 TA。')
const hasUnsavedSelection = computed(() => selectedTheme.value !== memorial.value?.theme)
const canSaveSelected = computed(() => hasThemeAccess(selected.value, capabilities.value))
const saveLabel = computed(() => {
  if (!canSaveSelected.value) return '解锁外观作品集'
  if (!hasUnsavedSelection.value) return '已保存'
  return '保存并应用'
})
const saveStatus = computed(() => {
  if (saving.value) return '保存中'
  if (!canSaveSelected.value) return '需购买'
  return hasUnsavedSelection.value ? '未保存' : '已保存'
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [loadedMemorial, loadedExperience, loadedGallery, loadedLetter, loadedCapabilities] = await Promise.all([
      getMemorial(memorialId.value), getMemorialExperience(memorialId.value), listGallery(memorialId.value), getLetter(memorialId.value), getAccountCapabilities(),
    ])
    memorial.value = loadedMemorial
    experience.value = loadedExperience
    gallery.value = loadedGallery
    letter.value = loadedLetter
    capabilities.value = loadedCapabilities
    entryTheme.value = loadedMemorial.theme
    selectedTheme.value = loadedMemorial.theme
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '主题工作室暂时无法打开。'
  } finally {
    loading.value = false
  }
}

function chooseTheme(code: MemorialThemeCode) {
  selectedTheme.value = code
  notice.value = ''
  error.value = ''
}

async function saveTheme() {
  if (!memorial.value || !canSaveSelected.value || saving.value) return
  saving.value = true
  error.value = ''
  notice.value = ''
  try {
    memorial.value = await updateMemorial(memorial.value.id, {
      petName: memorial.value.petName,
      species: memorial.value.species,
      coverMediaId: memorial.value.coverMediaId,
      farewellMessage: memorial.value.farewellMessage,
      aboutTa: memorial.value.aboutTa,
      companionStartedOn: memorial.value.companionStartedOn,
      companionEndedOn: memorial.value.companionEndedOn,
      visibility: memorial.value.visibility,
      theme: selectedTheme.value,
      version: memorial.value.version,
    })
    notice.value = `已应用「${selected.value.name}」。照片、文字、可见范围和导出内容都没有改变。`
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '主题暂时未能保存。'
  } finally {
    saving.value = false
  }
}

async function restoreEntryTheme() {
  if (!memorial.value || saving.value || memorial.value.theme === entryTheme.value) {
    selectedTheme.value = entryTheme.value
    return
  }
  selectedTheme.value = entryTheme.value
  await saveTheme()
}

onMounted(load)
</script>

<template>
  <main class="theme-studio section-narrow" :style="themeStyleVars(selectedTheme)">
    <div v-if="loading" class="account-empty">正在准备 {{ memorial?.petName || 'TA' }} 的主题预览…</div>
    <div v-else-if="!memorial" class="account-empty">
      <h1>主题工作室暂时无法打开。</h1>
      <p>{{ error }}</p>
      <RouterLink class="button" to="/account">回到我的纪念</RouterLink>
    </div>
    <template v-else>
      <header class="studio-hero">
        <div>
          <p class="eyebrow">外观工作室 · 不改动任何原始记忆</p>
          <h1>让 {{ memorial.petName }} 的空间，<br /><em>像它生活过的样子。</em></h1>
          <p>主题会同时影响公开页构图、相片比例、时间线、信件、分享卡与小屋材质。它不移动照片、不改写文字，也不改变谁能看到。</p>
        </div>
        <aside class="studio-assurance" aria-label="主题切换说明">
          <strong>可先看，再决定</strong>
          <span>预览不保存</span><span>已发布内容不丢失</span><span>可还原到进入时主题</span>
        </aside>
      </header>

      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <p v-if="notice" class="form-success" role="status">{{ notice }}</p>

      <section class="theme-workbench" aria-labelledby="theme-workbench-title">
        <div class="theme-sidebar">
          <div class="studio-section-heading"><p class="eyebrow">六份艺术指导</p><h2 id="theme-workbench-title">选择阅读方式</h2></div>
          <div class="theme-list" role="listbox" aria-label="纪念主题">
            <button
              v-for="theme in memorialThemes"
              :key="theme.code"
              type="button"
              role="option"
              :aria-selected="selectedTheme === theme.code"
              :class="['theme-choice', `choice-${theme.layout}`, { selected: selectedTheme === theme.code }]"
              @click="chooseTheme(theme.code)"
            >
              <span :class="['theme-choice-thumb', `thumb-${theme.layout}`]" aria-hidden="true">
                <img v-if="cover" :src="cover" alt="" />
                <b>{{ memorial.petName.slice(0, 1) }}</b><i></i>
              </span>
              <span><strong>{{ theme.name }}</strong><small>{{ theme.description }}</small></span>
              <em v-if="theme.capability !== 'FREE' && !hasThemeAccess(theme, capabilities)">外观包</em>
              <em v-else-if="theme.code === memorial.theme">使用中</em>
            </button>
          </div>
          <RouterLink class="text-link" :to="`/editor/${memorial.id}`">返回中性整理台 →</RouterLink>
        </div>

        <section class="preview-panel" :class="[themeClass(selectedTheme), `device-${previewDevice.toLowerCase()}`]" :style="themeStyleVars(selectedTheme)" aria-label="实时主题预览">
          <div class="preview-toolbar"><div><p class="eyebrow">实时预览</p><strong>{{ selected.name }}</strong></div><div class="device-switch" role="group" aria-label="预览设备"><button type="button" :class="{ active: previewDevice === 'DESKTOP' }" @click="previewDevice = 'DESKTOP'">桌面</button><button type="button" :class="{ active: previewDevice === 'MOBILE' }" @click="previewDevice = 'MOBILE'">手机</button></div></div>
          <div class="preview-canvas" :class="`layout-${selected.layout}`">
            <section class="preview-cover-card"><div class="preview-photo" :class="{ empty: !cover }"><img v-if="cover" :src="cover" :alt="`${memorial.petName} 的封面预览`" /><span v-else aria-hidden="true">{{ memorial.petName.slice(0, 1) }}</span></div><div class="preview-cover-copy"><p>{{ memorial.species }} · 一处生活角落</p><h2>{{ memorial.petName }}</h2><blockquote>“{{ previewQuote }}”</blockquote></div></section>
            <section class="preview-timeline-card"><p>{{ selected.art.timelineStyle }}</p><div><time>{{ firstMoment?.momentTime || '15:20' }}</time><strong>{{ firstMoment?.title || '在熟悉的地方安静待着' }}</strong><span>{{ firstMoment?.placeName || '等待一段真实记忆' }}</span></div></section>
            <section class="preview-letter-card"><p>写给 {{ memorial.petName }} 的信</p><h3>{{ letter?.subject || '有些话，想慢慢告诉你。' }}</h3><span>{{ previewLetter }}</span></section>
            <section class="preview-share-card"><small>数字小窝 · 真实生活痕迹</small><strong>{{ memorial.petName }}</strong><span>{{ lifeLine || '记住 TA 怎样生活过。' }}</span></section>
          </div>
          <div class="theme-contract"><span>照片 {{ selected.art.photoRatio }}</span><span>{{ selected.art.timelineStyle }}</span><span>{{ selected.art.letterStyle }}</span><span>{{ selected.art.roomMaterial }}</span><span>{{ selected.art.motion }}</span></div>
        </section>
      </section>

      <section class="theme-save-bar" aria-label="主题保存操作">
        <div><p class="eyebrow">{{ saveStatus }} · 当前选择</p><h2>{{ selected.name }}</h2><p v-if="!canSaveSelected">完整艺术指导包属于会员外观权益；会员已解锁全部六套主题。基础纪念、导出、删除和亲友查看始终不受影响。</p><p v-else>主题只改变展示方式。公开页、私密房间、分享卡和纪念册会同步采用这套艺术指导；原始照片、文字和可见范围不会改变。</p></div>
        <div class="theme-save-actions"><button class="button button-quiet" type="button" :disabled="saving || memorial.theme === entryTheme" @click="restoreEntryTheme">还原进入时主题</button><button class="button" type="button" :disabled="saving || !canSaveSelected || !hasUnsavedSelection" @click="saveTheme">{{ saving ? '正在保存…' : saveLabel }}</button><RouterLink v-if="!canSaveSelected" class="text-link" to="/pricing">查看外观包边界 →</RouterLink></div>
      </section>
    </template>
  </main>
</template>

<style scoped>
.theme-studio{padding-block:clamp(2rem,5vw,5rem)}.studio-hero{display:grid;grid-template-columns:minmax(0,1fr) 250px;gap:clamp(28px,7vw,90px);align-items:end;margin-bottom:clamp(2.5rem,6vw,5.5rem)}.studio-hero h1{max-width:760px;margin:.45rem 0 1rem;font-size:clamp(36px,4.2vw,58px);letter-spacing:-.035em;text-wrap:balance}.studio-hero h1 em{color:var(--primary);font-style:normal}.studio-hero>div>p:last-child{max-width:650px;color:var(--muted);line-height:1.85}.studio-assurance{display:grid;gap:.55rem;padding:1.35rem;border-left:1px solid var(--border)}.studio-assurance strong{font-family:var(--font-serif);font-size:1.35rem}.studio-assurance span{color:var(--muted);font-size:.9rem}.theme-workbench{display:grid;grid-template-columns:minmax(240px,.34fr) minmax(0,1fr);border:1px solid var(--border);background:var(--surface);box-shadow:var(--shadow)}.theme-sidebar{display:grid;align-content:start;gap:1.25rem;padding:clamp(20px,3vw,34px);border-right:1px solid var(--border)}.studio-section-heading h2{margin:.25rem 0 0;font-size:clamp(26px,3vw,38px)}.theme-list{display:grid;gap:.6rem}.theme-choice{position:relative;display:grid;grid-template-columns:70px minmax(0,1fr) auto;gap:.75rem;align-items:start;width:100%;padding:.9rem;border:1px solid transparent;background:transparent;color:inherit;text-align:left}.theme-choice:hover,.theme-choice:focus-visible,.theme-choice.selected{border-color:var(--primary);background:color-mix(in srgb,var(--primary) 9%,transparent)}.theme-choice-thumb{position:relative;display:block;height:52px;overflow:hidden;border:1px solid color-mix(in srgb,var(--border) 88%,transparent);background:var(--surface-soft)}.theme-choice-thumb img{width:100%;height:100%;object-fit:cover;filter:saturate(.8)}.theme-choice-thumb b{position:absolute;right:5px;bottom:3px;color:#fff;font-family:var(--font-serif);font-size:1rem;text-shadow:0 1px 5px #000}.theme-choice-thumb i{position:absolute;inset:0;display:block;border:4px solid rgb(255 255 255 / 46%);pointer-events:none}.thumb-window{border-radius:16px 2px 2px}.thumb-garden{border-radius:38% 3% 30% 4%}.thumb-meadow{aspect-ratio:16/9;height:auto;align-self:center}.thumb-album{padding:4px;transform:rotate(-2deg);background:#efe2cc}.thumb-album i{border-width:6px;border-color:#fbf8f0}.thumb-home{border-radius:5px 5px 22px 22px;box-shadow:inset 0 0 0 999px rgb(59 40 23 / 17%)}.theme-choice strong,.theme-choice small{display:block}.theme-choice small{margin-top:.25rem;color:var(--muted);font-size:.77rem;line-height:1.45}.theme-choice em{margin-top:.2rem;color:var(--muted);font-size:.68rem;font-style:normal;white-space:nowrap}.preview-panel{min-width:0;padding:clamp(18px,3vw,38px);color:var(--text);background:var(--canvas)}.preview-toolbar{display:flex;justify-content:space-between;gap:1rem;align-items:center;margin-bottom:1.5rem}.preview-toolbar p{margin:0;color:var(--muted);font-size:.72rem}.preview-toolbar strong{font-family:var(--font-serif);font-size:1.3rem}.device-switch{display:inline-flex;padding:3px;border:1px solid var(--border)}.device-switch button{padding:.38rem .72rem;border:0;background:transparent;color:var(--muted)}.device-switch button.active{background:var(--primary);color:var(--button-text)}.preview-canvas{position:relative;display:grid;gap:12px;min-height:560px;padding:clamp(16px,3vw,30px);overflow:hidden;background:var(--surface);border:1px solid var(--border);box-shadow:0 18px 48px rgb(0 0 0 / 12%)}.preview-canvas::before{position:absolute;inset:0;pointer-events:none;content:''}.preview-cover-card,.preview-timeline-card,.preview-letter-card,.preview-share-card{position:relative;min-width:0;overflow:hidden;border:1px solid color-mix(in srgb,var(--border) 80%,transparent);background:color-mix(in srgb,var(--canvas) 20%,var(--surface));box-shadow:0 8px 24px rgb(0 0 0 / 8%)}.preview-photo{overflow:hidden;background:var(--surface-soft)}.preview-photo img{width:100%;height:100%;object-fit:cover}.preview-photo.empty{display:grid;place-items:center;color:var(--primary);font-family:var(--font-serif);font-size:4rem}.preview-cover-copy{padding:clamp(16px,2.6vw,28px)}.preview-cover-copy p,.preview-letter-card>p,.preview-timeline-card>p{margin:0;color:var(--muted);font-size:.72rem}.preview-cover-copy h2{margin:.3rem 0 1rem;font-size:clamp(30px,4vw,48px)}.preview-cover-copy blockquote{margin:0;font-family:var(--font-serif);line-height:1.7}.preview-timeline-card div{display:grid;gap:.25rem;padding:1rem}.preview-timeline-card time{color:var(--primary);font-family:var(--font-serif);font-size:1.4rem}.preview-timeline-card span{color:var(--muted);font-size:.8rem}.preview-letter-card{display:grid;align-content:start;gap:.65rem;padding:clamp(18px,3vw,32px)}.preview-letter-card h3{margin:0;font-size:clamp(21px,3vw,30px)}.preview-letter-card>span{display:-webkit-box;overflow:hidden;line-height:1.75;-webkit-box-orient:vertical;-webkit-line-clamp:4}.preview-share-card{display:grid;align-content:end;gap:.45rem;padding:1.2rem;background:linear-gradient(135deg,color-mix(in srgb,var(--primary) 16%,var(--surface)),var(--canvas))}.preview-share-card small{color:var(--muted)}.preview-share-card strong{font-family:var(--font-serif);font-size:2rem}.preview-share-card span{font-size:.84rem;line-height:1.55}.layout-letter{grid-template-columns:minmax(0,1.15fr) minmax(180px,.85fr);grid-template-rows:1.1fr .9fr}.layout-letter .preview-cover-card{display:grid;grid-template-columns:.92fr 1.08fr;grid-row:1/-1}.layout-letter .preview-photo{height:100%}.layout-letter .preview-timeline-card{align-self:end}.layout-letter .preview-letter-card{background:color-mix(in srgb,var(--primary) 8%,var(--surface))}.layout-window{grid-template-columns:1fr 1fr;grid-template-rows:1.15fr .85fr;background:linear-gradient(115deg,color-mix(in srgb,var(--primary) 10%,var(--surface)),var(--surface))}.layout-window::before{background:linear-gradient(115deg,transparent 0 55%,color-mix(in srgb,#fff 40%,transparent) 55.2% 55.7%,transparent 56%)}.layout-window .preview-cover-card{display:grid;grid-template-columns:1fr;grid-row:1/-1}.layout-window .preview-photo{height:62%}.layout-window .preview-letter-card{margin-top:34px}.layout-garden{grid-template-columns:.75fr 1.25fr;grid-template-rows:1fr 1fr;border-radius:46% 2% 38% 3%}.layout-garden::before{background:radial-gradient(ellipse at 90% 6%,color-mix(in srgb,var(--primary) 22%,transparent),transparent 28%),radial-gradient(ellipse at 10% 95%,color-mix(in srgb,var(--primary) 16%,transparent),transparent 30%)}.layout-garden .preview-cover-card{display:grid;grid-template-columns:.9fr 1.1fr;grid-column:1/-1}.layout-garden .preview-photo{min-height:220px}.layout-garden .preview-letter-card{border-radius:26px 3px 26px 3px}.layout-meadow{grid-template-columns:1.1fr .9fr;grid-template-rows:1fr 1fr;background:linear-gradient(180deg,color-mix(in srgb,#b8dbe1 30%,var(--surface)),var(--surface))}.layout-meadow .preview-cover-card{grid-column:1/-1;display:grid;grid-template-columns:1.4fr .6fr}.layout-meadow .preview-photo{min-height:190px}.layout-album{grid-template-columns:1fr 1fr;grid-template-rows:1fr 1fr;gap:20px;padding:32px;background:repeating-linear-gradient(0deg,color-mix(in srgb,var(--primary) 5%,transparent) 0 1px,transparent 1px 4px),var(--surface)}.layout-album .preview-cover-card{transform:rotate(-1.1deg)}.layout-album .preview-timeline-card{transform:rotate(1.2deg)}.layout-album .preview-letter-card{transform:rotate(.6deg)}.layout-home{grid-template-columns:1fr 1.1fr;grid-template-rows:.95fr 1.05fr;background:radial-gradient(circle at 24% 15%,color-mix(in srgb,var(--primary) 26%,transparent),transparent 17%),var(--surface)}.layout-home .preview-cover-card{display:grid;grid-template-columns:1fr;grid-row:1/-1}.layout-home .preview-photo{height:56%}.layout-home .preview-share-card{border-radius:40px 40px 4px 4px}.theme-contract{display:flex;flex-wrap:wrap;gap:.45rem;margin-top:1rem;color:var(--muted);font-size:.72rem}.theme-contract span{padding:.35rem .55rem;border:1px solid var(--border)}.theme-save-bar{display:flex;justify-content:space-between;gap:2rem;align-items:end;margin-top:1.5rem;padding:clamp(20px,4vw,40px);border:1px solid var(--border);background:var(--surface)}.theme-save-bar h2{margin:.25rem 0 .5rem}.theme-save-bar p:last-child{max-width:680px;margin:0;color:var(--muted);line-height:1.75}.theme-save-actions{display:flex;flex-wrap:wrap;justify-content:flex-end;gap:.75rem;min-width:max-content}@media(max-width:960px){.studio-hero,.theme-workbench{grid-template-columns:1fr}.studio-assurance{grid-template-columns:repeat(3,1fr);border-top:1px solid var(--border);border-left:0}.preview-panel{order:-1}.theme-sidebar{border-right:0;border-bottom:1px solid var(--border)}.theme-list{grid-template-columns:repeat(2,minmax(0,1fr))}.theme-save-bar{align-items:start;flex-direction:column}.theme-save-actions{justify-content:flex-start;min-width:0}}@media(max-width:640px){.theme-studio{padding-inline:0;padding-bottom:7.5rem}.studio-hero{padding-inline:1rem}.studio-hero h1{font-size:clamp(34px,10vw,48px)}.studio-assurance{grid-template-columns:1fr}.theme-sidebar{padding:16px}.theme-list{display:flex;gap:.7rem;overflow-x:auto;scroll-snap-type:x proximity;padding-bottom:.25rem;grid-template-columns:none}.theme-choice{grid-template-columns:1fr;flex:0 0 132px;gap:.42rem;padding:.55rem;scroll-snap-align:start}.theme-choice-thumb{height:78px}.thumb-meadow{height:74px;aspect-ratio:auto}.theme-choice small{display:-webkit-box;overflow:hidden;-webkit-box-orient:vertical;-webkit-line-clamp:2}.theme-choice em{position:absolute;top:.55rem;right:.55rem;padding:.2rem .35rem;background:var(--surface);border:1px solid var(--border)}.preview-panel{padding:16px}.preview-toolbar{align-items:start;flex-direction:column}.preview-canvas{min-height:500px}.device-mobile .preview-canvas{width:min(100%,330px);margin-inline:auto;grid-template-columns:1fr;grid-template-rows:auto;min-height:620px}.device-mobile .preview-cover-card,.device-mobile .preview-timeline-card,.device-mobile .preview-letter-card,.device-mobile .preview-share-card{grid-column:auto;grid-row:auto;transform:none}.device-mobile .preview-cover-card{display:block}.device-mobile .preview-photo{height:220px}.layout-letter,.layout-window,.layout-garden,.layout-meadow,.layout-album,.layout-home{grid-template-columns:1fr}.theme-save-bar{position:sticky;bottom:0;z-index:8;border-right:0;border-bottom:0;border-left:0;box-shadow:0 -12px 30px rgb(0 0 0 / 12%)}.theme-save-bar>div:first-child{display:none}.theme-save-actions{display:grid;grid-template-columns:1fr 1fr;width:100%;min-width:0}.theme-save-actions .button{width:100%}.theme-save-actions .text-link{grid-column:1/-1;text-align:center}}@media(prefers-reduced-motion:reduce){.theme-choice,.preview-cover-card,.preview-timeline-card,.preview-letter-card,.preview-share-card{transition:none!important}}
</style>
