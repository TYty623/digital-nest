<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createMemorial, uploadImage } from '@/api/memorials'
import { useAuth } from '@/stores/auth'
import {
  clearMemorialDraft,
  draftReady,
  draftStorageNotice,
  flushMemorialDraft,
  memorialDraft,
} from '@/stores/memorialDraft'

const router = useRouter()
const route = useRoute()
const auth = useAuth()
const step = ref(1)
const saving = ref(false)
const initializing = ref(true)
const errorMessage = ref('')
const today = new Date().toLocaleDateString('en-CA')
const steps = ['认识 TA', '第一张照片', '一句想念', '看看小窝']
const hasCover = computed(() => memorialDraft.coverFile !== null)
const dateError = computed(() => {
  if (memorialDraft.companionStartedOn && memorialDraft.companionStartedOn > today)
    return '来到身边的日子不能晚于今天。'
  if (memorialDraft.companionEndedOn && memorialDraft.companionEndedOn > today)
    return '想念开始的日子不能晚于今天。'
  if (
    memorialDraft.companionStartedOn && memorialDraft.companionEndedOn &&
    memorialDraft.companionStartedOn > memorialDraft.companionEndedOn
  ) return '想念开始的日子不能早于来到身边的日子。'
  return ''
})
const canContinue = computed(() => {
  if (initializing.value) return false
  if (dateError.value) return false
  if (step.value === 1) return memorialDraft.petName.trim().length > 0
  if (step.value === 2) return hasCover.value
  return true
})
const imagePreview = ref('')
watch(
  () => memorialDraft.coverFile,
  (file) => {
    if (imagePreview.value) URL.revokeObjectURL(imagePreview.value)
    imagePreview.value = file ? URL.createObjectURL(file) : ''
  },
  { immediate: true },
)
onBeforeUnmount(() => {
  if (imagePreview.value) URL.revokeObjectURL(imagePreview.value)
  void flushMemorialDraft()
})

function nextStep() {
  if (canContinue.value && step.value < steps.length) step.value += 1
}

function previousStep() {
  if (step.value > 1) step.value -= 1
}

function chooseCover(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (
    !['image/jpeg', 'image/png', 'image/webp'].includes(file.type) ||
    file.size > 5 * 1024 * 1024
  ) {
    errorMessage.value = '请选择不超过 5MB 的 JPG、PNG 或 WebP 图片。'
    input.value = ''
    return
  }
  errorMessage.value = ''
  memorialDraft.coverFile = file
}

async function saveMemorial() {
  if (saving.value || initializing.value) return
  errorMessage.value = ''
  await flushMemorialDraft()
  if (!auth.state.user) {
    await router.push({ name: 'login', query: { next: '/create?save=1' } })
    return
  }
  if (!memorialDraft.coverFile) {
    step.value = 2
    return
  }

  saving.value = true
  try {
    const image = await uploadImage(memorialDraft.coverFile)
    const created = await createMemorial({
      petName: memorialDraft.petName.trim(),
      species: memorialDraft.species,
      coverMediaId: image.id,
      farewellMessage: memorialDraft.farewellMessage.trim() || null,
      companionStartedOn: memorialDraft.companionStartedOn || null,
      companionEndedOn: memorialDraft.companionEndedOn || null,
      visibility: 'LINK',
      theme: 'NIGHT',
      version: 0,
    })
    await clearMemorialDraft()
    await router.replace(`/editor/${created.id}/room`)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '保存没有完成，请稍后重试。'
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await draftReady
  initializing.value = false
  try {
    await auth.hydrate()
    if (
      route.query.save === '1' &&
      auth.state.user &&
      memorialDraft.petName.trim() &&
      memorialDraft.coverFile
    )
      await saveMemorial()
  } catch {
    errorMessage.value = '暂时无法连接服务。可以先整理本机草稿，服务恢复后再保存。'
  }
})
</script>

<template>
  <section class="create-page section-narrow">
    <header class="create-header">
      <p class="eyebrow">免费创建</p>
      <h1>先为 TA 留下<br />一个小小的开场。</h1>
      <p>不用一次写完。先从昵称和第一张照片开始，其他内容随时可以慢慢补上。</p>
      <small class="field-hint" role="status">{{ draftStorageNotice }}</small>
    </header>
    <ol class="create-steps" aria-label="创建步骤">
      <li
        v-for="(item, index) in steps"
        :key="item"
        :class="{ active: index + 1 === step, complete: index + 1 < step }"
      >
        <span>{{ index + 1 }}</span
        ><small>{{ item }}</small>
      </li>
    </ol>
    <div class="create-layout">
      <form class="create-form" :inert="initializing || saving" @submit.prevent="nextStep">
        <template v-if="step === 1">
          <label
            ><span>TA 的昵称 <em>必填</em></span
            ><input
              v-model="memorialDraft.petName"
              type="text"
              maxlength="32"
              placeholder="例如：豆包、小麦、团团"
              autocomplete="off"
              autofocus
          /></label>
          <fieldset>
            <legend>TA 是一只</legend>
            <div class="choice-row">
              <button
                v-for="option in ['小狗', '小猫', '其他小伙伴']"
                :key="option"
                type="button"
                :class="{ selected: memorialDraft.species === option }"
                @click="memorialDraft.species = option"
              >
                {{ option }}
              </button>
            </div>
          </fieldset>
          <fieldset>
            <legend>陪伴日期（可选）</legend>
            <div class="date-fields">
              <label
                ><span>来到身边的日子</span
                ><input v-model="memorialDraft.companionStartedOn" type="date" :max="today" /></label
              ><label
                ><span>想念开始的日子</span
                ><input v-model="memorialDraft.companionEndedOn" type="date" :max="today"
              /></label>
            </div>
            <small class="field-hint"
              >不知道确切日期也没关系，可以留空；它们不会被强制解释为出生或离世日期。</small
            >
          </fieldset>
        </template>
        <template v-else-if="step === 2">
          <label class="upload-placeholder" :class="{ selected: hasCover }">
            <input type="file" accept="image/jpeg,image/png,image/webp" @change="chooseCover" />
            <span aria-hidden="true">＋</span
            ><strong>{{ hasCover ? '已经选好第一张照片' : '选择一张最想念的照片' }}</strong
            ><small>支持 JPG、PNG、WebP，最大 5MB。</small>
          </label>
        </template>
        <template v-else-if="step === 3">
          <label
            ><span>如果现在能对 TA 说一句话</span
            ><textarea
              v-model="memorialDraft.farewellMessage"
              maxlength="280"
              rows="6"
              placeholder="不用写得完整。比如：谢谢你总是在门口等我回家。"
            ></textarea
            ><small class="field-hint">这段话只有你决定发布后，才会出现在纪念页里。</small></label
          >
        </template>
        <template v-else>
          <div class="ready-copy">
            <span aria-hidden="true">✦</span>
            <p class="eyebrow">尚未保存 · 最后确认</p>
            <h2>属于 {{ memorialDraft.petName }} 的开场已经准备好。</h2>
            <p>保存成功后会直接进入纪念小屋，从“TA 的一天”和“生命指纹”继续记录，而不是停在账户列表。</p>
            <dl class="ready-summary"><div><dt>第一张照片</dt><dd>{{ hasCover ? '已准备' : '待补充' }}</dd></div><div><dt>一句想念</dt><dd>{{ memorialDraft.farewellMessage ? '已写下' : '可以以后再写' }}</dd></div><div><dt>保存状态</dt><dd>现在只是本机预览</dd></div></dl>
          </div>
        </template>
        <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
        <div v-if="dateError" class="form-error date-error" role="alert"><span>{{ dateError }}</span><button v-if="step !== 1" type="button" class="text-button" @click="step = 1">返回修改日期</button></div>
        <div class="form-actions">
          <button v-if="step > 1" class="button button-quiet" type="button" @click="previousStep">
            上一步
          </button>
          <button v-if="step < steps.length" class="button" type="submit" :disabled="!canContinue">
            继续 <span aria-hidden="true">→</span>
          </button>
          <button v-else class="button" type="button" :disabled="saving || !canContinue" @click="saveMemorial">
            {{ saving ? '正在保存…' : auth.state.user ? '保存我的小窝' : '登录并保存小窝' }}
            <span aria-hidden="true">→</span>
          </button>
        </div>
      </form>
      <aside class="live-preview" aria-live="polite">
        <p class="preview-label">实时预览</p>
        <article class="preview-card">
          <div
            class="preview-cover"
            :class="{ filled: hasCover }"
            :style="
              imagePreview
                ? {
                    backgroundImage: `url(${imagePreview})`,
                    backgroundSize: 'cover',
                    backgroundPosition: 'center',
                  }
                : undefined
            "
          >
            <span v-if="!imagePreview" aria-hidden="true">⌁</span>
          </div>
          <p>{{ memorialDraft.species }}</p>
          <h2>{{ memorialDraft.petName || 'TA 的昵称' }}</h2>
          <div class="preview-rule"></div>
          <blockquote>
            {{
              memorialDraft.farewellMessage
                ? `“${memorialDraft.farewellMessage}”`
                : '“这里会放下一句最想对 TA 说的话。”'
            }}
          </blockquote>
          <small v-if="memorialDraft.companionStartedOn || memorialDraft.companionEndedOn"
            >{{ memorialDraft.companionStartedOn || '从相遇开始' }} -
            {{ memorialDraft.companionEndedOn || '一直想念' }}</small
          >
        </article>
      </aside>
    </div>
  </section>
</template>
