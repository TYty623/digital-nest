<script setup lang="ts">
import { memorialExamples } from '@/data/memorialExamples'
</script>

<template>
  <section class="section page-intro"><p class="eyebrow">纪念样板</p><h1>每一段陪伴，<br />都有自己的颜色。</h1><p>以下均为演示内容，用来帮助你找到更接近 TA 的表达方式。</p></section>
  <section class="section example-grid examples-page"><article v-for="example in memorialExamples" :id="example.slug" :key="example.slug" class="example-card large" :class="`theme-${example.theme}`"><div class="example-photo" aria-hidden="true"><span>{{ example.coverSymbol }}</span></div><div class="example-content"><p>{{ example.kind }} · {{ example.years }}</p><h2>{{ example.name }}</h2><blockquote>{{ example.quote }}</blockquote><RouterLink class="text-link" :to="`/examples/${example.slug}`">查看完整演示 <span aria-hidden="true">→</span></RouterLink></div></article></section>
</template>
