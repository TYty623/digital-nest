<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getServiceHealth, type ServiceHealth } from '@/api/memorials'

const health = ref<ServiceHealth | null>(null)
const checking = ref(true)
const errorMessage = ref('')

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'medium' }).format(new Date(value))
}

async function checkHealth() {
  checking.value = true
  errorMessage.value = ''
  try {
    health.value = await getServiceHealth()
  } catch (error) {
    health.value = null
    errorMessage.value = error instanceof Error ? error.message : '后端服务暂时无法连接。'
  } finally {
    checking.value = false
  }
}

onMounted(checkHealth)
</script>

<template>
  <section class="section-narrow health-page">
    <p class="eyebrow">运行状态</p>
    <h1>先确认每一盏灯，<em>都亮着。</em></h1>
    <p class="intro">这个页面不需要登录，用于确认网页与 Java 服务之间的连接是否正常。</p>

    <div v-if="checking" class="health-card" aria-live="polite">
      <span class="health-dot checking" aria-hidden="true"></span>
      <div><strong>正在检查后端服务…</strong><p>请稍候。</p></div>
    </div>
    <div v-else-if="health" class="health-card healthy" aria-live="polite">
      <span class="health-dot" aria-hidden="true"></span>
      <div><strong>后端服务运行正常</strong><p>{{ health.service }} · 最近检查 {{ formatTime(health.timestamp) }}</p></div>
    </div>
    <div v-else class="health-card unhealthy" role="alert">
      <span class="health-dot" aria-hidden="true"></span>
      <div><strong>暂时无法连接后端服务</strong><p>{{ errorMessage }}</p></div>
    </div>

    <button class="button button-quiet" type="button" :disabled="checking" @click="checkHealth">
      {{ checking ? '正在检查…' : '重新检查' }}
    </button>
  </section>
</template>

<style scoped>
.health-page { padding-top: 64px; }.health-page h1 { max-width: 650px; }.intro { max-width: 600px; margin-bottom: 36px; }.health-card { display: flex; align-items: center; gap: 16px; max-width: 620px; margin-bottom: 20px; padding: 23px; border: 1px solid var(--border); border-radius: 18px; background: var(--surface); }.health-card p { margin: 5px 0 0; color: var(--muted); font-size: 13px; }.health-card.healthy { border-color: #bbd9c5; }.health-card.unhealthy { border-color: #e1bbb5; }.health-dot { width: 12px; height: 12px; flex: 0 0 auto; border-radius: 50%; background: #4a8661; box-shadow: 0 0 0 6px #e3f1e7; }.health-dot.checking { background: #b18342; box-shadow: 0 0 0 6px #f8efde; }.unhealthy .health-dot { background: #a64032; box-shadow: 0 0 0 6px #f6ddd9; }
</style>
