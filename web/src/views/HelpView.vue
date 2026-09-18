<script setup lang="ts">
const questions = [
  ['谁可以看到我的小窝？', '默认只有拿到链接的人可以看到。你可以随时改为密码访问、公开或隐藏。'],
  ['我可以修改或删除内容吗？', '可以。页面发布后仍可编辑，用户中心也会提供导出和删除入口。'],
  ['页面会保存多久？', '免费版提供基础保存；付费套餐会明确写明托管期限。我们不会使用无法兑现的“永久保存”承诺。'],
  ['亲友留言需要付费吗？', '不需要。访问、留言与点亮都不收费。'],
]
</script>

<template>
  <section class="section page-intro"><p class="eyebrow">帮助中心</p><h1>慢一点也没关系，<br />我们把常见的问题写在这里。</h1></section>
  <section class="section faq-list"><details v-for="([question, answer]) in questions" :key="question"><summary>{{ question }} <span aria-hidden="true">＋</span></summary><p>{{ answer }}</p></details></section>
</template>

