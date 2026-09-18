<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { footerNavigation, headerNavigation, siteBrand } from '@/config/site'
import { useAuth } from '@/stores/auth'

const auth = useAuth()
const router = useRouter()
const menuOpen = ref(false)
const sessionError = ref('')
watch(
  () => router.currentRoute.value.fullPath,
  () => {
    menuOpen.value = false
  },
)
const canUseAdmin = computed(() =>
  (auth.state.user?.roles ?? []).some((role) => role === 'ADMIN' || role === 'MODERATOR'),
)

async function signOut() {
  sessionError.value = ''
  try {
    await auth.signOut()
    menuOpen.value = false
    await router.push('/')
  } catch {
    sessionError.value = '退出没有完成，请检查网络后重试。'
  }
}

onMounted(() => auth.hydrate().catch(() => undefined))
</script>

<template>
  <div class="app-shell night-site">
    <div class="site-ambience" aria-hidden="true"><i></i><i></i><i></i></div>
    <a class="skip-link" href="#main-content">跳到主要内容</a>
    <header class="site-header">
      <RouterLink class="brand" to="/" :aria-label="siteBrand.homeAriaLabel"
        ><span class="brand-mark" aria-hidden="true">✧</span
        ><span
          ><strong>{{ siteBrand.name }}</strong
          ><small>{{ siteBrand.descriptor }}</small></span
        ></RouterLink
      >
      <nav class="site-nav" aria-label="主导航">
        <RouterLink v-for="item in headerNavigation" :key="item.to" :to="item.to">{{
          item.label
        }}</RouterLink>
      </nav>
      <div class="header-actions">
        <RouterLink v-if="auth.state.user" class="text-link" to="/account">我的小窝</RouterLink>
        <RouterLink v-if="canUseAdmin" class="text-link" to="/admin">管理台</RouterLink>
        <button v-if="auth.state.user" class="text-link" type="button" @click="signOut">
          退出
        </button>
        <RouterLink v-else class="text-link" to="/login">登录</RouterLink>
        <RouterLink class="button button-small" to="/create">为 TA 建小窝</RouterLink>
        <button
          class="mobile-menu-toggle"
          type="button"
          :aria-expanded="menuOpen"
          aria-controls="mobile-nav"
          @click="menuOpen = !menuOpen"
        >
          {{ menuOpen ? '收起' : '菜单' }}
        </button>
      </div>
    </header>
    <nav
      v-if="menuOpen"
      id="mobile-nav"
      class="mobile-nav"
      aria-label="手机导航"
      @keydown.esc="menuOpen = false"
    >
      <RouterLink v-for="item in headerNavigation" :key="item.to" :to="item.to">{{
        item.label
      }}</RouterLink>
      <RouterLink v-if="auth.state.user" to="/account">我的小窝</RouterLink>
      <RouterLink v-if="canUseAdmin" to="/admin">管理台</RouterLink>
      <button v-if="auth.state.user" type="button" @click="signOut">退出登录</button>
      <RouterLink v-else to="/login">登录 / 注册</RouterLink>
    </nav>
    <p v-if="sessionError" class="form-error session-error" role="alert">{{ sessionError }}</p>
    <main id="main-content" tabindex="-1">
      <RouterView :key="router.currentRoute.value.path" />
    </main>
    <footer class="site-footer">
      <div>
        <strong>{{ siteBrand.name }}</strong>
        <p>{{ siteBrand.footerDescription }}</p>
      </div>
      <div class="footer-links">
        <RouterLink v-for="item in footerNavigation" :key="item.to" :to="item.to">{{
          item.label
        }}</RouterLink>
      </div>
    </footer>
  </div>
</template>
