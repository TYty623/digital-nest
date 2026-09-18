<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { discoverMemorials, lightMemorial, type CommunityMemorial } from '@/api/memorials'

const memorials = ref<CommunityMemorial[]>([])
const loading = ref(true)
const error = ref('')
const filter = ref('全部')
const lightingSlug = ref('')
const litSlugs = ref(new Set<string>())
const unusableCovers = ref(new Set<string>())
const filters = ['全部', '小狗', '小猫', '其他小伙伴']
const topics = [
  { label: '爱晒太阳的 TA 们', words: ['晒太阳', '阳台', '光斑'] },
  { label: '总在门口等候的 TA 们', words: ['门口', '等我', '等主人'] },
  { label: '喜欢钻进被窝的 TA 们', words: ['被窝', '枕头', '床上'] },
  { label: '一听见吃的就跑来的 TA 们', words: ['零食', '罐头', '吃', '包装袋'] },
] as const
const activeTopic = ref('')
const visibleMemorials = computed(() => memorials.value.filter((item) => {
  const speciesMatch = filter.value === '全部' || item.species === filter.value
  const topic = topics.find((candidate) => candidate.label === activeTopic.value)
  const topicMatch = !topic || topic.words.some((word) => item.signature?.includes(word))
  return speciesMatch && topicMatch
}))

function years(item: CommunityMemorial) {
  const start = item.companionStartedOn?.slice(0, 4) || '相遇'
  const end = item.companionEndedOn?.slice(0, 4) || '一直想念'
  return `${start} - ${end}`
}

function chooseFilter(value: string) {
  filter.value = value
  activeTopic.value = ''
}

function chooseTopic(value: string) {
  activeTopic.value = activeTopic.value === value ? '' : value
  filter.value = '全部'
}

function markCoverUnavailable(url: string) {
  unusableCovers.value = new Set(unusableCovers.value).add(url)
}

function validateCover(event: Event, url: string) {
  const image = event.currentTarget as HTMLImageElement
  if (image.naturalWidth < 32 || image.naturalHeight < 32) markCoverUnavailable(url)
}

async function light(item: CommunityMemorial) {
  if (lightingSlug.value || litSlugs.value.has(item.slug)) return
  lightingSlug.value = item.slug
  error.value = ''
  try {
    const result = await lightMemorial(item.slug)
    item.lightCount = result.count
    litSlugs.value = new Set(litSlugs.value).add(item.slug)
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '这盏灯暂时没有点亮。'
  } finally {
    lightingSlug.value = ''
  }
}

onMounted(async () => {
  try {
    memorials.value = await discoverMemorials()
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '纪念星河暂时无法打开。'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="community-page">
    <header class="community-hero section-narrow">
      <p class="eyebrow">纪念星河 · 弱社区</p>
      <h1>今天，也有人<br />在想念 TA。</h1>
      <p>这里只展示主人主动公开的纪念摘要。没有排名、关注和私信，让每一次路过都保持安静。</p>
      <div class="community-stats"><span><strong>{{ memorials.length }}</strong> 个公开纪念空间</span><span>只显示生活细节，不公开主人资料</span></div>
    </header>

    <main class="community-main section-narrow">
      <section class="topic-panel" aria-labelledby="topic-title">
        <div><p class="eyebrow">因为相似的小事相遇</p><h2 id="topic-title">主题纪念集合</h2></div>
        <div class="topic-list">
          <button v-for="topic in topics" :key="topic.label" type="button" :class="{ active: activeTopic === topic.label }" @click="chooseTopic(topic.label)">{{ topic.label }}</button>
        </div>
      </section>

      <div class="community-toolbar" aria-label="按宠物类型筛选">
        <button v-for="item in filters" :key="item" type="button" :class="{ active: filter === item }" @click="chooseFilter(item)">{{ item }}</button>
      </div>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <div v-if="loading" class="community-empty">正在打开这片纪念星河…</div>
      <div v-else-if="visibleMemorials.length" class="community-grid">
        <article v-for="item in visibleMemorials" :key="item.id" class="community-card">
          <RouterLink :to="`/m/${item.slug}`" :aria-label="`查看 ${item.petName} 的纪念空间`">
            <div class="community-cover">
              <img v-if="item.coverImageUrl && !unusableCovers.has(item.coverImageUrl)" :src="item.coverImageUrl" alt="" @load="validateCover($event, item.coverImageUrl)" @error="markCoverUnavailable(item.coverImageUrl)" />
              <span v-else aria-hidden="true">✦</span>
            </div>
            <div class="community-copy"><p>{{ item.species }} · {{ years(item) }}</p><h2>{{ item.petName }}</h2><blockquote>“{{ item.signature || '关于 TA 的故事，正在被家人慢慢写下。' }}”</blockquote></div>
          </RouterLink>
          <div class="community-card-footer"><span>{{ item.lightCount }} 盏安静的灯</span><button type="button" :disabled="lightingSlug === item.slug || litSlugs.has(item.slug)" @click="light(item)">{{ litSlugs.has(item.slug) ? '已经点亮' : lightingSlug === item.slug ? '点亮中…' : '留一盏远方的灯' }}</button></div>
        </article>
      </div>
      <div v-else class="community-empty"><span aria-hidden="true">✦</span><h2>{{ activeTopic ? '这个主题还在等待第一段公开的记忆。' : filter === '全部' ? '这里还在等待第一段公开的记忆。' : `暂时没有公开的${filter}纪念。` }}</h2><p>只有选择“公开展示”并完成发布的纪念空间才会出现；仅链接、口令和私密内容永远不会进入这里。</p><RouterLink class="button" to="/create">为 TA 建一间小窝</RouterLink></div>
    </main>
  </section>
</template>

<style scoped>
.community-page{min-height:70vh;background:radial-gradient(circle at 75% 4%,rgb(216 190 136 / 10%),transparent 28%)}.community-hero{padding-block:clamp(64px,10vw,130px) clamp(38px,6vw,72px)}.community-hero h1{max-width:800px;margin:.65rem 0 1.2rem;font-size:clamp(48px,8vw,96px);line-height:1.06}.community-hero>p:not(.eyebrow){max-width:670px;color:var(--muted);font-size:1.08rem;line-height:1.85}.community-stats{display:flex;gap:2rem;flex-wrap:wrap;margin-top:2rem;color:var(--muted)}.community-stats strong{color:var(--primary);font-family:var(--font-serif);font-size:1.5rem}.community-main{padding-bottom:100px}.topic-panel{display:grid;grid-template-columns:minmax(220px,.62fr) minmax(0,1.38fr);gap:3rem;padding:clamp(24px,4vw,44px);border-top:1px solid var(--border);border-bottom:1px solid var(--border)}.topic-panel h2{margin:.5rem 0;font-size:clamp(28px,4vw,44px)}.topic-list{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:.7rem}.topic-list button,.community-toolbar button{padding:.9rem 1rem;border:1px solid var(--border);border-radius:999px;background:transparent;color:var(--text);text-align:left}.topic-list button.active,.community-toolbar button.active{border-color:var(--primary);background:rgb(216 190 136 / 10%);color:var(--primary)}.community-toolbar{display:flex;gap:.6rem;flex-wrap:wrap;margin:3rem 0 1.5rem}.community-toolbar button{padding:.65rem 1rem;text-align:center}.community-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:1rem}.community-card{overflow:hidden;border:1px solid var(--border);border-radius:20px;background:var(--surface)}.community-card>a{display:block;color:inherit;text-decoration:none}.community-cover{display:grid;place-items:center;aspect-ratio:4/3;background:linear-gradient(145deg,var(--surface-soft),#111a26);background-position:center;background-size:cover}.community-cover span{font-size:2rem;color:var(--primary)}.community-copy{padding:1.3rem}.community-copy>p{margin:0;color:var(--muted);font-size:.83rem}.community-copy h2{margin:.4rem 0 .8rem;font-size:2rem}.community-copy blockquote{min-height:5.2em;margin:0;font-family:var(--font-serif);line-height:1.7}.community-card-footer{display:flex;justify-content:space-between;gap:1rem;align-items:center;padding:1rem 1.3rem;border-top:1px solid var(--border);color:var(--muted);font-size:.82rem}.community-card-footer button{padding:0;border:0;background:transparent;color:var(--primary)}.community-card-footer button:disabled{color:var(--muted)}.community-empty{display:grid;place-items:center;gap:1rem;min-height:320px;padding:3rem;text-align:center;border:1px solid var(--border);border-radius:20px;background:var(--surface)}.community-empty>span{font-size:2rem;color:var(--primary)}.community-empty p{max-width:620px;color:var(--muted);line-height:1.8}
.community-cover{position:relative;background-color:#172535;background-image:radial-gradient(circle at 72% 24%,rgb(216 190 136 / 24%) 0 2px,transparent 3px),radial-gradient(circle at 30% 62%,rgb(216 190 136 / 16%) 0 1px,transparent 2px),linear-gradient(145deg,#223548,#111a26)}.community-cover img{position:absolute;inset:0;width:100%;height:100%;object-fit:cover}
@media(max-width:900px){.community-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}
@media(max-width:640px){.topic-panel,.community-grid{grid-template-columns:1fr}.topic-list{grid-template-columns:1fr}.community-hero h1{font-size:clamp(44px,15vw,64px)}.community-card-footer{align-items:flex-start;flex-direction:column}}
</style>
