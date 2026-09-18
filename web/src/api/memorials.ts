import { api, uploadWithProgress, type UploadProgressHandler } from './client'

export type Account = {
  id: string
  email: string
  displayName: string
  roles: string[]
}

export type ServiceHealth = {
  status: 'UP'
  service: string
  timestamp: string
}

export type AccountDeletionStatus = {
  pending: boolean
  scheduledFor: string | null
}

export type AccountCapabilities = {
  planCode: string
  enabled: string[]
  /** Theme ownership is supplied by the server; source content stays independent of it. */
  themeCodes?: Memorial['theme'][]
  allThemeCollectionUnlocked?: boolean
}

export type ArchiveEntry = {
  id: string
  memorialId: string
  entryType: 'PLACE' | 'RELATION' | 'OBJECT' | 'MOMENT' | 'MEDIA'
  title: string
  body: string | null
  eventDate: string | null
  placeLabel: string | null
  sourceLabel: string | null
  verificationStatus: 'CONFIRMED' | 'PENDING'
  visibility: 'PRIVATE' | 'FAMILY'
  mediaId: string | null
  mediaUrl: string | null
  createdAt: string
  updatedAt: string
}

export type Anniversary = {
  id: string
  memorialId: string
  anniversaryType: 'BIRTHDAY' | 'MEETING' | 'ADOPTION' | 'FAREWELL' | 'SEASON' | 'CUSTOM'
  title: string
  eventDate: string
  repeatRule: 'ANNUAL' | 'ONCE' | 'OFF'
  reminderEnabled: boolean
  createdAt: string
  updatedAt: string
}

export type TimeCapsule = {
  id: string
  memorialId: string
  title: string
  body: string
  mediaId: string | null
  mediaUrl: string | null
  unlockOn: string
  visibility: 'PRIVATE' | 'FAMILY'
  unlocked: boolean
  openedAt: string | null
  createdAt: string
}

export type RitualRecord = {
  id: string
  memorialId: string
  ritualType: 'FIRST_HOME' | 'BIRTHDAY' | 'ADOPTION' | 'FAREWELL' | 'SEASON' | 'CUSTOM' | 'FAMILY'
  ritualAction: 'LIGHT' | 'FLOWER' | 'LETTER' | 'SOUND' | 'CAPSULE'
  note: string | null
  ambientEnabled: boolean
  completedAt: string
}

export type RitualSpace = {
  archiveEntries: ArchiveEntry[]
  anniversaries: Anniversary[]
  capsules: TimeCapsule[]
  rituals: RitualRecord[]
}

export type YearbookChapter = { kind: 'MEMORY' | 'RITUAL' | 'TIMELINE'; title: string; body: string; dateLabel: string }
export type YearbookPreview = { petName: string; year: number; chapters: YearbookChapter[]; exportAvailable: boolean; exportNotice: string }

export type DigitalLifeFact = {
  id: string
  profileId: string
  factType: 'TRAIT' | 'HABIT' | 'RELATION' | 'EVENT' | 'PREFERENCE'
  statement: string
  sourceType: 'LIFE_DETAIL' | 'ARCHIVE_ENTRY' | 'TIMELINE' | 'USER_NOTE'
  sourceLabel: string
  verificationStatus: 'PENDING' | 'CONFIRMED' | 'REJECTED'
  createdAt: string
  updatedAt: string
}

export type DigitalLifeWorkspace = {
  profile: { id: string; status: 'ACTIVE' | 'PAUSED' | 'ARCHIVED'; consentVersion: string; updatedAt: string } | null
  facts: DigitalLifeFact[]
  profileAvailable: boolean
  qaAvailable: boolean
  providerStatuses: { capability: string; status: string; notice: string }[]
  ethicsNotice: string
}

export type DigitalLifeAnswer = {
  answer: string
  generationMode: string
  citations: { sourceType: string; sourceLabel: string; statement: string }[]
  safetyNotice: string
}

export type Memorial = {
  id: string
  slug: string
  petName: string
  species: string
  coverMediaId: string | null
  coverImageUrl: string | null
  farewellMessage: string | null
  aboutTa: string | null
  companionStartedOn: string | null
  companionEndedOn: string | null
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
  visibility: 'PUBLIC' | 'LINK' | 'PASSWORD' | 'PRIVATE'
  theme: 'SUNNY' | 'NIGHT' | 'GARDEN' | 'MEADOW' | 'ALBUM' | 'HOME'
  publishedAt: string | null
  createdAt: string
  updatedAt: string
  version: number
}

export type MemorialPayload = {
  petName: string
  species: string
  coverMediaId?: string | null
  farewellMessage?: string | null
  aboutTa?: string | null
  companionStartedOn?: string | null
  companionEndedOn?: string | null
  visibility: 'PUBLIC' | 'LINK' | 'PASSWORD' | 'PRIVATE'
  theme?: Memorial['theme']
  accessCode?: string | null
  version: number
}

export type Tribute = {
  id: string
  authorName: string
  message: string
  status: 'PENDING' | 'APPROVED' | 'HIDDEN' | 'REPORTED'
  reportCount: number
  createdAt: string
  updatedAt: string
}

export type TimelineEntry = {
  id: string
  memorialId: string
  mediaId: string | null
  mediaContentType: 'image/jpeg' | 'image/png' | 'image/webp' | null
  eventDate: string | null
  datePrecision: 'DAY' | 'MONTH' | 'YEAR' | 'UNKNOWN'
  title: string
  body: string | null
  position: number
  createdAt: string
  updatedAt: string
}

export type TimelinePayload = {
  mediaId?: string | null
  eventDate?: string | null
  datePrecision: TimelineEntry['datePrecision']
  title: string
  body?: string | null
}

export type MemorialLetter = {
  id: string
  memorialId: string
  subject: string | null
  body: string
  createdAt: string
  updatedAt: string
}

export type GalleryItem = {
  id: string
  mediaId: string
  mediaUrl: string
  contentType: 'image/jpeg' | 'image/png' | 'image/webp' | 'video/mp4' | 'video/webm'
  caption: string | null
  position: number
  createdAt: string
}

export type SoundMemory = { id: string; mediaId: string; mediaUrl: string; contentType: string; title: string; story: string | null; position: number; createdAt: string }
export type InterviewAnswer = { id: string; promptKey: string; answer: string; updatedAt: string }
export type Keepsake = { id: string; title: string; story: string; createdAt: string }
export type DayMoment = {
  id: string
  momentTime: string
  title: string
  placeName: string | null
  story: string | null
  mediaId: string | null
  mediaUrl: string | null
  createdAt: string
  updatedAt: string
}
export type DayMomentPayload = Pick<DayMoment, 'momentTime' | 'title' | 'placeName' | 'story' | 'mediaId'>
export type LifeDetail = { id: string; detailKey: string; answer: string; updatedAt: string }
export type BurialRecord = {
  id: string; memorialId: string; petName: string; ownerDisplayName: string
  dispositionType: 'PROFESSIONAL_HARMLESS' | 'CREMATION' | 'ASHES_KEPT' | 'ASHES_PLACED' | 'OTHER_LAWFUL'
  occurredOn: string | null; region: string | null; placeName: string | null; remembranceText: string | null
  reviewStatus: 'PRIVATE' | 'PENDING' | 'APPROVED' | 'REJECTED'; complianceAttested: boolean
  reviewNote: string | null; reviewedAt: string | null; updatedAt: string
}
export type MemorialExperience = {
  dayMoments: DayMoment[]
  lifeDetails: LifeDetail[]
  sounds: SoundMemory[]
  interviewAnswers: InterviewAnswer[]
  keepsakes: Keepsake[]
  burial: BurialRecord | null
}

export type HabitatItem = {
  id: string
  kind: 'LIGHT' | 'FLOWER' | 'STONE'
  xPercent: number
  yPercent: number
  createdAt: string
}

export type HabitatNote = {
  id: string
  memoryDate: string
  text: string
  sourceType: 'MANUAL' | 'DAY_MOMENT' | 'LIFE_DETAIL' | 'KEEPSAKE' | 'ARCHIVE_ENTRY'
  sourceId: string | null
  sourceLabel: string | null
  createdAt: string
}

export type MemorialHabitatSpace = {
  memorialId: string
  scene: 'FOREST' | 'COMPANION'
  title: string
  light: number
  updatedAt: string | null
  items: HabitatItem[]
  notes: HabitatNote[]
}

export type CommunityMemorial = {
  id: string
  slug: string
  petName: string
  species: string
  coverImageUrl: string | null
  companionStartedOn: string | null
  companionEndedOn: string | null
  signature: string | null
  lightCount: number
  publishedAt: string
}

export type BillingPlan = {
  code: 'FREE' | 'GUARDIAN' | 'TREASURE' | 'CUSTOM'
  name: string
  amountCents: number
  currency: string
  photoLimit: number
  shortVideoLimit: number
  timelineLimit: number
  themeLimit: number
  hostedYears: number
  checkoutAvailable: boolean
}

export type BillingOrder = {
  id: string
  planCode: BillingPlan['code']
  planName: string
  amountCents: number
  currency: string
  photoLimit: number
  shortVideoLimit: number
  timelineLimit: number
  themeLimit: number
  hostedYears: number
  status: 'PENDING' | 'PAID' | 'CANCELED' | 'REFUNDED'
  paymentReference: string | null
  paidAt: string | null
  refundedAt: string | null
  createdAt: string
}

export type AccountEntitlement = {
  id: string
  orderId: string
  planCode: BillingPlan['code']
  planName: string
  photoLimit: number
  shortVideoLimit: number
  timelineLimit: number
  themeLimit: number
  hostedUntil: string
  status: 'ACTIVE' | 'REVOKED'
  revokedAt: string | null
  createdAt: string
}

export type AccountLimits = {
  photoLimit: number
  shortVideoLimit: number
  timelineLimit: number
}

export type AdminOverview = {
  pendingTributes: number
  reportedTributes: number
  publishedMemorials: number
  pendingOrders: number
  pendingDeletionRequests: number
  openCustomServiceRequests: number
  uniqueVisitorsToday: number
  uniqueVisitorsLast7Days: number
  newMemorialsLast7Days: number
  publishedMemorialsLast7Days: number
  shareEventsLast7Days: number
  paidOrdersLast30Days: number
  managedMediaBytes: number
}

export type AdminTribute = {
  id: string
  memorialId: string
  memorialSlug: string
  petName: string
  ownerDisplayName: string
  authorName: string
  message: string
  status: 'PENDING' | 'REPORTED' | 'APPROVED' | 'HIDDEN'
  reportCount: number
  createdAt: string
  updatedAt: string
}

export type AdminMemorial = {
  id: string
  slug: string
  petName: string
  species: string
  farewellMessage: string | null
  status: 'PUBLISHED' | 'ARCHIVED'
  visibility: Memorial['visibility']
  publishedAt: string | null
  createdAt: string
  updatedAt: string
  ownerId: string
  ownerDisplayName: string
}

export type AdminOrder = {
  id: string
  planCode: BillingPlan['code']
  planName: string
  amountCents: number
  currency: string
  status: BillingOrder['status']
  paymentReference: string | null
  paidAt: string | null
  refundedAt: string | null
  createdAt: string
  ownerId: string
  ownerDisplayName: string
  ownerEmail: string
}

export type AdminAuditLog = {
  id: string
  actorUserId: string | null
  actorDisplayName: string | null
  action: string
  targetType: string
  targetId: string
  reason: string
  details: string | null
  createdAt: string
}

export type CustomServiceStatus =
  | 'SUBMITTED'
  | 'MATERIALS_PENDING'
  | 'IN_PROGRESS'
  | 'REVISION'
  | 'OUT_OF_SCOPE'
  | 'DELIVERED'
  | 'COMPLETED'
  | 'CANCELED'

export type CustomServiceRequest = {
  id: string
  userId: string
  contactDetails: string
  requestDetails: string
  status: CustomServiceStatus
  materialsReady: boolean
  assignee: string | null
  dueDate: string | null
  revisionCount: number
  customerMessage: string | null
  createdAt: string
  updatedAt: string
}

export type AdminCustomServiceRequest = CustomServiceRequest & {
  ownerDisplayName: string
  ownerEmail: string
}

export async function register(email: string, password: string, displayName: string) {
  return api<Account>('/auth/register', {
    method: 'POST',
    body: JSON.stringify({ email, password, displayName }),
  })
}

export async function login(email: string, password: string) {
  return api<Account>('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) })
}

export async function currentAccount() {
  return api<Account>('/auth/me')
}

export async function getServiceHealth() {
  return api<ServiceHealth>('/health')
}

export async function logout() {
  return api<void>('/auth/logout', { method: 'POST' })
}

export async function requestAccountDeletion() {
  return api<{ scheduled: boolean; scheduledFor: string }>('/auth/account', {
    method: 'DELETE',
    body: JSON.stringify({ confirmation: 'DELETE' }),
  })
}

export async function getAccountDeletionStatus() {
  return api<AccountDeletionStatus>('/auth/account/deletion')
}

export async function cancelAccountDeletion() {
  return api<AccountDeletionStatus>('/auth/account/deletion/cancel', { method: 'POST' })
}

export async function downloadAccountExport() {
  const link = await api<{ downloadUrl: string; expiresAt: string }>('/auth/export-links', {
    method: 'POST',
  })
  const response = await fetch(link.downloadUrl, {
    credentials: 'include',
  })
  if (!response.ok) throw new Error('资料包暂时无法生成，请稍后重试。')
  return response.blob()
}

export async function uploadImage(file: File, onProgress?: UploadProgressHandler) {
  const form = new FormData()
  form.append('file', file)
  return uploadWithProgress<{ id: string; url: string; contentType: string }>(
    '/media/images',
    form,
    onProgress,
  )
}

export async function uploadVideo(file: File, onProgress?: UploadProgressHandler) {
  const form = new FormData()
  form.append('file', file)
  return uploadWithProgress<{ id: string; url: string; contentType: string }>(
    '/media/videos',
    form,
    onProgress,
  )
}

export async function uploadAudio(file: File, onProgress?: UploadProgressHandler) {
  const form = new FormData()
  form.append('file', file)
  return uploadWithProgress<{ id: string; url: string; contentType: string }>('/media/audio', form, onProgress)
}

export async function getMemorialExperience(id: string) { return api<MemorialExperience>(`/memorials/${id}/experience`) }
export async function getMemorialHabitat(id: string) { return api<MemorialHabitatSpace>(`/memorials/${id}/habitat`) }
export async function saveMemorialHabitat(id: string, payload: Pick<MemorialHabitatSpace, 'scene' | 'title' | 'light'>) {
  return api<MemorialHabitatSpace>(`/memorials/${id}/habitat`, { method: 'PUT', body: JSON.stringify(payload) })
}
export async function addHabitatItem(id: string, payload: { kind: HabitatItem['kind']; xPercent: number; yPercent: number }) {
  return api<HabitatItem>(`/memorials/${id}/habitat/items`, { method: 'POST', body: JSON.stringify(payload) })
}
export async function deleteHabitatItem(id: string, itemId: string) {
  return api<{ deleted: boolean }>(`/memorials/${id}/habitat/items/${itemId}`, { method: 'DELETE' })
}
export async function addHabitatNote(id: string, payload: Omit<HabitatNote, 'id' | 'createdAt'>) {
  return api<HabitatNote>(`/memorials/${id}/habitat/notes`, { method: 'POST', body: JSON.stringify(payload) })
}
export async function deleteHabitatNote(id: string, noteId: string) {
  return api<{ deleted: boolean }>(`/memorials/${id}/habitat/notes/${noteId}`, { method: 'DELETE' })
}
export async function getPublicMemorialExperience(slug: string) { return api<MemorialExperience>(`/memorials/public/${slug}/experience`) }
export async function addDayMoment(id: string, payload: DayMomentPayload) {
  return api<DayMoment>(`/memorials/${id}/day-moments`, { method: 'POST', body: JSON.stringify(payload) })
}
export async function updateDayMoment(id: string, momentId: string, payload: DayMomentPayload) {
  return api<DayMoment>(`/memorials/${id}/day-moments/${momentId}`, { method: 'PUT', body: JSON.stringify(payload) })
}
export async function deleteDayMoment(id: string, momentId: string) {
  return api<{ deleted: boolean }>(`/memorials/${id}/day-moments/${momentId}`, { method: 'DELETE' })
}
export async function saveLifeDetail(id: string, detailKey: string, answer: string) {
  return api<MemorialExperience>(`/memorials/${id}/life-details/${detailKey}`, {
    method: 'PUT', body: JSON.stringify({ answer }),
  })
}
export async function addSoundMemory(id: string, mediaId: string, title: string, story: string | null) {
  return api<SoundMemory>(`/memorials/${id}/sounds`, { method: 'POST', body: JSON.stringify({ mediaId, title, story }) })
}
export async function deleteSoundMemory(id: string, soundId: string) { return api<{ deleted: boolean }>(`/memorials/${id}/sounds/${soundId}`, { method: 'DELETE' }) }
export async function saveInterviewAnswer(id: string, promptKey: string, answer: string) {
  return api<MemorialExperience>(`/memorials/${id}/interviews/${promptKey}`, { method: 'PUT', body: JSON.stringify({ answer }) })
}
export async function addKeepsake(id: string, title: string, story: string) {
  return api<Keepsake>(`/memorials/${id}/keepsakes`, { method: 'POST', body: JSON.stringify({ title, story }) })
}
export async function deleteKeepsake(id: string, keepsakeId: string) { return api<{ deleted: boolean }>(`/memorials/${id}/keepsakes/${keepsakeId}`, { method: 'DELETE' }) }
export async function saveBurialRecord(id: string, payload: Pick<BurialRecord, 'dispositionType' | 'occurredOn' | 'region' | 'placeName' | 'remembranceText'>) {
  return api<BurialRecord>(`/memorials/${id}/burial`, { method: 'PUT', body: JSON.stringify(payload) })
}
export async function submitBurialRecord(id: string) { return api<BurialRecord>(`/memorials/${id}/burial/submit`, { method: 'POST', body: JSON.stringify({ attested: true }) }) }
export async function listAdminBurialRecords(status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'ALL' = 'PENDING') { return api<BurialRecord[]>(`/admin/burial-records?status=${status}`) }
export async function moderateBurialRecord(id: string, status: 'APPROVED' | 'REJECTED', reason: string) {
  return api<BurialRecord>(`/admin/burial-records/${id}`, { method: 'PATCH', body: JSON.stringify({ status, reason }) })
}

export async function createMemorial(payload: MemorialPayload) {
  return api<Memorial>('/memorials', { method: 'POST', body: JSON.stringify(payload) })
}

export async function listMemorials() {
  return api<Memorial[]>('/memorials/mine')
}

export async function getMemorial(id: string) {
  return api<Memorial>(`/memorials/${id}`)
}

export async function updateMemorial(id: string, payload: MemorialPayload) {
  return api<Memorial>(`/memorials/${id}`, { method: 'PUT', body: JSON.stringify(payload) })
}

export async function publishMemorial(id: string) {
  return api<Memorial>(`/memorials/${id}/publish`, { method: 'POST' })
}

export async function archiveMemorial(id: string) {
  return api<Memorial>(`/memorials/${id}/archive`, { method: 'POST' })
}

export async function restoreMemorial(id: string) {
  return api<Memorial>(`/memorials/${id}/restore`, { method: 'POST' })
}

export async function recordMemorialShare(
  id: string,
  type: 'LINK_COPIED' | 'SHARE_CARD_DOWNLOADED',
) {
  return api<{ recorded: boolean }>(`/memorials/${id}/share-events`, {
    method: 'POST',
    body: JSON.stringify({ type }),
  })
}

export async function publicMemorial(slug: string) {
  return api<{
    memorial: Memorial
    tributes: Tribute[]
    timelineEntries: TimelineEntry[]
    letter: MemorialLetter | null
    galleryItems: GalleryItem[]
    lightCount: number
  }>(`/memorials/public/${slug}`)
}

export async function discoverMemorials(limit = 24) {
  return api<CommunityMemorial[]>(`/memorials/public/discover?limit=${limit}`)
}

export async function lightMemorial(slug: string) {
  return api<{ lit: boolean; count: number }>(`/memorials/public/${slug}/lights`, {
    method: 'POST',
  })
}

export async function unlockMemorial(slug: string, accessCode: string) {
  return api<{ granted: boolean }>(`/memorials/public/${slug}/unlock`, {
    method: 'POST',
    body: JSON.stringify({ accessCode }),
  })
}

export async function addTribute(slug: string, authorName: string, message: string) {
  return api<Tribute>(`/memorials/public/${slug}/tributes`, {
    method: 'POST',
    body: JSON.stringify({ authorName, message }),
  })
}

export async function reportTribute(slug: string, tributeId: string) {
  return api<{ reported: boolean }>(`/memorials/public/${slug}/tributes/${tributeId}/report`, {
    method: 'POST',
  })
}

export async function listOwnerTributes(memorialId: string) {
  return api<Tribute[]>(`/memorials/${memorialId}/tributes`)
}

export async function updateTributeStatus(
  memorialId: string,
  tributeId: string,
  status: 'APPROVED' | 'HIDDEN',
) {
  return api<Tribute>(`/memorials/${memorialId}/tributes/${tributeId}`, {
    method: 'PATCH',
    body: JSON.stringify({ status }),
  })
}

export async function listTimeline(memorialId: string) {
  return api<TimelineEntry[]>(`/memorials/${memorialId}/timeline`)
}

export async function createTimelineEntry(memorialId: string, payload: TimelinePayload) {
  return api<TimelineEntry>(`/memorials/${memorialId}/timeline`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export async function updateTimelineEntry(
  memorialId: string,
  entryId: string,
  payload: TimelinePayload,
) {
  return api<TimelineEntry>(`/memorials/${memorialId}/timeline/${entryId}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export async function deleteTimelineEntry(memorialId: string, entryId: string) {
  return api<{ deleted: boolean }>(`/memorials/${memorialId}/timeline/${entryId}`, {
    method: 'DELETE',
  })
}

export async function reorderTimeline(memorialId: string, entryIds: string[]) {
  return api<TimelineEntry[]>(`/memorials/${memorialId}/timeline/order`, {
    method: 'PUT',
    body: JSON.stringify({ entryIds }),
  })
}

export async function getLetter(memorialId: string) {
  return api<MemorialLetter | null>(`/memorials/${memorialId}/letter`)
}

export async function saveLetter(memorialId: string, subject: string | null, body: string) {
  return api<MemorialLetter>(`/memorials/${memorialId}/letter`, {
    method: 'PUT',
    body: JSON.stringify({ subject, body }),
  })
}

export async function listGallery(memorialId: string) {
  return api<GalleryItem[]>(`/memorials/${memorialId}/gallery`)
}

export async function addGalleryItem(memorialId: string, mediaId: string, caption?: string | null) {
  return api<GalleryItem>(`/memorials/${memorialId}/gallery`, {
    method: 'POST',
    body: JSON.stringify({ mediaId, caption: caption || null }),
  })
}

export async function deleteGalleryItem(memorialId: string, galleryItemId: string) {
  return api<{ deleted: boolean }>(`/memorials/${memorialId}/gallery/${galleryItemId}`, {
    method: 'DELETE',
  })
}

export async function updateGalleryCaption(
  memorialId: string,
  galleryItemId: string,
  caption: string | null,
) {
  return api<GalleryItem>(`/memorials/${memorialId}/gallery/${galleryItemId}`, {
    method: 'PUT',
    body: JSON.stringify({ caption }),
  })
}

export async function reorderGallery(memorialId: string, itemIds: string[]) {
  return api<GalleryItem[]>(`/memorials/${memorialId}/gallery/order`, {
    method: 'PUT',
    body: JSON.stringify({ itemIds }),
  })
}

export async function listBillingPlans() {
  return api<BillingPlan[]>('/billing/plans')
}

export async function listBillingOrders() {
  return api<BillingOrder[]>('/billing/orders')
}

export async function listAccountEntitlements() {
  return api<AccountEntitlement[]>('/billing/entitlements')
}

export async function getAccountLimits() {
  return api<AccountLimits>('/billing/limits')
}

export async function getAccountCapabilities() {
  return api<AccountCapabilities>('/billing/capabilities')
}

export async function getRitualSpace(memorialId: string) {
  return api<RitualSpace>(`/memorials/${memorialId}/ritual-space`)
}

export type ArchiveEntryPayload = Omit<ArchiveEntry, 'id' | 'memorialId' | 'mediaUrl' | 'createdAt' | 'updatedAt'>
export async function createArchiveEntry(memorialId: string, payload: ArchiveEntryPayload) {
  return api<ArchiveEntry>(`/memorials/${memorialId}/archive-entries`, { method: 'POST', body: JSON.stringify(payload) })
}
export async function updateArchiveEntry(memorialId: string, entryId: string, payload: ArchiveEntryPayload) {
  return api<ArchiveEntry>(`/memorials/${memorialId}/archive-entries/${entryId}`, { method: 'PUT', body: JSON.stringify(payload) })
}
export async function deleteArchiveEntry(memorialId: string, entryId: string) {
  return api<{ deleted: boolean }>(`/memorials/${memorialId}/archive-entries/${entryId}`, { method: 'DELETE' })
}

export type AnniversaryPayload = Omit<Anniversary, 'id' | 'memorialId' | 'createdAt' | 'updatedAt'>
export async function createAnniversary(memorialId: string, payload: AnniversaryPayload) {
  return api<Anniversary>(`/memorials/${memorialId}/anniversaries`, { method: 'POST', body: JSON.stringify(payload) })
}
export async function updateAnniversary(memorialId: string, anniversaryId: string, payload: AnniversaryPayload) {
  return api<Anniversary>(`/memorials/${memorialId}/anniversaries/${anniversaryId}`, { method: 'PUT', body: JSON.stringify(payload) })
}
export async function deleteAnniversary(memorialId: string, anniversaryId: string) {
  return api<{ deleted: boolean }>(`/memorials/${memorialId}/anniversaries/${anniversaryId}`, { method: 'DELETE' })
}

export async function createTimeCapsule(memorialId: string, payload: Pick<TimeCapsule, 'title' | 'body' | 'mediaId' | 'unlockOn' | 'visibility'>) {
  return api<TimeCapsule>(`/memorials/${memorialId}/capsules`, { method: 'POST', body: JSON.stringify(payload) })
}
export async function openTimeCapsule(memorialId: string, capsuleId: string) {
  return api<TimeCapsule>(`/memorials/${memorialId}/capsules/${capsuleId}/open`, { method: 'POST' })
}
export async function deleteTimeCapsule(memorialId: string, capsuleId: string) {
  return api<{ deleted: boolean }>(`/memorials/${memorialId}/capsules/${capsuleId}`, { method: 'DELETE' })
}

export async function completeRitual(memorialId: string, payload: Pick<RitualRecord, 'ritualType' | 'ritualAction' | 'note' | 'ambientEnabled'>) {
  return api<RitualRecord>(`/memorials/${memorialId}/rituals`, { method: 'POST', body: JSON.stringify(payload) })
}
export async function getYearbook(memorialId: string, year?: number) {
  return api<YearbookPreview>(`/memorials/${memorialId}/yearbook${year ? `?year=${year}` : ''}`)
}

export async function getDigitalLifeWorkspace(memorialId: string) {
  return api<DigitalLifeWorkspace>(`/memorials/${memorialId}/digital-life`)
}
export async function enableDigitalLife(memorialId: string) {
  return api<DigitalLifeWorkspace>(`/memorials/${memorialId}/digital-life/consent`, { method: 'PUT', body: JSON.stringify({ profileConsent: true, textProcessingConsent: true, consentVersion: 'DIGITAL_LIFE_V1' }) })
}
export async function updateDigitalLifeStatus(memorialId: string, status: 'ACTIVE' | 'PAUSED' | 'ARCHIVED') {
  return api<DigitalLifeWorkspace>(`/memorials/${memorialId}/digital-life/status`, { method: 'PUT', body: JSON.stringify({ status }) })
}
export async function addDigitalLifeFact(memorialId: string, payload: Omit<DigitalLifeFact, 'id' | 'profileId' | 'createdAt' | 'updatedAt'>) {
  return api<DigitalLifeFact>(`/memorials/${memorialId}/digital-life/facts`, { method: 'POST', body: JSON.stringify(payload) })
}
export async function updateDigitalLifeFactStatus(memorialId: string, factId: string, status: DigitalLifeFact['verificationStatus']) {
  return api<DigitalLifeFact>(`/memorials/${memorialId}/digital-life/facts/${factId}/status`, { method: 'PUT', body: JSON.stringify({ status }) })
}
export async function deleteDigitalLifeFact(memorialId: string, factId: string) {
  return api<{ deleted: boolean }>(`/memorials/${memorialId}/digital-life/facts/${factId}`, { method: 'DELETE' })
}
export async function askDigitalLife(memorialId: string, question: string) {
  return api<DigitalLifeAnswer>(`/memorials/${memorialId}/digital-life/questions`, { method: 'POST', body: JSON.stringify({ question }) })
}
export async function deleteDigitalLife(memorialId: string) {
  return api<{ deleted: boolean }>(`/memorials/${memorialId}/digital-life`, { method: 'DELETE' })
}

export async function createBillingOrder(planCode: BillingPlan['code'], idempotencyKey: string) {
  return api<BillingOrder>('/billing/orders', {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify({ planCode }),
  })
}

export async function mockPayBillingOrder(orderId: string) {
  return api<{ order: BillingOrder; entitlement: AccountEntitlement | null; newlyPaid: boolean }>(
    `/billing/orders/${orderId}/mock-pay`,
    {
      method: 'POST',
    },
  )
}

export async function getAdminOverview() {
  return api<AdminOverview>('/admin/overview')
}

export async function listAdminTributes(
  status: 'PENDING' | 'REPORTED' | 'APPROVED' | 'HIDDEN' | 'ALL',
) {
  return api<AdminTribute[]>(`/admin/tributes?status=${status}`)
}

export async function moderateAdminTribute(
  tributeId: string,
  status: 'APPROVED' | 'HIDDEN',
  reason: string,
) {
  return api<AdminTribute>(`/admin/tributes/${tributeId}`, {
    method: 'PATCH',
    body: JSON.stringify({ status, reason }),
  })
}

export async function listAdminMemorials(status: 'PUBLISHED' | 'ARCHIVED' | 'ALL' = 'PUBLISHED') {
  return api<AdminMemorial[]>(`/admin/memorials?status=${status}`)
}

export async function archiveAdminMemorial(memorialId: string, reason: string) {
  return api<AdminMemorial>(`/admin/memorials/${memorialId}`, {
    method: 'PATCH',
    body: JSON.stringify({ reason }),
  })
}

export async function refundAdminMockOrder(orderId: string, reason: string) {
  return api<{
    orderId: string
    orderStatus: BillingOrder['status']
    entitlementStatus: AccountEntitlement['status'] | null
    newlyRefunded: boolean
  }>(`/admin/orders/${orderId}/mock-refund`, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  })
}

export async function listAdminOrders() {
  return api<AdminOrder[]>('/admin/orders')
}

export async function listAdminAuditLogs() {
  return api<AdminAuditLog[]>('/admin/audit-logs')
}

export async function createCustomServiceRequest(contactDetails: string, requestDetails: string) {
  return api<CustomServiceRequest>('/custom-service-requests', {
    method: 'POST',
    body: JSON.stringify({ contactDetails, requestDetails }),
  })
}

export async function listCustomServiceRequests() {
  return api<CustomServiceRequest[]>('/custom-service-requests')
}

export async function confirmCustomServiceDelivery(requestId: string) {
  return api<CustomServiceRequest>(`/custom-service-requests/${requestId}/confirm-delivery`, {
    method: 'POST',
  })
}

export async function listAdminCustomServiceRequests(
  status: 'OPEN' | 'ALL' | CustomServiceStatus = 'OPEN',
) {
  return api<AdminCustomServiceRequest[]>(`/admin/custom-service-requests?status=${status}`)
}

export async function updateAdminCustomServiceRequest(
  requestId: string,
  payload: Pick<
    CustomServiceRequest,
    'status' | 'materialsReady' | 'assignee' | 'dueDate' | 'revisionCount' | 'customerMessage'
  > & { reason: string },
) {
  return api<CustomServiceRequest>(`/admin/custom-service-requests/${requestId}`, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  })
}
