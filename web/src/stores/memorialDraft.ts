import { reactive, ref, watch } from 'vue'

export const memorialDraft = reactive({
  petName: '',
  species: '小狗',
  farewellMessage: '',
  companionStartedOn: '',
  companionEndedOn: '',
  coverFile: null as File | null,
})

const lifetime = 24 * 60 * 60 * 1000
export const draftStorageNotice = ref('正在检查本机草稿…')
let ready = false
let timer: ReturnType<typeof setTimeout> | undefined
let writes = Promise.resolve()
let database: Promise<IDBDatabase> | undefined

function openDatabase() {
  database ??= new Promise<IDBDatabase>((resolve, reject) => {
    if (!globalThis.indexedDB) {
      reject(new Error('IndexedDB unavailable'))
      return
    }
    const request = indexedDB.open('digital-nest-drafts', 1)
    request.onupgradeneeded = () => request.result.createObjectStore('drafts')
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  })
  return database
}

async function persist(remove = false) {
  const db = await openDatabase()
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction('drafts', 'readwrite')
    const store = tx.objectStore('drafts')
    if (remove) store.delete('creation')
    else if (!memorialDraft.petName && !memorialDraft.coverFile && !memorialDraft.farewellMessage)
      store.delete('creation')
    else store.put({ ...memorialDraft, expiresAt: Date.now() + lifetime }, 'creation')
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
    tx.onabort = () => reject(tx.error)
  })
}

export async function flushMemorialDraft() {
  if (!ready) return
  clearTimeout(timer)
  writes = writes
    .then(() => persist())
    .then(() => {
      draftStorageNotice.value = '已暂存到本机，24 小时内可恢复。请勿在共用设备留下私人内容。'
    })
    .catch(() => {
      draftStorageNotice.value = '浏览器不允许暂存；离开或刷新可能丢失未提交内容。'
    })
  return writes
}

export const draftReady = (async () => {
  try {
    const db = await openDatabase()
    const saved = await new Promise<(typeof memorialDraft & { expiresAt: number }) | undefined>(
      (resolve, reject) => {
        const request = db.transaction('drafts').objectStore('drafts').get('creation')
        request.onsuccess = () => resolve(request.result)
        request.onerror = () => reject(request.error)
      },
    )
    if (saved && saved.expiresAt > Date.now()) {
      for (const key of [
        'petName',
        'species',
        'farewellMessage',
        'companionStartedOn',
        'companionEndedOn',
      ] as const) {
        if (typeof saved[key] === 'string') memorialDraft[key] = saved[key]
      }
      if (saved.coverFile instanceof File) memorialDraft.coverFile = saved.coverFile
      draftStorageNotice.value = '已恢复本机暂存的照片与文字，你可以继续整理。'
    } else {
      if (saved) await persist(true)
      draftStorageNotice.value = '照片与文字只在本机暂存 24 小时，登录保存后才会上传。'
    }
  } catch {
    draftStorageNotice.value = '本机暂存不可用；请在关闭页面前登录并保存。'
  } finally {
    ready = true
  }
})()

watch(
  memorialDraft,
  () => {
    if (!ready) return
    clearTimeout(timer)
    timer = setTimeout(() => void flushMemorialDraft(), 400)
  },
  { deep: true, flush: 'sync' },
)

export async function clearMemorialDraft() {
  ready = false
  clearTimeout(timer)
  memorialDraft.petName = ''
  memorialDraft.species = '小狗'
  memorialDraft.farewellMessage = ''
  memorialDraft.companionStartedOn = ''
  memorialDraft.companionEndedOn = ''
  memorialDraft.coverFile = null
  writes = writes
    .then(() => persist(true))
    .catch(() => {
      draftStorageNotice.value = '无法清除本机暂存，请在浏览器设置中清除此站点数据。'
    })
  await writes
  ready = true
}
