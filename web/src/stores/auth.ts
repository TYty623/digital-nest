import { reactive } from 'vue'
import { ApiRequestError } from '@/api/client'
import { currentAccount, login, logout, register, type Account } from '@/api/memorials'

const state = reactive<{ user: Account | null; initialized: boolean }>({
  user: null,
  initialized: false,
})
let hydration: Promise<void> | null = null

export function useAuth() {
  async function hydrate() {
    if (state.initialized) return
    if (hydration) return hydration
    hydration = (async () => {
      try {
        state.user = await currentAccount()
      } catch (error) {
        if (!(error instanceof ApiRequestError) || error.status !== 401) throw error
        state.user = null
      }
      state.initialized = true
    })().finally(() => {
      hydration = null
    })
    return hydration
  }

  async function signIn(email: string, password: string) {
    state.user = await login(email, password)
    state.initialized = true
  }

  async function signUp(email: string, password: string, displayName: string) {
    state.user = await register(email, password, displayName)
    state.initialized = true
  }

  async function signOut() {
    await logout()
    state.user = null
  }

  function clear() {
    state.user = null
    state.initialized = true
  }

  return { state, hydrate, signIn, signUp, signOut, clear }
}
