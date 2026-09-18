<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { findMemorialExample } from '@/data/memorialExamples'

const route = useRoute()
const example = computed(() => findMemorialExample(String(route.params.slug)))
const lightCount = ref(0)
const lit = ref(false)

watch(example, (value) => {
  lightCount.value = value?.lightCount ?? 0
  lit.value = false
}, { immediate: true })

function light() {
  if (lit.value) return
  lightCount.value += 1
  lit.value = true
}
</script>

<template>
  <section v-if="!example" class="empty-page"><p class="eyebrow">纪念样板</p><h1>没有找到这个演示小窝。</h1><RouterLink class="button" to="/examples">回到纪念样板</RouterLink></section>
  <section v-else class="example-memorial section-narrow" :class="`theme-${example.theme}`">
    <p class="demo-badge">演示内容 · 不代表真实宠物或用户</p>
    <article class="public-card"><div class="public-cover demo-cover"><span aria-hidden="true">{{ example.coverSymbol }}</span></div><div class="public-copy"><p class="eyebrow">{{ example.species }} 的数字小窝</p><h1>{{ example.name }}</h1><p class="demo-years">{{ example.years }}</p><div class="public-rule"></div><blockquote>“{{ example.quote }}”</blockquote><p class="public-date">由爱 TA 的人一起留下</p></div></article>
    <section class="example-gallery"><p class="eyebrow">TA 的相册</p><h2>还想再看看这些瞬间</h2><div><figure v-for="photo in example.gallery" :key="photo.caption"><span aria-hidden="true">{{ photo.symbol }}</span><figcaption>{{ photo.caption }}</figcaption></figure></div></section>
    <section class="public-timeline"><p class="eyebrow">TA 的时间线</p><h2>那些值得记得的日子</h2><ol><li v-for="entry in example.timeline" :key="entry.title"><p>{{ entry.date }}</p><h3>{{ entry.title }}</h3><p>{{ entry.body }}</p></li></ol></section>
    <section class="example-letter"><p class="eyebrow">写给 TA 的信</p><h2>{{ example.letter.subject }}</h2><p>{{ example.letter.body }}</p></section>
    <section class="example-light"><p class="eyebrow">一盏小灯</p><h2>为 {{ example.name }} 留一束温柔的光</h2><p>已有 {{ lightCount }} 盏灯静静亮着。</p><button class="button" type="button" :disabled="lit" @click="light">{{ lit ? '今天已点亮' : '点亮一盏灯' }}</button></section>
    <section class="tribute-section"><div><p class="eyebrow">亲友留言</p><h2>把想说的话，留在这里。</h2></div><div class="tribute-list"><article v-for="tribute in example.tributes" :key="tribute.author" class="tribute-card"><strong>{{ tribute.author }}</strong><p>{{ tribute.message }}</p></article><RouterLink class="text-link" to="/create">也为 TA 建一个小窝 <span aria-hidden="true">→</span></RouterLink></div></section>
  </section>
</template>

<style scoped>
.example-memorial { padding-top: 44px; color: var(--text); }.demo-badge { width: fit-content; margin: 0 auto 18px; padding: 7px 12px; border-radius: 999px; background: rgb(255 253 249 / 72%); color: var(--muted); font-size: 12px; }.demo-cover { display: grid; place-items: center; background: radial-gradient(circle at 44% 31%, #f7e6c9 0 12%, transparent 13%), linear-gradient(145deg, #d8b793, #8e614c); }.demo-cover span { color: rgb(255 255 255 / 78%); font-size: 120px; }.demo-years { margin: 0 0 22px; color: var(--primary); font-size: 14px; }.example-gallery, .example-letter, .example-light { max-width: 900px; margin: 72px auto 0; }.example-gallery h2, .example-letter h2, .example-light h2 { margin: 8px 0 24px; font-size: clamp(32px, 4vw, 45px); }.example-gallery > div { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }.example-gallery figure { min-height: 220px; margin: 0; overflow: hidden; border-radius: 18px; background: var(--surface); }.example-gallery figure span { display: grid; height: 170px; place-items: center; background: linear-gradient(145deg, #e8d4bd, #b88769); color: rgb(255 255 255 / 78%); font-size: 68px; }.example-gallery figcaption { padding: 15px; color: var(--muted); font-size: 13px; line-height: 1.65; }.example-letter { padding: 36px; border: 1px solid var(--border); border-radius: 22px; background: var(--surface); }.example-letter p:not(.eyebrow) { white-space: pre-line; }.example-light { padding: 38px; border-radius: 22px; background: linear-gradient(135deg, #fff8eb, var(--surface)); text-align: center; }.example-light h2 { margin-bottom: 10px; }.example-light p:not(.eyebrow) { margin-bottom: 22px; }.theme-night { --canvas: #151b2b; --surface: #202b42; --text: #f7efdf; --muted: #c4cad8; --primary: #e0bf7f; --border: #3f4d68; }.theme-night .demo-cover, .theme-night .example-gallery figure span { background: radial-gradient(circle at 70% 16%, #fff1b7 0 2%, transparent 3%), linear-gradient(145deg, #485b82, #151b2b); }.theme-night .example-light { background: linear-gradient(135deg, #263451, #1a2337); }.theme-garden { --canvas: #edf2e6; --surface: #fbfdf7; --text: #334333; --muted: #647364; --primary: #627f55; --border: #cbd9c3; }.theme-garden .demo-cover, .theme-garden .example-gallery figure span { background: radial-gradient(ellipse at 20% 86%, #bdcfaa 0 13%, transparent 14%), linear-gradient(145deg, #e3d6b6, #88a275); }.theme-garden .example-light { background: linear-gradient(135deg, #fbfdf7, #e5efdc); }@media (max-width: 720px) { .example-gallery > div { grid-template-columns: 1fr; }.example-gallery figure { min-height: 180px; }.example-light, .example-letter { padding: 28px 22px; } }
</style>
