<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuth } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuth()
const mode = ref<'login' | 'register'>('login')
const email = ref('')
const password = ref('')
const displayName = ref('')
const submitting = ref(false)
const errorMessage = ref('')

async function submit() {
  errorMessage.value = ''
  submitting.value = true
  try {
    if (mode.value === 'login') await auth.signIn(email.value, password.value)
    else await auth.signUp(email.value, password.value, displayName.value)
    const requested = route.query.next
    const next =
      typeof requested === 'string' && /^\/(?![/\\])/.test(requested) ? requested : '/account'
    await router.replace(next)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '暂时无法登录，请稍后再试。'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="auth-page">
    <form class="auth-card" @submit.prevent="submit">
      <p class="eyebrow">{{ mode === 'login' ? '欢迎回来' : '创建账户' }}</p>
      <h1>{{ mode === 'login' ? '登录你的数字小窝' : '给想念留一把钥匙' }}</h1>
      <p>
        {{
          mode === 'login'
            ? '用邮箱登录，继续整理关于 TA 的故事。'
            : '注册后，小窝会安全地保存在你的账户里。'
        }}
      </p>
      <label v-if="mode === 'register'"
        ><span>你的昵称</span
        ><input
          v-model="displayName"
          maxlength="32"
          placeholder="例如：小麦的家人"
          autocomplete="name"
      /></label>
      <label
        ><span>邮箱</span
        ><input
          v-model="email"
          type="email"
          placeholder="name@example.com"
          autocomplete="email"
          required
      /></label>
      <label
        ><span>密码</span
        ><input
          v-model="password"
          type="password"
          minlength="8"
          placeholder="至少 8 个字符"
          :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"
          required
      /></label>
      <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
      <button class="button" type="submit" :disabled="submitting">
        {{ submitting ? '正在处理…' : mode === 'login' ? '登录并继续' : '创建账户并继续' }}
        <span aria-hidden="true">→</span>
      </button>
      <button
        class="auth-switch"
        type="button"
        @click="mode = mode === 'login' ? 'register' : 'login'"
      >
        {{ mode === 'login' ? '还没有账户？创建一个' : '已经有账户？直接登录' }}
      </button>
      <small
        >继续即代表你同意<RouterLink to="/terms">用户协议</RouterLink>和<RouterLink to="/privacy"
          >隐私政策</RouterLink
        >。我们不会公开你的邮箱。</small
      >
    </form>
  </section>
</template>
