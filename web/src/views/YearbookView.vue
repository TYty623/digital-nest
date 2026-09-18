<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { getMemorial, getYearbook, type Memorial, type YearbookPreview } from '@/api/memorials'
import { memorialThemeFor, themeClass, themeStyleVars } from '@/config/memorialThemes'

const route = useRoute()
const memorialId = computed(() => String(route.params.id))
const memorial = ref<Memorial | null>(null)
const book = ref<YearbookPreview | null>(null)
const year = ref(new Date().getFullYear())
const loading = ref(true)
const error = ref('')
const years = computed(() => Array.from({ length: 6 }, (_, index) => new Date().getFullYear() - index))
const selectedTheme = computed(() => memorialThemeFor(memorial.value?.theme))

async function load() {
  loading.value = true; error.value = ''
  try { const [current, preview] = await Promise.all([getMemorial(memorialId.value), getYearbook(memorialId.value, year.value)]); memorial.value = current; book.value = preview }
  catch (caught) { error.value = caught instanceof Error ? caught.message : '年度纪念册暂时无法生成。' }
  finally { loading.value = false }
}
function printBook() { window.print() }
onMounted(load)
</script>

<template>
  <main class="yearbook-page section-narrow" :class="themeClass(memorial?.theme)" :style="themeStyleVars(memorial?.theme)">
    <div v-if="loading" class="account-empty">正在排版年度纪念册…</div>
    <template v-else-if="memorial && book">
      <header class="yearbook-toolbar no-print"><div><p class="eyebrow">{{ memorial.petName }} 的年度纪念册</p><p>自动收集这一年确认过的生命档案、时间线和纪念仪式。章节会随真实内容慢慢长出来。</p></div><div><RouterLink class="button button-quiet" :to="`/editor/${memorialId}/ritual`">回到仪式中心</RouterLink><select v-model.number="year" aria-label="选择年度" @change="load"><option v-for="item in years" :key="item" :value="item">{{ item }} 年</option></select></div></header>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <article class="yearbook-sheet" :class="`yearbook-${selectedTheme.layout}`">
        <header class="book-cover"><p>{{ book.year }} · 生命纪念册</p><div class="cover-rule"></div><h1>{{ book.petName }}</h1><blockquote>{{ memorial.farewellMessage ? `“${memorial.farewellMessage}”` : '“被记住的日常，会一直留在这里。”' }}</blockquote><small>由家人亲自保存的生活痕迹</small></header>
        <section class="book-opening"><p class="eyebrow">这一年重新发现的记忆</p><h2>{{ book.chapters.length ? `这一年，关于 ${book.petName} 的 ${book.chapters.length} 段片段。` : `这一年还没有新章节。` }}</h2><p>{{ book.chapters.length ? '它们没有被排序成重要程度，只是按发生与被重新想起的顺序留在这里。' : '下一次完成仪式、补充时间线或生命档案时，这里会慢慢有内容。' }}</p></section>
        <ol v-if="book.chapters.length" class="book-chapters"><li v-for="(chapter, index) in book.chapters" :key="`${chapter.kind}-${index}`"><div class="chapter-number">{{ String(index + 1).padStart(2, '0') }}</div><article><p>{{ chapter.kind === 'RITUAL' ? '纪念仪式' : chapter.kind === 'TIMELINE' ? '时间线' : '生命档案' }}<span>{{ chapter.dateLabel }}</span></p><h3>{{ chapter.title }}</h3><div class="chapter-rule"></div><p class="chapter-body">{{ chapter.body }}</p></article></li></ol>
        <section v-else class="book-empty"><span aria-hidden="true">⌁</span><p>一本纪念册不需要着急写满。</p><RouterLink class="text-link no-print" :to="`/editor/${memorialId}/archive`">去留下第一段生活细节 →</RouterLink></section>
        <footer class="book-footer"><span>数字小窝 · {{ book.year }}</span><span>献给 {{ book.petName }}</span></footer>
      </article>
      <aside class="export-panel no-print"><div><p class="eyebrow">保存与印刷</p><h2>{{ book.exportAvailable ? '把这一年的记忆带走。' : '先预览，等准备好再带走。' }}</h2><p>{{ book.exportNotice }}</p></div><button v-if="book.exportAvailable" class="button" type="button" @click="printBook">打印 / 存为 PDF</button><RouterLink v-else class="button button-quiet" to="/pricing">查看高级会员权益</RouterLink></aside>
    </template>
    <div v-else-if="error" class="account-empty"><h1>暂时无法打开纪念册。</h1><p>{{ error }}</p></div>
  </main>
</template>

<style scoped>
.yearbook-page{padding-block:clamp(2rem,5vw,5rem);font-family:var(--font-body)}.yearbook-toolbar{display:flex;justify-content:space-between;align-items:end;gap:2rem;margin-bottom:2rem}.yearbook-toolbar>div:first-child{max-width:650px}.yearbook-toolbar p:last-child{color:var(--muted);line-height:1.75}.yearbook-toolbar>div:last-child{display:flex;align-items:center;gap:.8rem}.yearbook-toolbar select{padding:.75rem;border:1px solid var(--border);border-radius:8px;background:var(--surface);color:var(--text)}.yearbook-sheet{overflow:hidden;max-width:880px;margin:auto;border:1px solid color-mix(in srgb,var(--primary) 42%,var(--border));background:var(--surface);color:var(--text);box-shadow:0 24px 90px rgb(0 0 0 / 20%)}.book-cover{display:grid;min-height:500px;place-content:center;padding:clamp(36px,8vw,90px);background:radial-gradient(circle at 73% 22%,color-mix(in srgb,var(--primary) 24%,transparent),transparent 28%),linear-gradient(145deg,var(--surface-soft),var(--canvas));color:var(--text);text-align:center}.book-cover>p,.book-cover small{color:var(--primary);letter-spacing:.14em;font-size:.82rem}.cover-rule{width:46px;height:1px;margin:1.5rem auto;background:var(--primary)}.book-cover h1{margin:0;font:clamp(68px,11vw,128px)/1 var(--font-serif)}.book-cover blockquote{max-width:520px;margin:2rem auto;color:var(--muted);font:clamp(18px,2.6vw,27px)/1.75 var(--font-serif)}.book-opening{max-width:680px;padding:clamp(36px,7vw,88px) clamp(26px,9vw,108px) 3rem}.book-opening h2{margin:.5rem 0 1rem;font:clamp(34px,5vw,58px)/1.16 var(--font-serif)}.book-opening p:last-child{color:var(--muted);line-height:1.85}.book-chapters{display:grid;gap:0;margin:0;padding:0;list-style:none}.book-chapters li{display:grid;grid-template-columns:clamp(72px,13vw,150px) 1fr;gap:clamp(20px,4vw,48px);padding:clamp(24px,5vw,58px) clamp(26px,9vw,108px);border-top:1px solid var(--border)}.chapter-number{color:var(--primary);font:clamp(24px,4vw,42px) var(--font-serif)}.book-chapters article>p:first-child{display:flex;justify-content:space-between;gap:1rem;margin:0;color:var(--primary);font-size:.78rem;letter-spacing:.08em}.book-chapters article>p:first-child span{color:var(--muted);letter-spacing:0}.book-chapters h3{margin:.6rem 0;font:clamp(28px,4vw,44px)/1.2 var(--font-serif)}.chapter-rule{width:34px;height:1px;margin:1rem 0;background:var(--primary)}.chapter-body{margin:0;color:var(--muted);white-space:pre-wrap;line-height:1.9}.book-empty{padding:4rem;text-align:center;color:var(--muted)}.book-empty span{display:block;margin-bottom:.75rem;color:var(--primary);font-size:2rem}.book-footer{display:flex;justify-content:space-between;gap:1rem;padding:1.2rem clamp(26px,9vw,108px);border-top:1px solid var(--border);color:var(--muted);font-size:.78rem}.yearbook-window .book-cover{background:linear-gradient(120deg,color-mix(in srgb,var(--primary) 12%,var(--surface)),var(--surface));text-align:left}.yearbook-window .book-cover>*{margin-left:0;margin-right:0}.yearbook-garden .book-cover{border-radius:42% 2% 0 0;background:radial-gradient(ellipse at 10% 95%,color-mix(in srgb,var(--primary) 26%,transparent),transparent 27%),var(--surface-soft)}.yearbook-meadow{max-width:1080px}.yearbook-meadow .book-cover{min-height:410px;place-content:end start;text-align:left;background:linear-gradient(180deg,color-mix(in srgb,var(--primary) 20%,var(--surface)),var(--surface))}.yearbook-meadow .book-cover>*{margin-left:0;margin-right:0}.yearbook-album{padding:22px;background:repeating-linear-gradient(0deg,color-mix(in srgb,var(--primary) 5%,transparent) 0 1px,transparent 1px 4px),var(--surface-soft)}.yearbook-album .book-cover{min-height:430px;transform:rotate(-.7deg);background:var(--surface);box-shadow:0 10px 20px rgb(0 0 0 / 12%)}.yearbook-album .book-chapters li:nth-child(odd) article{transform:rotate(.25deg)}.yearbook-home .book-cover{min-height:520px;background:radial-gradient(circle at 50% 12%,color-mix(in srgb,var(--primary) 30%,transparent),transparent 20%),var(--canvas)}.yearbook-home .book-opening{margin-left:clamp(18px,7vw,88px);border-left:1px solid var(--border)}.export-panel{display:flex;align-items:end;justify-content:space-between;gap:2rem;max-width:880px;margin:2rem auto 0;padding:1.5rem 0}.export-panel h2{margin:.45rem 0;font:clamp(26px,3vw,36px)/1.2 var(--font-serif)}.export-panel p{max-width:520px;color:var(--muted);line-height:1.7}@media(max-width:600px){.yearbook-toolbar,.export-panel{display:grid}.yearbook-toolbar>div:last-child{align-items:stretch;flex-wrap:wrap}.book-chapters li{grid-template-columns:1fr}.book-footer{display:grid}.yearbook-album{padding:10px}.yearbook-home .book-opening{margin-left:0}}@media print{.no-print,.site-header,.site-footer{display:none!important}.yearbook-page{padding:0}.yearbook-sheet{max-width:none;border:0;box-shadow:none}.book-cover{min-height:100vh;break-after:page}.book-chapters li{break-inside:avoid}.book-opening{break-after:avoid}}
</style>
