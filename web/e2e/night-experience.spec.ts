import { expect, test, type Page } from '@playwright/test'
import { mkdir } from 'node:fs/promises'

const PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9n2V8AAAAASUVORK5CYII=',
  'base64',
)
const photo = { name: 'memory.png', mimeType: 'image/png', buffer: PNG }
const evidence = '../docs/night-review'

async function createTestNest(page: Page) {
  await page.goto('/login')
  await page.request.get('/api/v1/auth/csrf')
  let token = (await page.context().cookies()).find((cookie) => cookie.name === 'XSRF-TOKEN')!.value
  const registration = await page.request.post('/api/v1/auth/register', {
    headers: { 'X-XSRF-TOKEN': decodeURIComponent(token) },
    data: {
      email: `night-${Date.now()}@example.test`,
      password: 'night-review-password',
      displayName: '星夜测试家人',
    },
  })
  expect(registration.status()).toBe(201)
  await page.request.get('/api/v1/auth/csrf')
  token = (await page.context().cookies()).find((cookie) => cookie.name === 'XSRF-TOKEN')!.value
  const headers = { 'X-XSRF-TOKEN': decodeURIComponent(token) }
  const upload = await page.request.post('/api/v1/media/images', {
    headers,
    multipart: { file: photo },
  })
  expect(upload.ok()).toBeTruthy()
  const media = (await upload.json()).data
  const creation = await page.request.post('/api/v1/memorials', {
    headers,
    data: {
      petName: '星夜验收小窝',
      species: '小狗',
      coverMediaId: media.id,
      theme: 'NIGHT',
      visibility: 'LINK',
      version: 0,
    },
  })
  expect(creation.status()).toBe(201)
  const nest = (await creation.json()).data
  await page.goto(`/editor/${nest.id}`)
  await expect(page.getByRole('heading', { name: '亲友会看到的样子' })).toBeVisible()
  return nest
}

test('B public routes preserve readable layouts and mobile navigation', async ({ page }, testInfo) => {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  for (const width of [390, 1440]) {
    await page.setViewportSize({ width, height: 900 })
    for (const route of [
      '/',
      '/create',
      '/examples',
      '/examples/doubao',
      '/examples/tuantuan',
      '/examples/xiaomai',
      '/pricing',
      '/login',
      '/help',
      '/privacy',
      '/terms',
      '/content',
      '/refund',
      '/health',
      '/not-found',
    ]) {
      await page.goto(route)
      await expect(page.locator('main h1').first()).toBeVisible()
      await expect(page.locator('.night-site')).toBeVisible()
      expect(
        await page.evaluate(
          () => document.documentElement.scrollWidth <= document.documentElement.clientWidth,
        ),
      ).toBeTruthy()

      // A full-page screenshot does not itself scroll each section into the viewport.
      // Reveal every motion-gated section first so the captured artifact represents the
      // page a visitor sees after reading it, rather than a stack of intentionally hidden
      // off-screen sections.
      const revealSections = page.locator('[data-reveal]')
      for (let index = 0; index < (await revealSections.count()); index += 1) {
        await revealSections.nth(index).scrollIntoViewIfNeeded()
      }
      await page.screenshot({
        path: testInfo.outputPath(`${route.replaceAll('/', '-') || 'home'}-${width}.png`),
        fullPage: true,
      })
    }
  }
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/')
  await page.getByRole('button', { name: '菜单', exact: true }).click()
  await page
    .getByRole('navigation', { name: '手机导航' })
    .getByRole('link', { name: '登录 / 注册' })
    .click()
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('button', { name: '菜单', exact: true })).toHaveAttribute(
    'aria-expanded',
    'false',
  )
  expect(errors).toEqual([])
})

test('creation restores both text and photo after browser reload', async ({ page }) => {
  await page.goto('/create')
  await page.getByLabel('TA 的昵称').fill('本机草稿小猫')
  await page.getByRole('button', { name: '继续', exact: true }).click()
  await page.locator('input[type=file]').setInputFiles(photo)
  await expect(page.getByRole('status')).toContainText('已暂存到本机')
  await page.reload()
  await expect(page.getByLabel('TA 的昵称')).toHaveValue('本机草稿小猫')
  await page.getByRole('button', { name: '继续', exact: true }).click()
  await expect(page.getByText('已经选好第一张照片')).toBeVisible()
  await page
    .locator('input[type=file]')
    .setInputFiles({ name: 'bad.txt', mimeType: 'text/plain', buffer: Buffer.from('invalid') })
  await expect(page.getByRole('alert')).toContainText('不超过 5MB')
})

test('editor preserves in-flight edits and retries failed uploads without duplicate photos', async ({
  page,
}) => {
  // eslint-disable-next-line playwright/no-skipped-test
  test.skip(process.env.E2E_FULL_STACK !== 'true', 'Requires local Java API')
  test.setTimeout(60_000)
  const nest = await createTestNest(page)
  await expect(page.getByText('已准备至少 3 张照片（当前 1 张）')).toBeVisible()
  let saves = 0
  await page.route(`**/api/v1/memorials/${nest.id}`, async (route) => {
    if (route.request().method() !== 'PUT') {
      await route.continue()
      return
    }
    saves += 1
    const response = await route.fetch()
    if (saves === 1) await new Promise((resolve) => setTimeout(resolve, 1700))
    await route.fulfill({ response })
  })
  await page.getByLabel('TA 的小习惯与喜欢的事').fill('第一次输入')
  await page.getByRole('button', { name: '保存草稿', exact: true }).click()
  await expect(page.getByRole('button', { name: '正在保存…', exact: true })).toBeDisabled()
  await page.getByLabel('TA 的小习惯与喜欢的事').fill('请求进行中继续输入的完整内容')
  await expect
    .poll(
      async () =>
        (await (await page.request.get(`/api/v1/memorials/${nest.id}`)).json()).data.aboutTa,
      { timeout: 12000 },
    )
    .toBe('请求进行中继续输入的完整内容')
  await expect(page.locator('.autosave-status')).toContainText('已自动保存')
  await page.reload()
  await expect(page.getByLabel('TA 的小习惯与喜欢的事')).toHaveValue('请求进行中继续输入的完整内容')
  await page.route('**/api/v1/media/images', (route) =>
    route.fulfill({ status: 503, json: { data: { message: '测试网络中断' } } }),
  )
  await page.locator('input[type=file][multiple]').setInputFiles(photo)
  await expect(page.locator('.gallery-upload-queue')).toContainText('测试网络中断')
  await page.unroute('**/api/v1/media/images')
  await page.getByRole('button', { name: '重试上传' }).click()
  await expect(page.locator('.gallery-editor-list figure')).toHaveCount(2)
  await expect(page.locator('.gallery-upload-queue')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '保存并发布' })).toBeDisabled()
  await mkdir(evidence, { recursive: true })
  for (const width of [1440, 768, 390]) {
    await page.setViewportSize({ width, height: 900 })
    await page.screenshot({ path: `${evidence}/editor-${width}.png`, fullPage: true })
  }
  await page.getByRole('tab', { name: '编辑内容' }).focus()
  await page.keyboard.press('ArrowRight')
  await expect(page.getByRole('tab', { name: '实时预览' })).toBeFocused()
  await expect(page.locator('#editor-edit-panel')).toBeHidden()
  await page.getByText('展开内容预览').click()
  await expect(page.locator('.preview-mini-gallery img')).toHaveCount(2)
  await page.goto('/account')
  await expect(page.locator('.account-card')).toContainText('私密草稿')
  await page.screenshot({ path: `${evidence}/account-390.png`, fullPage: true })
  for (const width of [390, 1440]) {
    await page.setViewportSize({ width, height: 900 })
    for (const route of ['/account', '/account/orders', '/custom-service']) {
      await page.goto(route)
      await expect(page.locator('main h1')).toBeVisible()
      await page.screenshot({
        path: `${evidence}/${route.replaceAll('/', '-')}-${width}.png`,
        fullPage: true,
      })
    }
  }
})

test('theme studio previews safely, respects appearance entitlements, and preserves memorial content', async ({ page }) => {
  // eslint-disable-next-line playwright/no-skipped-test
  test.skip(process.env.E2E_FULL_STACK !== 'true', 'Requires local Java API')
  test.setTimeout(60_000)
  const nest = await createTestNest(page)

  await page.goto(`/editor/${nest.id}/themes`)
  await expect(page.getByRole('heading', { name: /让 星夜验收小窝 的空间/ })).toBeVisible()
  await expect(page.getByRole('option', { name: /星夜来信/ })).toHaveAttribute('aria-selected', 'true')
  await page.getByRole('option', { name: /窗边午后/ }).click()
  await expect(page.getByText('完整艺术指导包属于会员外观权益')).toBeVisible()
  expect(
    (await (await page.request.get(`/api/v1/memorials/${nest.id}`)).json()).data.theme,
  ).toBe('NIGHT')

  for (const width of [1440, 390]) {
    await page.setViewportSize({ width, height: 900 })
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= document.documentElement.clientWidth,
      ),
    ).toBeTruthy()
    await page.screenshot({ path: `${evidence}/theme-studio-${width}.png`, fullPage: true })
  }

  await page.goto('/account/orders')
  page.once('dialog', (dialog) => dialog.accept())
  await page.getByRole('button', { name: '本地模拟开通' }).first().click()
  await expect(page.getByText('已开通「温暖守护」，权益已写入账户。')).toBeVisible()

  await page.goto(`/editor/${nest.id}/themes`)
  await page.getByRole('option', { name: /窗边午后/ }).click()
  await page.getByRole('button', { name: '保存并应用' }).click()
  await expect(page.getByText('照片、文字、可见范围和导出内容都没有改变。')).toBeVisible()
  const afterTheme = (await (await page.request.get(`/api/v1/memorials/${nest.id}`)).json()).data
  expect(afterTheme.theme).toBe('SUNNY')
  expect(afterTheme.petName).toBe('星夜验收小窝')

  await page.getByRole('button', { name: '还原进入时主题' }).click()
  await expect(page.getByText('已应用「星夜来信」。照片、文字、可见范围和导出内容都没有改变。')).toBeVisible()
  expect(
    (await (await page.request.get(`/api/v1/memorials/${nest.id}`)).json()).data.theme,
  ).toBe('NIGHT')
})
