<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import {
  addGalleryItem,
  archiveMemorial,
  createTimelineEntry,
  deleteGalleryItem,
  deleteTimelineEntry,
  getAccountLimits,
  getLetter,
  getMemorial,
  listOwnerTributes,
  listGallery,
  listTimeline,
  publishMemorial,
  recordMemorialShare,
  reorderGallery,
  reorderTimeline,
  restoreMemorial,
  saveLetter,
  updateMemorial,
  updateGalleryCaption,
  updateTimelineEntry,
  updateTributeStatus,
  uploadImage,
  uploadVideo,
  type AccountLimits,
  type Memorial,
  type GalleryItem,
  type TimelineEntry,
  type TimelinePayload,
  type Tribute,
} from '@/api/memorials'
import { useAuth } from '@/stores/auth'
import { memorialThemeFor } from '@/config/memorialThemes'

type UploadedMedia = { id: string; url: string; contentType: string }
type GalleryUpload = {
  id: string
  file: File
  progress: number
  status: 'uploading' | 'failed'
  error: string
  media: UploadedMedia | null
}

const route = useRoute()
const router = useRouter()
const auth = useAuth()
const memorial = ref<Memorial | null>(null)
const timeline = ref<TimelineEntry[]>([])
const loading = ref(true)
const saving = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const newCoverFile = ref<File | null>(null)
const coverPreview = ref('')
const editingEntryId = ref<string | null>(null)
const letterSubject = ref('')
const letterBody = ref('')
const savingLetter = ref(false)
const savingTimeline = ref(false)
const captionDrafts = reactive<Record<string, string>>({})
const captionErrors = reactive<Record<string, string>>({})
const captionSaving = reactive<Record<string, boolean>>({})
const ownerTributes = ref<Tribute[]>([])
const moderatingId = ref<string | null>(null)
const gallery = ref<GalleryItem[]>([])
const accountLimits = ref<AccountLimits | null>(null)
const uploadingGallery = ref(false)
const galleryUploads = ref<GalleryUpload[]>([])
const changingPublication = ref(false)
const generatingShareCard = ref(false)
const editorReady = ref(false)
const autoSaveState = ref<'idle' | 'saving' | 'saved' | 'failed'>('idle')
const editorMode = ref<'edit' | 'preview'>('edit')
const isCompactEditor = ref(false)
let autoSaveTimer: number | undefined
let editorViewportQuery: MediaQueryList | undefined
let lastSavedSignature = ''
let lastSavedLetterSignature = ''
let lastTimelineFormSignature = ''

const form = reactive({
  petName: '',
  species: '小狗',
  farewellMessage: '',
  aboutTa: '',
  companionStartedOn: '',
  companionEndedOn: '',
  visibility: 'PRIVATE' as Memorial['visibility'],
  theme: 'NIGHT' as Memorial['theme'],
  accessCode: '',
})

const timelineForm = reactive<TimelinePayload>({
  mediaId: null,
  eventDate: null,
  datePrecision: 'UNKNOWN',
  title: '',
  body: '',
})

const imageUrl = computed(() => coverPreview.value || memorial.value?.coverImageUrl || '')
const canPublish = computed(() => form.visibility !== 'PRIVATE')
const shareUrl = computed(() =>
  memorial.value ? new URL(`/m/${memorial.value.slug}`, window.location.origin).toString() : '',
)
const photoCount = computed(
  () => gallery.value.filter((item) => item.contentType.startsWith('image/')).length,
)
const shortVideoCount = computed(
  () => gallery.value.filter((item) => item.contentType.startsWith('video/')).length,
)
const timelinePhotoChoices = computed(() =>
  gallery.value.filter((item) => item.contentType.startsWith('image/')),
)
const remainingPhotoSlots = computed(() =>
  accountLimits.value ? Math.max(0, accountLimits.value.photoLimit - photoCount.value) : null,
)
const remainingShortVideoSlots = computed(() =>
  accountLimits.value
    ? Math.max(0, accountLimits.value.shortVideoLimit - shortVideoCount.value)
    : null,
)
const publishChecklist = computed(() => {
  const coverReady = Boolean(imageUrl.value)
  const totalPhotoCount = photoCount.value
  return [
    { id: 'cover', label: '已添加封面照片', done: coverReady },
    {
      id: 'photos',
      label: `已准备至少 3 张照片（当前 ${totalPhotoCount} 张）`,
      done: totalPhotoCount >= 3,
    },
    { id: 'visibility', label: '已选择可分享的范围', done: canPublish.value },
  ]
})
const publishReady = computed(() => publishChecklist.value.every((item) => item.done))
const companionDatesLabel = computed(() => {
  if (!form.companionStartedOn && !form.companionEndedOn) return '陪伴的日子，慢慢补充'
  const start = form.companionStartedOn || '相遇以前'
  const end = form.companionEndedOn || '一直想念'
  return `${start} - ${end}`
})
const currentTheme = computed(() => memorialThemeFor(form.theme))
type EditorTask = 'know' | 'life' | 'stories' | 'letter' | 'space' | 'publish'
const activeEditorModule = ref<EditorTask>('know')
const editorModules: ReadonlyArray<{ id: EditorTask; target: string; label: string }> = [
  { id: 'know', target: 'editor-cover', label: '认识 TA' },
  { id: 'life', target: 'editor-timeline', label: '生活里的 TA' },
  { id: 'stories', target: 'editor-gallery', label: '照片与故事' },
  { id: 'letter', target: 'editor-letter', label: '写给 TA' },
  { id: 'space', target: 'editor-style-and-privacy', label: '纪念空间' },
  { id: 'publish', target: 'editor-publish', label: '预览发布' },
]
const moduleCompletion = computed<Record<EditorTask, boolean>>(() => ({
  know: Boolean(form.petName.trim() && imageUrl.value),
  life: Boolean(form.aboutTa.trim() || timeline.value.length),
  stories: Boolean(gallery.value.length),
  letter: Boolean(letterBody.value.trim()),
  space: Boolean(form.theme),
  publish: memorial.value?.status === 'PUBLISHED',
}))
const nextRecommendation = computed(() => {
  if (!imageUrl.value) return { module: 'know' as EditorTask, target: '#editor-cover', label: '先放进一张最像 TA 的照片', note: '一张照片就能让小屋先有一个生活角落。' }
  if (!form.aboutTa.trim()) return { module: 'life' as EditorTask, target: '#editor-about', label: '写下一个只有家人才知道的习惯', note: '不用写完整的一生，只要一个具体动作。' }
  if (gallery.value.length < 3) return { module: 'stories' as EditorTask, target: '#editor-gallery', label: '再收好两张生活照片', note: `现在有 ${gallery.value.length} 张；不用挑“最好看”的。` }
  if (!timeline.value.length) return { module: 'life' as EditorTask, target: '#editor-timeline', label: '把一个平常时刻放进时间线', note: '从“它在门口等我”这样的日常开始。' }
  return { module: 'space' as EditorTask, target: `/editor/${memorial.value?.id}/room`, label: '去小屋回答一个回忆问题', note: '让空间里出现真正属于 TA 的细节。' }
})

function formSignature() {
  return JSON.stringify({
    petName: form.petName.trim(),
    species: form.species.trim(),
    farewellMessage: form.farewellMessage.trim(),
    aboutTa: form.aboutTa.trim(),
    companionStartedOn: form.companionStartedOn,
    companionEndedOn: form.companionEndedOn,
    visibility: form.visibility,
    theme: form.theme,
    accessCode: form.accessCode,
  })
}

function letterSignature() {
  return JSON.stringify({
    subject: letterSubject.value.trim(),
    body: letterBody.value.trim(),
  })
}

function timelineFormSignature() {
  return JSON.stringify({
    mediaId: timelineForm.mediaId || null,
    eventDate: timelineForm.eventDate || null,
    datePrecision: timelineForm.datePrecision,
    title: timelineForm.title.trim(),
    body: timelineForm.body?.trim() || null,
  })
}

lastTimelineFormSignature = timelineFormSignature()

function hasUnsavedChanges() {
  return (
    editorReady.value &&
    (saving.value ||
      savingLetter.value ||
      savingTimeline.value ||
      Object.keys(captionDrafts).length > 0 ||
      uploadingGallery.value ||
      galleryUploads.value.length > 0 ||
      newCoverFile.value !== null ||
      formSignature() !== lastSavedSignature ||
      letterSignature() !== lastSavedLetterSignature ||
      timelineFormSignature() !== lastTimelineFormSignature)
  )
}

function warnBeforeUnload(event: BeforeUnloadEvent) {
  if (!hasUnsavedChanges()) return
  event.preventDefault()
  event.returnValue = true
}

function switchEditorMode(mode: 'edit' | 'preview') {
  editorMode.value = mode
}

async function handleViewTabKeys(event: KeyboardEvent) {
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return
  event.preventDefault()
  editorMode.value =
    event.key === 'Home'
      ? 'edit'
      : event.key === 'End'
        ? 'preview'
        : editorMode.value === 'edit'
          ? 'preview'
          : 'edit'
  await nextTick()
  document.getElementById(`editor-${editorMode.value}-tab`)?.focus()
}

function openEditorModule(module: EditorTask) {
  activeEditorModule.value = module
  window.localStorage.setItem(`digital-nest:editor-task:${String(route.params.id)}`, module)
  editorMode.value = 'edit'
  if (!isCompactEditor.value) {
    const target = editorModules.find((item) => item.id === module)?.target
    void nextTick(() => target && document.getElementById(target)?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
  }
}

function followNextRecommendation() {
  const recommendation = nextRecommendation.value
  if (recommendation.target.startsWith('/')) {
    void router.push(recommendation.target)
    return
  }
  openEditorModule(recommendation.module)
  void nextTick(() => document.querySelector(recommendation.target)?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
}

function syncEditorViewport() {
  isCompactEditor.value = editorViewportQuery?.matches ?? false
}

function resetTimelineForm() {
  editingEntryId.value = null
  timelineForm.mediaId = null
  timelineForm.eventDate = null
  timelineForm.datePrecision = 'UNKNOWN'
  timelineForm.title = ''
  timelineForm.body = ''
  lastTimelineFormSignature = timelineFormSignature()
}

function timelineImageUrl(entry: TimelineEntry) {
  return entry.mediaId ? `/api/v1/media/${entry.mediaId}/content` : ''
}

function chooseCover(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  if (
    !['image/jpeg', 'image/png', 'image/webp'].includes(file.type) ||
    file.size > 5 * 1024 * 1024
  ) {
    errorMessage.value = '请选择不超过 5MB 的 JPG、PNG 或 WebP 图片。'
    return
  }
  if (coverPreview.value) URL.revokeObjectURL(coverPreview.value)
  newCoverFile.value = file
  coverPreview.value = URL.createObjectURL(file)
}

async function chooseGallery(event: Event) {
  if (!memorial.value || uploadingGallery.value) return
  const files = Array.from((event.target as HTMLInputElement).files || [])
  if (!files.length) return
  const selectedPhotoCount = files.filter((file) => file.type.startsWith('image/')).length
  const selectedShortVideoCount = files.filter((file) => file.type.startsWith('video/')).length
  if (remainingPhotoSlots.value !== null && selectedPhotoCount > remainingPhotoSlots.value) {
    errorMessage.value = `当前套餐还可添加 ${remainingPhotoSlots.value} 张照片；请升级套餐或移除已有照片后再试。`
    ;(event.target as HTMLInputElement).value = ''
    return
  }
  if (
    remainingShortVideoSlots.value !== null &&
    selectedShortVideoCount > remainingShortVideoSlots.value
  ) {
    errorMessage.value = `当前套餐还可添加 ${remainingShortVideoSlots.value} 个短视频；请升级套餐或移除已有短视频后再试。`
    ;(event.target as HTMLInputElement).value = ''
    return
  }
  const queuedUploads = files.map((file) =>
    reactive<GalleryUpload>({
      id: crypto.randomUUID(),
      file,
      progress: 0,
      status: 'uploading',
      error: '',
      media: null,
    }),
  )
  galleryUploads.value.push(...queuedUploads)
  uploadingGallery.value = true
  errorMessage.value = ''
  try {
    const results = []
    for (const upload of queuedUploads) results.push(await uploadGalleryFile(upload))
    const completed = results.filter(Boolean).length
    if (completed) successMessage.value = `已收好 ${completed} 份媒体。`
    if (completed < queuedUploads.length) {
      errorMessage.value = '有媒体没有上传成功；已经成功的文件已保留，可只重试失败项。'
    }
  } finally {
    uploadingGallery.value = false
    ;(event.target as HTMLInputElement).value = ''
  }
}

async function uploadGalleryFile(upload: GalleryUpload) {
  if (!memorial.value) return false
  upload.status = 'uploading'
  upload.error = ''
  try {
    const media =
      upload.media ||
      (upload.file.type.startsWith('video/')
        ? await uploadVideo(upload.file, (loaded, total) =>
            updateGalleryUploadProgress(upload, loaded, total),
          )
        : await uploadImage(upload.file, (loaded, total) =>
            updateGalleryUploadProgress(upload, loaded, total),
          ))
    upload.media = media
    upload.progress = 100
    gallery.value.push(await addGalleryItem(memorial.value.id, media.id))
    galleryUploads.value = galleryUploads.value.filter((item) => item.id !== upload.id)
    return true
  } catch (error) {
    upload.status = 'failed'
    upload.error = error instanceof Error ? error.message : '上传没有完成，请稍后重试。'
    return false
  }
}

function updateGalleryUploadProgress(upload: GalleryUpload, loaded: number, total: number) {
  upload.progress = total ? Math.min(100, Math.round((loaded / total) * 100)) : 0
}

async function retryGalleryUpload(upload: GalleryUpload) {
  if (uploadingGallery.value) return
  uploadingGallery.value = true
  errorMessage.value = ''
  const completed = await uploadGalleryFile(upload)
  if (completed) successMessage.value = `已收好 ${upload.file.name}。`
  else errorMessage.value = '这份媒体仍然没有上传成功，请检查文件或网络后再试。'
  uploadingGallery.value = galleryUploads.value.some((item) => item.status === 'uploading')
}

function isVideo(item: GalleryItem) {
  return item.contentType.startsWith('video/')
}

async function removeGallery(item: GalleryItem) {
  if (!memorial.value || !window.confirm('要从相册移除这张照片吗？')) return
  try {
    await deleteGalleryItem(memorial.value.id, item.id)
    gallery.value = gallery.value.filter((galleryItem) => galleryItem.id !== item.id)
    delete captionDrafts[item.id]
    delete captionErrors[item.id]
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '这张照片暂时无法移除。'
  }
}

async function saveGalleryCaption(item: GalleryItem) {
  if (!memorial.value || captionSaving[item.id] || captionDrafts[item.id] === undefined) return
  const submitted = captionDrafts[item.id]!
  captionSaving[item.id] = true
  delete captionErrors[item.id]
  try {
    const updated = await updateGalleryCaption(memorial.value.id, item.id, submitted.trim() || null)
    const index = gallery.value.findIndex((galleryItem) => galleryItem.id === item.id)
    if (index >= 0) gallery.value.splice(index, 1, updated)
    if (captionDrafts[item.id] === submitted) delete captionDrafts[item.id]
  } catch (error) {
    captionErrors[item.id] = error instanceof Error ? error.message : '照片说明暂时没有保存成功。'
  } finally {
    captionSaving[item.id] = false
    if (!captionErrors[item.id] && captionDrafts[item.id] !== undefined)
      void saveGalleryCaption(item)
  }
}

async function moveGallery(index: number, direction: -1 | 1) {
  if (!memorial.value) return
  const destination = index + direction
  if (destination < 0 || destination >= gallery.value.length) return
  const previous = [...gallery.value]
  const next = [...gallery.value]
  const item = next[index]
  if (!item) return
  next.splice(index, 1)
  next.splice(destination, 0, item)
  gallery.value = next
  try {
    gallery.value = await reorderGallery(
      memorial.value.id,
      next.map((galleryItem) => galleryItem.id),
    )
  } catch (error) {
    gallery.value = previous
    errorMessage.value = error instanceof Error ? error.message : '相册排序暂时没有保存成功。'
  }
}

async function moveTimeline(index: number, direction: -1 | 1) {
  if (!memorial.value) return
  const destination = index + direction
  if (destination < 0 || destination >= timeline.value.length) return
  const previous = [...timeline.value]
  const next = [...timeline.value]
  const entry = next[index]
  if (!entry) return
  next.splice(index, 1)
  next.splice(destination, 0, entry)
  timeline.value = next
  try {
    timeline.value = await reorderTimeline(
      memorial.value.id,
      next.map((timelineEntry) => timelineEntry.id),
    )
  } catch (error) {
    timeline.value = previous
    errorMessage.value = error instanceof Error ? error.message : '时间线排序暂时没有保存成功。'
  }
}

async function load() {
  const id = String(route.params.id)
  const [nextMemorial, nextTimeline, nextTributes, nextGallery, letter, limits] = await Promise.all(
    [
      getMemorial(id),
      listTimeline(id),
      listOwnerTributes(id),
      listGallery(id),
      getLetter(id),
      getAccountLimits(),
    ],
  )
  memorial.value = nextMemorial
  timeline.value = nextTimeline
  ownerTributes.value = nextTributes
  gallery.value = nextGallery
  accountLimits.value = limits
  form.petName = memorial.value.petName
  form.species = memorial.value.species
  form.farewellMessage = memorial.value.farewellMessage || ''
  form.aboutTa = memorial.value.aboutTa || ''
  form.companionStartedOn = memorial.value.companionStartedOn || ''
  form.companionEndedOn = memorial.value.companionEndedOn || ''
  form.visibility = memorial.value.visibility
  form.theme = memorial.value.theme
  letterSubject.value = letter?.subject || ''
  letterBody.value = letter?.body || ''
  lastSavedSignature = formSignature()
  lastSavedLetterSignature = letterSignature()
}

async function save(publishAfter = false, quiet = false) {
  if (!memorial.value || saving.value) return
  if (autoSaveTimer) {
    window.clearTimeout(autoSaveTimer)
    autoSaveTimer = undefined
  }
  if (publishAfter && !canPublish.value) {
    errorMessage.value = '请先选择一种可分享的范围，再发布。'
    return
  }
  if (
    form.visibility === 'PASSWORD' &&
    !form.accessCode &&
    memorial.value.visibility !== 'PASSWORD'
  ) {
    errorMessage.value = '口令访问需要设置 6 至 64 位的分享口令。'
    return
  }

  saving.value = true
  const snapshot = { ...form }
  const snapshotSignature = formSignature()
  const coverToSave = newCoverFile.value
  if (quiet) autoSaveState.value = 'saving'
  errorMessage.value = ''
  successMessage.value = ''
  try {
    let coverMediaId = memorial.value.coverMediaId
    if (coverToSave) {
      coverMediaId = (await uploadImage(coverToSave)).id
    }
    const updated = await updateMemorial(memorial.value.id, {
      petName: snapshot.petName.trim(),
      species: snapshot.species.trim(),
      coverMediaId,
      farewellMessage: snapshot.farewellMessage.trim() || null,
      aboutTa: snapshot.aboutTa.trim() || null,
      companionStartedOn: snapshot.companionStartedOn || null,
      companionEndedOn: snapshot.companionEndedOn || null,
      visibility: snapshot.visibility,
      theme: snapshot.theme,
      accessCode: snapshot.accessCode.trim() || null,
      version: memorial.value.version,
    })
    memorial.value = updated
    if (newCoverFile.value === coverToSave) {
      newCoverFile.value = null
      if (coverPreview.value) URL.revokeObjectURL(coverPreview.value)
      coverPreview.value = ''
    }
    if (form.accessCode === snapshot.accessCode) form.accessCode = ''
    // Track exactly the submitted snapshot, not edits made while the request was in flight.
    lastSavedSignature = JSON.stringify({ ...JSON.parse(snapshotSignature), accessCode: '' })
    if (publishAfter) memorial.value = await publishMemorial(updated.id)
    if (coverToSave) gallery.value = await listGallery(updated.id)
    autoSaveState.value = 'saved'
    if (!quiet)
      successMessage.value = publishAfter
        ? '已经发布。现在可以复制链接分享给亲友。'
        : '已保存。你可以继续慢慢整理。'
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '保存没有完成，请稍后再试。'
    autoSaveState.value = 'failed'
  } finally {
    saving.value = false
    if (autoSaveState.value !== 'failed') scheduleAutoSave()
  }
}

async function changePublication() {
  if (!memorial.value) return
  const isPublished = memorial.value.status === 'PUBLISHED'
  const action = isPublished ? '暂时下线' : '恢复分享'
  if (
    !window.confirm(
      `${action}后，亲友将${isPublished ? '暂时无法' : '可以再次'}通过原链接访问这间小窝。是否继续？`,
    )
  )
    return
  changingPublication.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    memorial.value = isPublished
      ? await archiveMemorial(memorial.value.id)
      : await restoreMemorial(memorial.value.id)
    successMessage.value = isPublished
      ? '已暂时下线，原分享链接目前不会打开页面。'
      : '已恢复分享，原链接可以再次访问。'
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '页面状态暂时无法更新。'
  } finally {
    changingPublication.value = false
  }
}

async function copyShareLink() {
  if (!shareUrl.value) return
  errorMessage.value = ''
  successMessage.value = ''
  try {
    if (!navigator.clipboard?.writeText) throw new Error('CLIPBOARD_UNAVAILABLE')
    await navigator.clipboard.writeText(shareUrl.value)
    recordShare('LINK_COPIED')
    successMessage.value = '分享链接已复制，可以发给想念 TA 的亲友。'
  } catch {
    errorMessage.value = `浏览器没有授予复制权限，请手动复制：${shareUrl.value}`
  }
}

async function downloadShareCard() {
  if (!memorial.value) return
  generatingShareCard.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const current = memorial.value
    const theme = memorialThemeFor(current.theme)
    const palette = { background: theme.palette.canvas, accent: theme.palette.accent, text: theme.palette.ink }
    const canvas = document.createElement('canvas')
    canvas.width = 1200
    canvas.height = 630
    const context = canvas.getContext('2d')
    if (!context) throw new Error('CANVAS_UNAVAILABLE')

    context.fillStyle = palette.background
    context.fillRect(0, 0, canvas.width, canvas.height)
    drawShareCardMaterial(context, theme.layout, canvas.width, canvas.height, palette.accent)
    const cover = current.coverImageUrl ? await loadShareImage(current.coverImageUrl) : null
    if (cover) {
      const scale = Math.max(canvas.width / cover.width, canvas.height / cover.height)
      const width = cover.width * scale
      const height = cover.height * scale
      context.globalAlpha = 0.28
      context.drawImage(
        cover,
        (canvas.width - width) / 2,
        (canvas.height - height) / 2,
        width,
        height,
      )
      context.globalAlpha = 1
    }
    const shade = context.createLinearGradient(0, 0, canvas.width, canvas.height)
    shade.addColorStop(0, `${palette.background}EE`)
    shade.addColorStop(1, `${palette.background}A8`)
    context.fillStyle = shade
    context.fillRect(0, 0, canvas.width, canvas.height)
    context.fillStyle = palette.accent
    context.fillRect(72, 72, 8, 486)
    context.fillStyle = palette.accent
    context.font = '600 24px sans-serif'
    context.fillText('数字小窝 · 宠物纪念页', 112, 112)
    context.fillStyle = palette.text
    context.font = `700 82px ${theme.typography.share}`
    context.fillText(current.petName, 112, 230)
    context.font = '400 30px sans-serif'
    context.fillStyle = palette.accent
    context.fillText(`${current.species} 的数字小窝`, 116, 284)
    context.font = `400 34px ${theme.typography.share}`
    context.fillStyle = palette.text
    drawWrappedText(context, current.farewellMessage || '想念一直都在。', 112, 362, 720, 54, 3)
    context.font = '400 22px sans-serif'
    context.fillStyle = palette.text
    context.globalAlpha = 0.72
    context.fillText(shareUrl.value, 112, 538)
    context.globalAlpha = 1
    const blob = await canvasToBlob(canvas)
    const downloadUrl = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = downloadUrl
    link.download = `${current.petName}-数字小窝分享卡片.png`
    link.click()
    window.setTimeout(() => URL.revokeObjectURL(downloadUrl), 1000)
    recordShare('SHARE_CARD_DOWNLOADED')
    successMessage.value = '分享卡片已生成并下载。'
  } catch (error) {
    errorMessage.value =
      error instanceof Error && error.message === 'CANVAS_UNAVAILABLE'
        ? '当前浏览器无法生成分享卡片，请直接复制分享链接。'
        : '分享卡片暂时无法生成，请直接复制分享链接。'
  } finally {
    generatingShareCard.value = false
  }
}

function recordShare(type: 'LINK_COPIED' | 'SHARE_CARD_DOWNLOADED') {
  if (!memorial.value) return
  void recordMemorialShare(memorial.value.id, type).catch(() => undefined)
}

function drawShareCardMaterial(
  context: CanvasRenderingContext2D,
  layout: ReturnType<typeof memorialThemeFor>['layout'],
  width: number,
  height: number,
  accent: string,
) {
  context.save()
  context.strokeStyle = accent
  context.fillStyle = accent
  context.globalAlpha = 0.16
  if (layout === 'album') {
    context.setLineDash([5, 8]); context.strokeRect(38, 38, width - 76, height - 76)
    context.setLineDash([]); context.fillRect(width - 174, 60, 64, 64)
  } else if (layout === 'garden') {
    for (let index = 0; index < 7; index += 1) { context.beginPath(); context.ellipse(width - 85 - index * 34, 85 + index * 28, 42, 14, -0.8, 0, Math.PI * 2); context.fill() }
  } else if (layout === 'meadow') {
    for (let index = 0; index < 24; index += 1) { const x = index * 56; context.beginPath(); context.moveTo(x, height); context.lineTo(x + 13, height - 58 - (index % 4) * 13); context.lineTo(x + 22, height); context.fill() }
  } else if (layout === 'home') {
    context.beginPath(); context.arc(width - 120, 118, 82, 0, Math.PI * 2); context.fill()
  } else if (layout === 'window') {
    context.lineWidth = 10; context.strokeRect(width - 300, 68, 180, 180); context.beginPath(); context.moveTo(width - 210, 68); context.lineTo(width - 210, 248); context.moveTo(width - 300, 158); context.lineTo(width - 120, 158); context.stroke()
  } else {
    context.beginPath(); context.arc(width - 138, 112, 42, 0, Math.PI * 2); context.fill(); context.globalAlpha = 0.08; context.beginPath(); context.arc(width - 138, 112, 92, 0, Math.PI * 2); context.fill()
  }
  context.restore()
}

function loadShareImage(src: string) {
  return new Promise<HTMLImageElement | null>((resolve) => {
    const image = new Image()
    image.onload = () => resolve(image)
    image.onerror = () => resolve(null)
    image.src = src
  })
}

function canvasToBlob(canvas: HTMLCanvasElement) {
  return new Promise<Blob>((resolve, reject) => {
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new Error('CANVAS_EXPORT_FAILED'))),
      'image/png',
    )
  })
}

function drawWrappedText(
  context: CanvasRenderingContext2D,
  value: string,
  x: number,
  y: number,
  maxWidth: number,
  lineHeight: number,
  maxLines: number,
) {
  let line = ''
  let lineIndex = 0
  for (const character of value.trim()) {
    const candidate = line + character
    if (context.measureText(candidate).width > maxWidth && line) {
      context.fillText(
        lineIndex === maxLines - 1 ? `${line.slice(0, -1)}…` : line,
        x,
        y + lineIndex * lineHeight,
      )
      lineIndex += 1
      if (lineIndex === maxLines) return
      line = character
    } else {
      line = candidate
    }
  }
  if (line && lineIndex < maxLines) context.fillText(line, x, y + lineIndex * lineHeight)
}

function scheduleAutoSave() {
  if (
    !editorReady.value ||
    saving.value ||
    newCoverFile.value ||
    formSignature() === lastSavedSignature
  )
    return
  if (autoSaveTimer) window.clearTimeout(autoSaveTimer)
  autoSaveState.value = 'idle'
  autoSaveTimer = window.setTimeout(() => void save(false, true), 2000)
}

function startEdit(entry: TimelineEntry) {
  editingEntryId.value = entry.id
  timelineForm.mediaId = entry.mediaId
  timelineForm.eventDate = entry.eventDate
  timelineForm.datePrecision = entry.datePrecision
  timelineForm.title = entry.title
  timelineForm.body = entry.body || ''
  lastTimelineFormSignature = timelineFormSignature()
}

async function saveTimeline() {
  if (!memorial.value || !timelineForm.title.trim() || savingTimeline.value) return
  savingTimeline.value = true
  const submittedSignature = timelineFormSignature()
  const submittedEntryId = editingEntryId.value
  errorMessage.value = ''
  try {
    const payload: TimelinePayload = {
      mediaId: timelineForm.mediaId || null,
      eventDate: timelineForm.eventDate || null,
      datePrecision: timelineForm.datePrecision,
      title: timelineForm.title.trim(),
      body: timelineForm.body?.trim() || null,
    }
    const entry = editingEntryId.value
      ? await updateTimelineEntry(memorial.value.id, editingEntryId.value, payload)
      : await createTimelineEntry(memorial.value.id, payload)
    const index = timeline.value.findIndex((item) => item.id === entry.id)
    if (index === -1) timeline.value.push(entry)
    else timeline.value.splice(index, 1, entry)
    if (timelineFormSignature() === submittedSignature && editingEntryId.value === submittedEntryId)
      resetTimelineForm()
    else if (editingEntryId.value === submittedEntryId) editingEntryId.value = entry.id
    successMessage.value = '这段记忆已经收好。'
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '时间线没有保存成功。'
  } finally {
    savingTimeline.value = false
  }
}

async function removeTimeline(entry: TimelineEntry) {
  if (!memorial.value || !window.confirm(`要移除“${entry.title}”这段记忆吗？`)) return
  try {
    await deleteTimelineEntry(memorial.value.id, entry.id)
    timeline.value = timeline.value.filter((item) => item.id !== entry.id)
    if (editingEntryId.value === entry.id) resetTimelineForm()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '没有移除成功，请稍后再试。'
  }
}

async function storeLetter() {
  if (savingLetter.value) return
  if (!memorial.value || !letterBody.value.trim()) {
    errorMessage.value = '先写下一句话，才能把这封信收好。'
    return
  }
  savingLetter.value = true
  const savedSignature = letterSignature()
  errorMessage.value = ''
  try {
    await saveLetter(memorial.value.id, letterSubject.value.trim() || null, letterBody.value.trim())
    lastSavedLetterSignature = savedSignature
    successMessage.value = '这封信已经收好。'
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '这封信没有保存成功。'
  } finally {
    savingLetter.value = false
  }
}

async function setTributeStatus(tribute: Tribute, status: 'APPROVED' | 'HIDDEN') {
  if (!memorial.value) return
  moderatingId.value = tribute.id
  errorMessage.value = ''
  try {
    const updated = await updateTributeStatus(memorial.value.id, tribute.id, status)
    const index = ownerTributes.value.findIndex((item) => item.id === updated.id)
    if (index >= 0) ownerTributes.value.splice(index, 1, updated)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '留言状态没有更新成功。'
  } finally {
    moderatingId.value = null
  }
}

function displayDate(entry: TimelineEntry) {
  if (!entry.eventDate || entry.datePrecision === 'UNKNOWN') return '记得那时'
  const [year, month, day] = entry.eventDate.split('-')
  if (entry.datePrecision === 'YEAR') return `${year} 年`
  if (entry.datePrecision === 'MONTH') return `${year} 年 ${Number(month)} 月`
  return `${year} 年 ${Number(month)} 月 ${Number(day)} 日`
}

onMounted(async () => {
  editorViewportQuery = window.matchMedia('(max-width: 820px)')
  syncEditorViewport()
  editorViewportQuery.addEventListener('change', syncEditorViewport)
  window.addEventListener('beforeunload', warnBeforeUnload)
  try {
    await auth.hydrate()
  } catch {
    errorMessage.value = '暂时无法连接服务，请稍后刷新重试。'
    loading.value = false
    return
  }
  if (!auth.state.user) {
    await router.replace({ name: 'login', query: { next: route.fullPath } })
    return
  }
  try {
    await load()
    const savedTask = window.localStorage.getItem(`digital-nest:editor-task:${String(route.params.id)}`) as EditorTask | null
    if (savedTask && editorModules.some((item) => item.id === savedTask)) activeEditorModule.value = savedTask
    editorReady.value = true
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '这间小窝暂时无法打开。'
  } finally {
    loading.value = false
  }
})

watch(form, scheduleAutoSave, { deep: true })

onBeforeRouteLeave(
  () => !hasUnsavedChanges() || window.confirm('还有尚未保存的修改，确定要离开整理台吗？'),
)

onBeforeUnmount(() => {
  editorViewportQuery?.removeEventListener('change', syncEditorViewport)
  window.removeEventListener('beforeunload', warnBeforeUnload)
  if (autoSaveTimer) window.clearTimeout(autoSaveTimer)
  if (coverPreview.value) URL.revokeObjectURL(coverPreview.value)
})
</script>

<template>
  <section class="section editor-page">
    <div v-if="loading" class="account-empty"><p>正在打开整理台…</p></div>
    <div v-else-if="!memorial" class="account-empty">
      <h1>暂时无法打开这间小窝。</h1>
      <p>{{ errorMessage }}</p>
      <RouterLink class="button" to="/account">回到我的小窝</RouterLink>
    </div>
    <template v-else>
      <header class="editor-header">
        <div>
          <p class="eyebrow">小窝整理台</p>
          <h1>慢慢把 {{ memorial.petName }} 的故事放好。</h1>
          <p>
            {{
              memorial.status === 'PUBLISHED'
                ? '这间小窝已发布，保存后会同步更新。想先安静整理，可以暂时下线。'
                : '草稿只有你能看到；整理好之后，再决定何时分享。'
            }}
          </p>
          <small class="autosave-status" :class="autoSaveState">{{
            autoSaveState === 'saving'
              ? '正在自动保存…'
              : autoSaveState === 'saved'
                ? '已自动保存'
                : autoSaveState === 'failed'
                  ? '自动保存失败，请使用“保存草稿”重试'
                  : '修改会自动保存'
          }}</small>
          <button class="editor-next-action" type="button" @click="followNextRecommendation">
            <span>现在最容易完成的一件事</span>
            <strong>{{ nextRecommendation.label }} <i aria-hidden="true">→</i></strong>
            <small>{{ nextRecommendation.note }}</small>
          </button>
        </div>
        <div class="editor-header-actions">
          <RouterLink class="button button-quiet" :to="`/editor/${memorial.id}/themes`">预览外观主题</RouterLink>
          <RouterLink class="button" :to="`/editor/${memorial.id}/room`">布置纪念小屋</RouterLink>
          <RouterLink class="button button-quiet" to="/account">返回我的小窝</RouterLink>
        </div>
      </header>
      <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
      <p v-if="successMessage" class="form-success" role="status">{{ successMessage }}</p>

      <div
        class="editor-view-tabs"
        role="tablist"
        aria-label="整理台视图"
        @keydown="handleViewTabKeys"
      >
        <button
          id="editor-edit-tab"
          type="button"
          role="tab"
          :aria-selected="editorMode === 'edit'"
          aria-controls="editor-edit-panel"
          :tabindex="editorMode === 'edit' ? 0 : -1"
          @click="switchEditorMode('edit')"
        >
          编辑内容
        </button>
        <button
          id="editor-preview-tab"
          type="button"
          role="tab"
          :aria-selected="editorMode === 'preview'"
          aria-controls="editor-preview-panel"
          :tabindex="editorMode === 'preview' ? 0 : -1"
          @click="switchEditorMode('preview')"
        >
          实时预览
        </button>
      </div>

      <nav v-if="isCompactEditor && editorMode === 'edit'" class="editor-mobile-module-nav" aria-label="编辑任务">
        <button v-for="module in editorModules" :key="module.id" type="button" :class="{ active: activeEditorModule === module.id, complete: moduleCompletion[module.id] }" :aria-current="activeEditorModule === module.id ? 'step' : undefined" @click="openEditorModule(module.id)">{{ module.label }}</button>
      </nav>
      <div class="editor-workspace">
        <nav class="editor-module-nav" aria-label="整理模块">
          <p class="eyebrow">整理顺序</p>
          <ol>
            <li v-for="(module, index) in editorModules" :key="module.id">
              <button
                type="button"
                :class="{ active: activeEditorModule === module.id, complete: moduleCompletion[module.id] }"
                :aria-current="activeEditorModule === module.id ? 'step' : undefined"
                @click="openEditorModule(module.id)"
              >
                <span>{{ String(index + 1).padStart(2, '0') }}</span>{{ module.label }}
              </button>
            </li>
          </ol>
          <p class="module-nav-hint">内容会自动保存；发布前请在右侧预览确认。</p>
        </nav>

        <div
          id="editor-edit-panel"
          class="editor-edit-content"
          role="tabpanel"
          aria-labelledby="editor-edit-tab"
          :class="{ 'mobile-view-hidden': isCompactEditor && editorMode !== 'edit' }"
          :aria-hidden="isCompactEditor && editorMode !== 'edit'"
        >
          <div class="editor-grid">
            <form id="editor-cover" class="editor-panel" @submit.prevent="save()">
              <div v-show="!isCompactEditor || activeEditorModule === 'know'">
              <p class="eyebrow">基本信息</p>
              <h2>这间小窝的开场</h2>
              <label
                ><span>TA 的昵称</span><input v-model="form.petName" maxlength="32" required
              /></label>
              <label
                ><span>伙伴类型</span><input v-model="form.species" maxlength="32" required
              /></label>
              <div class="companion-date-fields">
                <label
                  ><span>来到身边的日子</span
                  ><input v-model="form.companionStartedOn" type="date" /></label
                ><label
                  ><span>想念开始的日子</span><input v-model="form.companionEndedOn" type="date"
                /></label>
              </div>
              <label
                ><span>第一张照片</span
                ><input
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  @change="chooseCover"
                /><small class="field-hint">JPG、PNG、WebP，最大 5MB。</small></label
              >
              <div
                v-if="imageUrl"
                class="editor-cover"
                :style="{ backgroundImage: `url(${imageUrl})` }"
              ></div>
              <label
                ><span>想念的话</span
                ><textarea
                  v-model="form.farewellMessage"
                  maxlength="280"
                  rows="5"
                  placeholder="写下最想对 TA 说的一句话。"
                ></textarea>
              </label>
              </div>
              <div v-show="!isCompactEditor || activeEditorModule === 'life'">
              <label id="editor-about"
                ><span>关于 TA</span
                ><textarea
                  v-model="form.aboutTa"
                  aria-label="TA 的小习惯与喜欢的事"
                  maxlength="1500"
                  rows="6"
                  placeholder="TA 有什么小习惯、最喜欢做什么，或有什么昵称由来？这些日常会留在公开纪念页里。"
                ></textarea
                ><small class="field-hint"
                  >把 TA 怎样生活过慢慢写下来；发布前只有你能看到。</small
                ></label
              >
              </div>
              <div v-show="!isCompactEditor || activeEditorModule === 'space'">
              <fieldset class="privacy-field">
                <legend>分享范围</legend>
                <label
                  ><input v-model="form.visibility" type="radio" value="PRIVATE" />
                  仅自己可见</label
                ><label
                  ><input v-model="form.visibility" type="radio" value="LINK" />
                  有链接即可访问</label
                ><label
                  ><input v-model="form.visibility" type="radio" value="PASSWORD" />
                  链接加访问口令</label
                ><label
                  ><input v-model="form.visibility" type="radio" value="PUBLIC" /> 公开纪念页</label
                ><small
                  v-if="memorial.status === 'ARCHIVED' && form.visibility === 'PRIVATE'"
                  class="field-hint"
                  >已切换为仅自己可见并暂时下线。选择一种可分享范围后即可恢复原链接。</small
                >
              </fieldset>
              <section id="editor-style-and-privacy" class="theme-field" aria-labelledby="editor-theme-heading">
                <p class="eyebrow">最终纪念作品</p>
                <h3 id="editor-theme-heading">{{ currentTheme.name }}</h3>
                <p>{{ currentTheme.description }} 主题会一起改变公开页、时间线、信件、分享卡与小屋材质；原始内容不会被改写。</p>
                <RouterLink class="text-link" :to="`/editor/${memorial.id}/themes`">进入外观工作室预览六套艺术指导 →</RouterLink>
                <RouterLink class="text-link" :to="`/editor/${memorial.id}/room#room-interview`">用一个回忆问题补充真实细节 →</RouterLink>
              </section>
              <label v-if="form.visibility === 'PASSWORD'"
                ><span>分享口令</span
                ><input
                  v-model="form.accessCode"
                  type="password"
                  minlength="6"
                  maxlength="64"
                  :required="memorial.visibility !== 'PASSWORD'"
                  placeholder="至少 6 个字符"
                /><small class="field-hint">已经设置过口令时，留空即可保留原口令。</small></label
              >
              </div>
              <section id="editor-publish" v-show="!isCompactEditor || activeEditorModule === 'publish'" class="publish-checklist" aria-label="发布前检查">
                <div>
                  <p class="eyebrow">发布前检查</p>
                  <h3>再确认一次，才和亲友分享。</h3>
                </div>
                <ul>
                  <li
                    v-for="item in publishChecklist"
                    :key="item.id"
                    :class="{ complete: item.done }"
                  >
                    <span aria-hidden="true">{{ item.done ? '✓' : '○' }}</span
                    >{{ item.label }}
                  </li>
                </ul>
              </section>
              <div v-show="!isCompactEditor || activeEditorModule === 'publish'" class="form-actions">
                <button class="button button-quiet" type="submit" :disabled="saving">
                  {{ saving ? '正在保存…' : '保存草稿' }}</button
                ><button
                  class="button"
                  type="button"
                  :disabled="saving || !publishReady"
                  @click="save(true)"
                >
                  {{ memorial.status === 'PUBLISHED' ? '更新已发布页面' : '保存并发布' }}
                </button>
              </div>
              <div
                v-show="!isCompactEditor || activeEditorModule === 'publish'"
                v-if="memorial.status === 'PUBLISHED' || memorial.status === 'ARCHIVED'"
                class="publication-actions"
              >
                <RouterLink
                  v-if="memorial.status === 'PUBLISHED'"
                  class="text-link"
                  :to="`/m/${memorial.slug}`"
                  >查看分享页 →</RouterLink
                ><button
                  v-if="memorial.status === 'PUBLISHED'"
                  class="text-link"
                  type="button"
                  @click="copyShareLink"
                >
                  复制分享链接</button
                ><button
                  v-if="memorial.status === 'PUBLISHED'"
                  class="text-link"
                  type="button"
                  :disabled="generatingShareCard"
                  @click="downloadShareCard"
                >
                  {{ generatingShareCard ? '正在生成卡片…' : '下载分享卡片' }}</button
                ><button
                  class="text-link danger-link"
                  type="button"
                  :disabled="
                    changingPublication ||
                    (memorial.status === 'ARCHIVED' && form.visibility === 'PRIVATE')
                  "
                  @click="changePublication"
                >
                  {{
                    changingPublication
                      ? '正在更新…'
                      : memorial.status === 'PUBLISHED'
                        ? '暂时下线'
                        : form.visibility === 'PRIVATE'
                          ? '选择分享范围后恢复'
                          : '恢复分享'
                  }}
                </button>
              </div>
            </form>

            <section id="editor-timeline" v-show="!isCompactEditor || activeEditorModule === 'life'" class="editor-panel timeline-panel">
              <p class="eyebrow">时间线</p>
              <h2>把那些日子，一段段放回来。</h2>
              <div class="timeline-list">
                <article v-for="(entry, index) in timeline" :key="entry.id" class="timeline-entry">
                  <p>{{ displayDate(entry) }}</p>
                  <h3>{{ entry.title }}</h3>
                  <p v-if="entry.body">{{ entry.body }}</p>
                  <img
                    v-if="entry.mediaId"
                    class="timeline-entry-image"
                    :src="timelineImageUrl(entry)"
                    :alt="`${entry.title} 关联的照片`"
                  />
                  <div>
                    <button
                      class="text-link"
                      type="button"
                      :disabled="index === 0"
                      @click="moveTimeline(index, -1)"
                    >
                      前移</button
                    ><button
                      class="text-link"
                      type="button"
                      :disabled="index === timeline.length - 1"
                      @click="moveTimeline(index, 1)"
                    >
                      后移</button
                    ><button class="text-link" type="button" @click="startEdit(entry)">编辑</button
                    ><button
                      class="text-link danger-link"
                      type="button"
                      @click="removeTimeline(entry)"
                    >
                      移除
                    </button>
                  </div>
                </article>
                <p v-if="!timeline.length" class="empty-tributes">第一段回忆，等你慢慢写下。</p>
              </div>
              <form class="timeline-form" @submit.prevent="saveTimeline">
                <h3>{{ editingEntryId ? '修改这段记忆' : '新增一段记忆' }}</h3>
                <div class="timeline-form-grid">
                  <label
                    ><span>日期（可选）</span
                    ><input v-model="timelineForm.eventDate" type="date" /></label
                  ><label
                    ><span>记得多清楚</span
                    ><select v-model="timelineForm.datePrecision">
                      <option value="UNKNOWN">不标注日期</option>
                      <option value="YEAR">记得年份</option>
                      <option value="MONTH">记得年月</option>
                      <option value="DAY">记得那一天</option>
                    </select></label
                  >
                </div>
                <label
                  ><span>这段记忆的标题</span
                  ><input
                    v-model="timelineForm.title"
                    maxlength="80"
                    required
                    placeholder="例如：第一次在门口等我回家" /></label
                ><label
                  ><span>慢慢写下细节（可选）</span
                  ><textarea
                    v-model="timelineForm.body"
                    maxlength="1000"
                    rows="4"
                    placeholder="那天发生了什么？"
                  ></textarea>
                </label>
                <label
                  ><span>关联相册照片（可选）</span
                  ><select v-model="timelineForm.mediaId">
                    <option :value="null">不关联照片</option>
                    <option
                      v-for="(item, index) in timelinePhotoChoices"
                      :key="item.id"
                      :value="item.mediaId"
                    >
                      {{ item.caption || `相册照片 ${index + 1}` }}
                    </option>
                  </select>
                  <small class="field-hint"
                    >关联后会在公开时间线展示；如需移除这张照片，请先取消这里的关联。</small
                  >
                </label>
                <div class="form-actions">
                  <button
                    v-if="editingEntryId"
                    class="button button-quiet"
                    type="button"
                    @click="resetTimelineForm"
                  >
                    取消编辑</button
                  ><button class="button" type="submit" :disabled="savingTimeline">
                    {{ editingEntryId ? '保存修改' : '收好这段记忆' }}
                  </button>
                </div>
              </form>
            </section>
            <section id="editor-gallery" v-show="!isCompactEditor || activeEditorModule === 'stories'" class="editor-panel letter-panel gallery-panel">
              <p class="eyebrow">照片与短视频</p>
              <h2>把更多瞬间收进来。</h2>
              <p>
                发布前至少准备 3
                张照片；封面会自动算作第一张。短视频不会自动播放，观看时由亲友主动点开。移除后页面会立即隐藏该媒体，文件将在
                24 小时后清理。
              </p>
              <label class="gallery-upload"
                ><span>添加图片或短视频</span
                ><input
                  type="file"
                  multiple
                  accept="image/jpeg,image/png,image/webp,video/mp4,video/webm"
                  :disabled="uploadingGallery"
                  @change="chooseGallery"
                /><small
                  >{{
                    uploadingGallery
                      ? '正在上传…'
                      : `当前 ${photoCount} 张照片、${shortVideoCount} 个短视频${remainingPhotoSlots === null || remainingShortVideoSlots === null ? '' : `；还可添加 ${remainingPhotoSlots} 张照片、${remainingShortVideoSlots} 个短视频`}`
                  }}；图片最大 5MB，短视频限 MP4／WebM、最大 30MB。</small
                ></label
              >
              <div v-if="galleryUploads.length" class="gallery-upload-queue" aria-live="polite">
                <article v-for="upload in galleryUploads" :key="upload.id">
                  <div class="gallery-upload-summary">
                    <strong>{{ upload.file.name }}</strong>
                    <span>{{
                      upload.status === 'uploading' ? `${upload.progress}%` : '上传失败'
                    }}</span>
                  </div>
                  <progress
                    :value="upload.status === 'uploading' ? upload.progress : 0"
                    max="100"
                    :aria-label="`${upload.file.name} 上传进度`"
                  >
                    {{ upload.progress }}%
                  </progress>
                  <p v-if="upload.status === 'failed'" class="form-error">{{ upload.error }}</p>
                  <button
                    v-if="upload.status === 'failed'"
                    class="text-link"
                    type="button"
                    :disabled="uploadingGallery"
                    @click="retryGalleryUpload(upload)"
                  >
                    重试上传
                  </button>
                  <button
                    v-if="upload.status === 'failed'"
                    class="text-link"
                    type="button"
                    :disabled="uploadingGallery"
                    @click="galleryUploads = galleryUploads.filter((item) => item.id !== upload.id)"
                  >
                    移除失败项
                  </button>
                </article>
              </div>
              <RouterLink class="gallery-upgrade-link" to="/pricing"
                >查看套餐容量与升级选项 →</RouterLink
              >
              <div class="gallery-editor-list">
                <figure v-for="(item, index) in gallery" :key="item.id">
                  <video
                    v-if="isVideo(item)"
                    :src="item.mediaUrl"
                    controls
                    playsinline
                    preload="metadata"
                    :aria-label="item.caption || '相册短视频'"
                  ></video
                  ><img v-else :src="item.mediaUrl" :alt="item.caption || '相册照片'" /><label
                    class="gallery-caption"
                    ><input
                      :value="captionDrafts[item.id] ?? item.caption ?? ''"
                      @input="captionDrafts[item.id] = ($event.target as HTMLInputElement).value"
                      aria-label="媒体说明"
                      maxlength="280"
                      :placeholder="isVideo(item) ? '写下这段短视频的故事' : '写下这张照片的故事'"
                      @change="saveGalleryCaption(item)"
                  /></label>
                  <p v-if="captionSaving[item.id]" class="field-hint" role="status">说明保存中…</p>
                  <div v-if="captionErrors[item.id]">
                    <p class="form-error" role="alert">{{ captionErrors[item.id] }}</p>
                    <button class="text-link" type="button" @click="saveGalleryCaption(item)">
                      重试保存说明
                    </button>
                  </div>
                  <div class="gallery-item-actions">
                    <button
                      class="text-link"
                      type="button"
                      :disabled="index === 0"
                      @click="moveGallery(index, -1)"
                    >
                      前移</button
                    ><button
                      class="text-link"
                      type="button"
                      :disabled="index === gallery.length - 1"
                      @click="moveGallery(index, 1)"
                    >
                      后移</button
                    ><button
                      class="text-link danger-link"
                      type="button"
                      @click="removeGallery(item)"
                    >
                      移除
                    </button>
                  </div>
                </figure>
              </div>
            </section>
            <form
              id="editor-letter"
              v-show="!isCompactEditor || activeEditorModule === 'letter'"
              class="editor-panel letter-panel"
              @submit.prevent="storeLetter"
            >
              <p class="eyebrow">写给 TA 的信</p>
              <h2>有些话，慢慢写。</h2>
              <label
                ><span>信的标题（可选）</span
                ><input
                  v-model="letterSubject"
                  maxlength="80"
                  placeholder="例如：今天也很想你" /></label
              ><label
                ><span>这封信</span
                ><textarea
                  v-model="letterBody"
                  maxlength="3000"
                  rows="10"
                  required
                  placeholder="不用写得完整。从最想说的一句话开始。"
                ></textarea>
              </label>
              <div class="form-actions">
                <button class="button" :disabled="savingLetter">
                  {{ savingLetter ? '正在收好…' : '收好这封信' }}
                </button>
              </div>
            </form>
            <section id="editor-tributes" v-show="!isCompactEditor || activeEditorModule === 'publish'" class="editor-panel letter-panel tribute-management">
              <p class="eyebrow">留言管理</p>
              <h2>为亲友的话留一个安心的地方。</h2>
              <div class="manage-tribute-list">
                <article v-for="tribute in ownerTributes" :key="tribute.id">
                  <div>
                    <strong>{{ tribute.authorName }}</strong
                    ><span :class="`tribute-status status-${tribute.status.toLowerCase()}`">{{
                      tribute.status === 'APPROVED'
                        ? '显示中'
                        : tribute.status === 'HIDDEN'
                          ? '已隐藏'
                          : '等待处理'
                    }}</span>
                    <p>{{ tribute.message }}</p>
                  </div>
                  <div class="manage-tribute-actions">
                    <button
                      v-if="tribute.status !== 'APPROVED'"
                      class="text-link"
                      type="button"
                      :disabled="moderatingId === tribute.id"
                      @click="setTributeStatus(tribute, 'APPROVED')"
                    >
                      显示</button
                    ><button
                      v-if="tribute.status !== 'HIDDEN'"
                      class="text-link danger-link"
                      type="button"
                      :disabled="moderatingId === tribute.id"
                      @click="setTributeStatus(tribute, 'HIDDEN')"
                    >
                      隐藏
                    </button>
                  </div>
                </article>
                <p v-if="!ownerTributes.length" class="empty-tributes">还没有亲友留言。</p>
              </div>
            </section>
          </div>
        </div>

        <aside
          id="editor-preview-panel"
          class="editor-live-preview"
          role="tabpanel"
          aria-labelledby="editor-preview-tab"
          :class="{ 'mobile-view-hidden': isCompactEditor && editorMode !== 'preview' }"
          :aria-hidden="isCompactEditor && editorMode !== 'preview'"
        >
          <div class="preview-heading">
            <div>
              <p class="eyebrow">实时预览</p>
              <h2>亲友会看到的样子</h2>
            </div>
            <span class="preview-draft-status">{{
              memorial.status === 'PUBLISHED' ? '编辑中预览' : '草稿预览'
            }}</span>
          </div>
          <article class="editor-preview-card" :class="`preview-theme-${form.theme.toLowerCase()}`">
            <div
              class="editor-preview-cover"
              :style="imageUrl ? { backgroundImage: `url(${imageUrl})` } : undefined"
            >
              <span v-if="!imageUrl" aria-hidden="true">⌁</span>
            </div>
            <div class="editor-preview-copy">
              <p>{{ form.species || 'TA 的伙伴类型' }}</p>
              <h3>{{ form.petName || 'TA 的昵称' }}</h3>
              <small>{{ companionDatesLabel }}</small>
              <span class="preview-rule" aria-hidden="true"></span>
              <blockquote>
                {{
                  form.farewellMessage
                    ? `“${form.farewellMessage}”`
                    : '“这里会放下一句最想对 TA 说的话。”'
                }}
              </blockquote>
            </div>
          </article>
          <section class="preview-content-summary" aria-label="预览内容摘要">
            <p v-if="form.aboutTa"><strong>关于 TA</strong>{{ form.aboutTa }}</p>
            <p v-else class="preview-empty">“关于 TA”的日常会出现在这里。</p>
            <dl>
              <div>
                <dt>时间线</dt>
                <dd>{{ timeline.length }} 段</dd>
              </div>
              <div>
                <dt>相册</dt>
                <dd>{{ photoCount }} 张照片</dd>
              </div>
              <div>
                <dt>短视频</dt>
                <dd>{{ shortVideoCount }} 个</dd>
              </div>
            </dl>
            <div v-if="timeline.length" class="preview-timeline">
              <strong>最近整理的回忆</strong>
              <p>{{ timeline[0]?.title }}</p>
            </div>
            <details class="preview-details">
              <summary>展开内容预览</summary>
              <div class="preview-mini-gallery">
                <figure v-for="item in gallery" :key="item.id">
                  <video
                    v-if="isVideo(item)"
                    :src="item.mediaUrl"
                    controls
                    playsinline
                    preload="metadata"
                    :aria-label="item.caption || '短视频预览'"
                  ></video
                  ><img
                    v-else
                    :src="item.mediaUrl"
                    :alt="item.caption || '相册预览'"
                    loading="lazy"
                  />
                  <figcaption v-if="item.caption">{{ item.caption }}</figcaption>
                </figure>
              </div>
              <div v-for="entry in timeline" :key="entry.id" class="preview-timeline">
                <strong>{{ displayDate(entry) }}</strong>
                <h3>{{ entry.title }}</h3>
                <p>{{ entry.body }}</p>
              </div>
              <article v-if="letterBody">
                <h3>{{ letterSubject || '写给 TA 的信' }}</h3>
                <p class="preview-letter-body">{{ letterBody }}</p>
              </article>
              <p v-if="!gallery.length && !timeline.length && !letterBody">
                添加照片、回忆或信件后，会在这里显示。
              </p>
            </details>
          </section>
          <p class="preview-disclaimer">
            预览会随编辑即时更新。草稿需发布后才可分享；已发布页面会随保存同步更新。
          </p>
        </aside>
      </div>
      <div v-if="isCompactEditor" class="editor-mobile-action-bar" aria-label="编辑器操作">
        <small class="autosave-status" :class="autoSaveState">{{ autoSaveState === 'saving' ? '保存中' : autoSaveState === 'failed' ? '保存失败' : autoSaveState === 'saved' ? '已保存' : '自动保存已开启' }}</small>
        <button class="button button-quiet" type="button" @click="switchEditorMode('preview')">预览</button>
        <button class="button" type="button" :disabled="saving" @click="save()">{{ saving ? '保存中…' : '保存' }}</button>
      </div>
    </template>
  </section>
</template>

<style scoped>
.editor-header-actions{display:flex;gap:.75rem;flex-wrap:wrap;justify-content:flex-end}.editor-mobile-action-bar,.editor-mobile-module-nav{display:none}
@media(max-width:720px){.editor-header-actions{justify-content:flex-start}}
.editor-next-action{display:grid;gap:.18rem;width:min(100%,550px);margin-top:1.2rem;padding:.85rem 0;border:0;border-top:1px solid var(--border);background:transparent;color:inherit;text-align:left}.editor-next-action span,.editor-next-action small{color:var(--muted);font-size:.8rem}.editor-next-action strong{font-family:var(--font-serif);font-size:1.15rem;font-weight:500}.editor-next-action i{color:var(--primary);font-style:normal}.editor-next-action:hover strong,.editor-next-action:focus-visible strong{color:var(--primary)}
.theme-field {
  margin: 25px 0;
  padding: 20px;
  border: 1px solid var(--border);
  border-radius: 15px;
}
.theme-field h3{margin:.3rem 0 .55rem}.theme-field p:not(.eyebrow){max-width:620px;color:var(--muted);line-height:1.7}
.theme-field legend {
  padding: 0;
  font-size: 14px;
  font-weight: 750;
}
.theme-options {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}
.theme-option {
  min-height: 114px;
  padding: 16px;
  border: 1px solid var(--border);
  border-radius: 13px;
  background: var(--surface);
  color: var(--text);
  text-align: left;
  cursor: pointer;
}
.theme-option strong,
.theme-option small {
  display: block;
}
.theme-option small {
  margin-top: 8px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.5;
}
.theme-option.selected {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgb(138 79 61 / 12%);
}
.theme-option-night {
  background: #25314a;
  color: #f7efdf;
  border-color: #51617f;
}
.theme-option-night small {
  color: #ced6e6;
}
.theme-option-garden {
  background: #eef4e8;
  border-color: #c7d9ba;
}
.editor-view-tabs {
  display: none;
}
.editor-workspace {
  display: grid;
  grid-template-columns: minmax(138px, 160px) minmax(0, 1fr) minmax(235px, 280px);
  align-items: start;
  gap: 20px;
}
.editor-module-nav,
.editor-live-preview {
  position: sticky;
  top: 18px;
}
.editor-module-nav {
  padding: 18px 0;
}
.editor-module-nav ol {
  display: grid;
  gap: 4px;
  padding: 0;
  margin: 12px 0 20px;
  list-style: none;
}
.editor-module-nav button {
  display: flex;
  gap: 9px;
  align-items: baseline;
  padding: 9px 4px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.35;
}
.editor-module-nav button {
  width:100%; border:0; background:transparent; color:var(--muted); text-align:left; font:inherit;
}
.editor-module-nav button:hover,
.editor-module-nav button:focus-visible,
.editor-module-nav button.active { color: var(--primary); }
.editor-module-nav button.complete::after { content:'✓'; margin-left:auto; color:var(--sage); font-size:11px; }
.editor-module-nav button span {
  color: var(--primary);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.08em;
}
.module-nav-hint,
.preview-disclaimer {
  color: var(--muted);
  font-size: 12px;
  line-height: 1.65;
}
.editor-edit-content .editor-grid {
  margin-top: 0;
}
.editor-live-preview {
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: 18px;
  background: var(--surface);
  box-shadow: 0 15px 38px rgb(65 47 36 / 8%);
}
.preview-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 18px 14px;
}
.preview-heading .eyebrow {
  margin-bottom: 4px;
}
.preview-heading h2 {
  margin: 0;
  font-size: 18px;
}
.preview-draft-status {
  flex: none;
  padding: 4px 7px;
  border-radius: 999px;
  background: #f3e9de;
  color: var(--primary);
  font-size: 10px;
  font-weight: 750;
}
.editor-preview-card {
  overflow: hidden;
  margin: 0 14px;
  border-radius: 14px;
  background: #f2e4d3;
  text-align: center;
}
.editor-preview-cover {
  display: grid;
  height: 160px;
  place-items: center;
  background: linear-gradient(145deg, #d7b998, #8c5f4b) center / cover no-repeat;
  color: rgb(255 255 255 / 76%);
  font-size: 56px;
}
.editor-preview-copy {
  min-height: 174px;
  padding: 17px 16px 15px;
}
.editor-preview-copy > p {
  margin: 0 0 5px;
  color: var(--primary);
  font-size: 10px;
  letter-spacing: 0.1em;
}
.editor-preview-copy h3 {
  margin: 0;
  font-size: 31px;
}
.editor-preview-copy small {
  display: block;
  margin-top: 6px;
  color: var(--muted);
  font-size: 10px;
}
.editor-preview-copy .preview-rule {
  display: block;
  width: 28px;
  height: 1px;
  margin: 14px auto;
  background: var(--primary);
}
.editor-preview-copy blockquote {
  display: -webkit-box;
  overflow: hidden;
  margin: 0;
  color: var(--muted);
  font-family: var(--font-serif);
  font-size: 12px;
  line-height: 1.7;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}
.preview-theme-night {
  background: #26334c;
  color: #f7efdf;
}
.preview-theme-night .editor-preview-copy > p,
.preview-theme-night .editor-preview-copy small,
.preview-theme-night .editor-preview-copy blockquote {
  color: #dfe6f0;
}
.preview-theme-night .preview-rule {
  background: #e0bf7f;
}
.preview-theme-garden {
  background: #e7f0df;
}
.preview-content-summary {
  display: grid;
  gap: 14px;
  padding: 16px 18px 0;
}
.preview-content-summary > p {
  display: -webkit-box;
  overflow: hidden;
  margin: 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.6;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}
.preview-content-summary > p strong {
  display: block;
  margin-bottom: 4px;
  color: var(--text);
  font-size: 12px;
}
.preview-content-summary .preview-empty {
  color: var(--muted);
}
.preview-content-summary dl {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
  margin: 0;
}
.preview-content-summary dl div {
  padding: 8px 4px;
  border-radius: 9px;
  background: #faf6f0;
  text-align: center;
}
.preview-content-summary dt,
.preview-content-summary dd {
  margin: 0;
}
.preview-content-summary dt {
  color: var(--muted);
  font-size: 10px;
}
.preview-content-summary dd {
  margin-top: 2px;
  font-size: 13px;
  font-weight: 800;
}
.preview-timeline {
  padding: 11px 12px;
  border-left: 2px solid var(--primary);
  background: #fcf7f1;
}
.preview-timeline strong {
  color: var(--primary);
  font-size: 10px;
}
.preview-timeline p {
  margin: 4px 0 0;
  font-size: 12px;
}
.preview-disclaimer {
  margin: 16px 18px 18px;
}
.publish-checklist {
  display: grid;
  gap: 12px;
  margin-top: 28px;
  padding: 17px;
  border: 1px solid var(--border);
  border-radius: 14px;
  background: #fcf7f1;
}
.publish-checklist .eyebrow {
  margin-bottom: 4px;
}
.publish-checklist h3 {
  margin: 0;
  font-size: 17px;
}
.publish-checklist ul {
  display: grid;
  gap: 6px;
  padding: 0;
  margin: 0;
  list-style: none;
}
.publish-checklist li {
  color: var(--muted);
  font-size: 12px;
}
.publish-checklist li span {
  display: inline-block;
  width: 18px;
  color: var(--muted);
  font-weight: 800;
}
.publish-checklist li.complete,
.publish-checklist li.complete span {
  color: var(--sage);
}
.gallery-upgrade-link {
  display: inline-block;
  margin: 0 0 16px;
  color: var(--primary);
  font-size: 13px;
  font-weight: 700;
}
.gallery-upload-queue {
  display: grid;
  gap: 10px;
  margin: 0 0 16px;
}
.gallery-upload-queue article {
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: 12px;
  background: #fcf7f1;
}
.gallery-upload-summary {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  font-size: 12px;
}
.gallery-upload-summary strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.gallery-upload-summary span {
  flex: none;
  color: var(--primary);
  font-size: 11px;
  font-weight: 750;
}
.gallery-upload-queue progress {
  width: 100%;
  height: 7px;
  margin-top: 8px;
  overflow: hidden;
  border: 0;
  border-radius: 999px;
  accent-color: var(--primary);
}
.gallery-upload-queue .form-error {
  margin: 8px 0 0;
}
.gallery-upload-queue .text-link {
  margin-top: 8px;
}
@media (max-width: 1100px) {
  .editor-workspace {
    grid-template-columns: 138px minmax(0, 1fr) 235px;
    gap: 14px;
  }
  .editor-panel {
    padding: 24px;
  }
}
@media (max-width: 960px) {
  .editor-workspace {
    grid-template-columns: minmax(0, 1fr) minmax(235px, 280px);
    gap: 18px;
  }
  .editor-module-nav {
    display: none;
  }
}
@media (max-width: 820px) {
  .editor-view-tabs {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 8px;
    margin: 22px 0 18px;
    padding: 5px;
    border-radius: 13px;
    background: #eee5da;
  }
  .editor-view-tabs button {
    min-height: 44px;
    border: 0;
    border-radius: 9px;
    background: transparent;
    color: var(--muted);
    font: inherit;
    font-size: 14px;
    font-weight: 750;
  }
  .editor-view-tabs button[aria-selected='true'] {
    background: var(--surface);
    color: var(--primary);
    box-shadow: 0 2px 8px rgb(65 47 36 / 9%);
  }
  .editor-mobile-module-nav{display:flex;gap:8px;overflow-x:auto;margin:0 0 14px;padding:0 0 8px;scroll-snap-type:x proximity}.editor-mobile-module-nav button{position:relative;flex:0 0 auto;min-height:38px;padding:.5rem .7rem;border:1px solid var(--border);border-radius:0;background:var(--surface);color:var(--muted);font:inherit;font-size:.82rem;scroll-snap-align:start;white-space:nowrap}.editor-mobile-module-nav button.active{border-color:var(--primary);background:color-mix(in srgb,var(--primary) 12%,var(--surface));color:var(--primary)}.editor-mobile-module-nav button.complete::after{content:'✓';margin-left:.32rem;font-size:.7rem;color:var(--sage)}
  .editor-workspace {
    display: block;
  }
  .editor-live-preview {
    position: static;
  }
  .mobile-view-hidden {
    display: none;
  }
  .editor-live-preview {
    max-width: 520px;
    margin: 0 auto;
  }
  .editor-mobile-action-bar{position:sticky;bottom:0;z-index:20;display:grid;grid-template-columns:1fr auto auto;gap:8px;align-items:center;margin-top:18px;padding:10px 0;background:var(--canvas);border-top:1px solid var(--border)}
  .editor-mobile-action-bar .autosave-status{margin:0;font-size:11px}.editor-mobile-action-bar .button{min-height:42px;padding:.55rem .85rem}
}
@media (max-width: 560px) {
  .theme-options {
    grid-template-columns: 1fr;
  }
  .theme-option {
    min-height: auto;
  }
}
.companion-date-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
@media (max-width: 560px) {
  .companion-date-fields {
    grid-template-columns: 1fr;
  }
}
</style>
