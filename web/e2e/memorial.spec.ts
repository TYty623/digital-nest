import { expect, test, type Page } from '@playwright/test'

const PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9n2V8AAAAASUVORK5CYII=',
  'base64',
)

function imageFile(name: string) {
  return { name, mimeType: 'image/png', buffer: PNG }
}

function videoFile(name: string) {
  return {
    name,
    mimeType: 'video/mp4',
    buffer: Buffer.from([0x00, 0x00, 0x00, 0x10, 0x66, 0x74, 0x79, 0x70, 0x69, 0x73, 0x6f, 0x6d]),
  }
}

async function ensureBootstrapAdmin(page: Page) {
  const csrfResponse = await page.request.get('/api/v1/auth/csrf')
  expect(csrfResponse.ok()).toBeTruthy()
  const csrfCookie = csrfResponse.headersArray().find((header) =>
    header.name.toLowerCase() === 'set-cookie' && header.value.startsWith('XSRF-TOKEN='),
  )?.value
  const csrfToken = csrfCookie?.match(/^XSRF-TOKEN=([^;]+)/)?.[1]
  expect(csrfToken).toBeTruthy()

  const registration = await page.request.post('/api/v1/auth/register', {
    headers: { 'X-XSRF-TOKEN': decodeURIComponent(csrfToken!) },
    data: {
      email: 'admin@local.test',
      password: 'a-long-enough-password',
      displayName: '本地运营',
    },
  })
  expect([201, 409]).toContain(registration.status())
}

test('visitors can reach the memorial creation flow', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByRole('heading', { name: /记住 TA\s*怎样生活过，\s*不只是怎样离开。/ })).toBeVisible()
  await page.keyboard.press('Tab')
  const skipLink = page.getByRole('link', { name: '跳到主要内容' })
  await expect(skipLink).toBeFocused()
  await page.keyboard.press('Enter')
  await expect(page.locator('#main-content')).toBeFocused()
  await page.getByRole('link', { name: '开始记录 TA 的一天 ↗' }).first().click()
  await expect(page).toHaveURL(/\/create$/)
  await expect(page.getByRole('heading', { name: /先为 TA 留下\s*一个小小的开场。/ })).toBeVisible()
})

test('key public and creation pages remain keyboard reachable without horizontal overflow', async ({ page }) => {
  const routes = [
    { path: '/', heading: /记住 TA/ },
    { path: '/create', heading: /先为 TA 留下/ },
    { path: '/examples/doubao', heading: '豆包' },
  ]
  for (const viewport of [
    { width: 390, height: 844 },
    { width: 768, height: 900 },
    { width: 1440, height: 900 },
  ]) {
    for (const route of routes) {
      await page.setViewportSize(viewport)
      await page.goto(route.path)
      await expect(
        page.getByRole('heading', { name: route.heading, exact: route.path === '/examples/doubao' }),
      ).toBeVisible()

      const layout = await page.evaluate(() => ({
        clientWidth: document.documentElement.clientWidth,
        scrollWidth: document.documentElement.scrollWidth,
      }))
      expect(layout.scrollWidth).toBeLessThanOrEqual(layout.clientWidth)

      const skipLink = page.getByRole('link', { name: '跳到主要内容' })
      await skipLink.focus()
      await expect(skipLink).toBeFocused()
      await skipLink.press('Enter')
      await expect(page.locator('#main-content')).toBeFocused()
    }
  }
})

test('visitors can check the Java service health without signing in', async ({ page }) => {
  // eslint-disable-next-line playwright/no-skipped-test
  test.skip(process.env.E2E_FULL_STACK !== 'true', 'Requires the local Java API.')

  await page.goto('/health')
  await expect(page.getByRole('heading', { name: /先确认每一盏灯/ })).toBeVisible()
  await expect(page.getByText('后端服务运行正常')).toBeVisible()
})

test('pricing reads the server-owned plan limits including supported short videos', async ({ page }) => {
  // The plan catalogue is fetched from the Java API, so this is a full-stack assertion.
  // eslint-disable-next-line playwright/no-skipped-test
  test.skip(process.env.E2E_FULL_STACK !== 'true', 'Requires the local Java API and database.')

  await page.goto('/pricing')

  await expect(page.getByRole('heading', { name: /让价格说清楚/ })).toBeVisible()
  await expect(page.getByRole('heading', { name: /^¥99\s*\/ 次$/ })).toBeVisible()
  await expect(page.getByText('80 张照片、3 个短视频与 30 个时间线节点')).toBeVisible()
  await expect(page.getByText('300 张照片、15 个短视频与 100 个时间线节点')).toBeVisible()
  await expect(page.getByText('全部六套外观作品集，贯穿公开页、分享卡和纪念册')).toHaveCount(2)
  await expect(page.getByText('星夜来信一套完整基础主题')).toHaveCount(1)
})

test('a bootstrap admin can open the public memorial moderation board and reverse a local mock payment', async ({ page }) => {
  // The local backend is started with BOOTSTRAP_ADMIN_EMAILS=admin@local.test.
  // eslint-disable-next-line playwright/no-skipped-test
  test.skip(process.env.E2E_FULL_STACK !== 'true', 'Requires the local Java API and bootstrap administrator.')

  await ensureBootstrapAdmin(page)
  await page.goto('/login?next=/admin')
  await page.getByLabel('邮箱').fill('admin@local.test')
  await page.getByLabel('密码').fill('a-long-enough-password')
  await page.getByRole('button', { name: '登录并继续' }).click()

  await expect(page).toHaveURL(/\/admin$/)
  await expect(page.getByRole('heading', { name: /把需要处理的事/ })).toBeVisible()
  await expect(page.getByText('当前受控媒体用量')).toBeVisible()
  await expect(page.getByRole('heading', { name: /必要时，先把页面安静地下线/ })).toBeVisible()
  await expect(page.getByText(/下线会立即关闭公开访问并留下原因/)).toBeVisible()
  for (const width of [390, 1440]) {
    await page.setViewportSize({ width, height: 900 })
    await page.screenshot({ path: `../docs/night-review/admin-${width}.png`, fullPage: true })
  }

  await page.goto('/account/orders')
  page.once('dialog', (dialog) => dialog.accept())
  await page.getByRole('button', { name: '本地模拟开通' }).first().click()
  await expect(page.getByText('已开通「温暖守护」，权益已写入账户。')).toBeVisible()

  await page.goto('/admin')
  await expect(page.getByRole('heading', { name: /需要撤销的模拟订单/ })).toBeVisible()
  const adminRefundCards = page.locator('article.review-card').filter({ hasText: '本地运营 · 温暖守护' })
  while (await adminRefundCards.count()) {
    const card = adminRefundCards.first()
    await card.getByLabel('温暖守护 的退款原因').fill('本地验收需要撤销这笔模拟付款')
    page.once('dialog', (dialog) => dialog.accept())
    await card.getByRole('button', { name: '撤销本地模拟付款' }).click()
    await expect(card).toHaveCount(0)
  }
})

test('a new owner can create, publish, and share a memorial', async ({ page, browser }) => {
  // The full journey requires an externally started Java API and local database.
  // eslint-disable-next-line playwright/no-skipped-test
  test.skip(process.env.E2E_FULL_STACK !== 'true', 'Requires the local Java API and database.')
  test.setTimeout(90_000)

  const petName = `小麦${Date.now().toString().slice(-5)}`
  const email = `e2e-${Date.now()}@example.test`

  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/create')
  const createLayout = await page.evaluate(() => ({
    clientWidth: document.documentElement.clientWidth,
    scrollWidth: document.documentElement.scrollWidth,
  }))
  expect(createLayout.scrollWidth).toBeLessThanOrEqual(createLayout.clientWidth)
  await page.getByLabel('TA 的昵称').fill(petName)
  await page.getByLabel('来到身边的日子').fill('2021-06-01')
  await page.getByLabel('想念开始的日子').fill('2025-01-01')
  await page.getByRole('button', { name: '继续' }).click()
  await page.locator('input[type="file"]').setInputFiles(imageFile('cover.png'))
  await page.getByRole('button', { name: '继续' }).click()
  await page.getByLabel('如果现在能对 TA 说一句话').fill('谢谢你一直在门口等我回家。')
  await page.getByRole('button', { name: '继续' }).click()
  await page.getByRole('button', { name: '登录并保存小窝' }).click()

  await page.getByRole('button', { name: '还没有账户？创建一个' }).click()
  await page.getByLabel('你的昵称').fill('端到端测试用户')
  await page.getByLabel('邮箱').fill(email)
  await page.getByLabel('密码').fill('a-long-enough-password')
  await page.getByRole('button', { name: '创建账户并继续' }).click()
  await expect(page.getByRole('heading', { name: '把想念，安放在看得见的地方。' })).toBeVisible()
  await page.getByRole('link', { name: '返回页面编辑' }).click()
  await expect(page.getByRole('heading', { name: `慢慢把 ${petName} 的故事放好。` })).toBeVisible()
  const editorLayout = await page.evaluate(() => ({
    clientWidth: document.documentElement.clientWidth,
    scrollWidth: document.documentElement.scrollWidth,
  }))
  expect(editorLayout.scrollWidth).toBeLessThanOrEqual(editorLayout.clientWidth)
  const editorUrl = page.url()
  await page.getByRole('button', { name: '预览发布' }).click()
  await expect(page.getByRole('button', { name: '保存并发布' })).toBeDisabled()

  await page.setViewportSize({ width: 1440, height: 900 })
  await expect(page.getByRole('navigation', { name: '整理模块' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '亲友会看到的样子' })).toBeVisible()
  const desktopEditorLayout = await page.evaluate(() => ({
    clientWidth: document.documentElement.clientWidth,
    scrollWidth: document.documentElement.scrollWidth,
  }))
  expect(desktopEditorLayout.scrollWidth).toBeLessThanOrEqual(desktopEditorLayout.clientWidth)

  await page.setViewportSize({ width: 390, height: 844 })
  await page.getByRole('tab', { name: '实时预览' }).click()
  await expect(page.locator('#editor-preview-panel')).toBeVisible()
  await expect(page.locator('#editor-edit-panel')).toBeHidden()
  const mobilePreviewLayout = await page.evaluate(() => ({
    clientWidth: document.documentElement.clientWidth,
    scrollWidth: document.documentElement.scrollWidth,
  }))
  expect(mobilePreviewLayout.scrollWidth).toBeLessThanOrEqual(mobilePreviewLayout.clientWidth)
  await page.getByRole('tab', { name: '编辑内容' }).click()
  await expect(page.locator('#editor-edit-panel')).toBeVisible()

  await page.getByRole('button', { name: '生活里的 TA' }).click()
  await page.getByLabel('TA 的小习惯与喜欢的事').fill('最喜欢叼着玩具在门口等人，也总会把肚皮翻出来。')
  const browserLeaveGuardEnabled = await page.evaluate(() => {
    const event = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(event)
    return event.defaultPrevented
  })
  expect(browserLeaveGuardEnabled).toBe(true)
  await page.getByRole('button', { name: '照片与故事' }).click()
  await page.locator('input[type="file"][multiple]').setInputFiles([imageFile('gallery-1.png'), imageFile('gallery-2.png')])
  await expect(page.getByText('已收好 2 份媒体。')).toBeVisible()

  await page.getByRole('button', { name: '生活里的 TA' }).click()
  await page.getByLabel('这段记忆的标题').fill('第一次在门口等我回家')
  await page.getByLabel('关联相册照片（可选）').selectOption({ index: 1 })
  await page.getByRole('button', { name: '收好这段记忆' }).click()
  await expect(page.locator('.timeline-entry-image')).toHaveCount(1)
  await page.getByLabel('这段记忆的标题').fill('后来一起散步')
  await page.getByRole('button', { name: '收好这段记忆' }).click()
  const secondTimelineEntry = page
    .locator('.timeline-entry')
    .filter({ has: page.getByRole('heading', { name: '后来一起散步' }) })
  await secondTimelineEntry.getByRole('button', { name: '前移' }).click()
  await expect(page.locator('.timeline-entry').first().getByRole('heading', { name: '后来一起散步' })).toBeVisible()

  await page.getByRole('button', { name: '预览发布' }).click()
  await page.getByRole('button', { name: '保存并发布' }).click()
  await expect(page.getByText('已经发布。现在可以复制链接分享给亲友。')).toBeVisible()
  const shareHref = await page.getByRole('link', { name: '查看分享页 →' }).getAttribute('href')
  expect(shareHref).toMatch(/^\/m\//)
  await page.context().grantPermissions(['clipboard-write'], { origin: 'http://localhost:5173' })
  await page.getByRole('button', { name: '复制分享链接' }).click()
  await expect(page.getByText('分享链接已复制，可以发给想念 TA 的亲友。')).toBeVisible()

  await page.locator('input[type="file"][multiple]').setInputFiles(videoFile('not-yet-entitled.mp4'))
  await expect(page.getByText('当前套餐还可添加 0 个短视频；请升级套餐或移除已有短视频后再试。')).toBeVisible()

  await page.goto('/account')
  await expect(page.getByRole('heading', { name: new RegExp(`先想起 ${petName} 的这一点。`) })).toBeVisible()
  await expect(page.getByText('已收好的痕迹')).toBeVisible()
  await expect(page.getByRole('link', { name: '进入纪念仪式' })).toBeVisible()
  const accountLayout = await page.evaluate(() => ({
    clientWidth: document.documentElement.clientWidth,
    scrollWidth: document.documentElement.scrollWidth,
  }))
  expect(accountLayout.scrollWidth).toBeLessThanOrEqual(accountLayout.clientWidth)
  for (const width of [390, 1440]) {
    await page.setViewportSize({ width, height: 900 })
    await page.screenshot({ path: `../docs/night-review/account-${width}.png`, fullPage: true })
  }

  await page.goto('/account/orders')
  page.once('dialog', (dialog) => dialog.accept())
  await page.getByRole('button', { name: '本地模拟开通' }).first().click()
  await expect(page.getByText('已开通「温暖守护」，权益已写入账户。')).toBeVisible()
  await page.goto(editorUrl)
  await page.locator('input[type="file"][multiple]').setInputFiles(videoFile('first-memory.mp4'))
  await expect(page.getByText('已收好 1 份媒体。')).toBeVisible()
  await expect(page.locator('.gallery-editor-list video')).toHaveCount(1)
  await page.getByRole('button', { name: '更新已发布页面' }).click()
  await expect(page.getByText('已经发布。现在可以复制链接分享给亲友。')).toBeVisible()

  const visitor = await browser.newContext()
  const visitorPage = await visitor.newPage()
  await visitorPage.goto(shareHref || '/')
  await expect(visitorPage.getByRole('heading', { name: petName, exact: true })).toBeVisible()
  await expect(visitorPage.getByText('2021 年 6 月 1 日 - 2025 年 1 月 1 日')).toBeVisible()
  await expect(visitorPage.getByText('最喜欢叼着玩具在门口等人，也总会把肚皮翻出来。')).toBeVisible()
  await expect(visitorPage.locator('.public-gallery video')).toHaveCount(1)
  await expect(visitorPage.locator('.public-timeline-image')).toHaveCount(1)
  await expect(visitorPage.locator('.public-timeline li').first().getByRole('heading', { name: '后来一起散步' })).toBeVisible()
  await visitorPage.getByLabel('你的称呼').fill('小麦的朋友')
  await visitorPage.getByLabel('想对 TA 说的话').fill('谢谢你带来很多温暖。')
  await visitorPage.getByRole('button', { name: '留下这句话' }).click()
  await expect(visitorPage.getByText('谢谢你带来很多温暖。')).toBeVisible()
  await visitor.close()

  await page.getByLabel('链接加访问口令').check()
  await page.getByLabel('分享口令').fill('remember-123')
  await page.getByRole('button', { name: '更新已发布页面' }).click()
  await expect(page.getByText('已经发布。现在可以复制链接分享给亲友。')).toBeVisible()

  const passwordVisitor = await browser.newContext()
  const passwordVisitorPage = await passwordVisitor.newPage()
  await passwordVisitorPage.goto(shareHref || '/')
  await expect(passwordVisitorPage.getByRole('heading', { name: '这间小窝有一把小锁。' })).toBeVisible()
  await passwordVisitorPage.getByLabel('分享口令').fill('remember-123')
  await passwordVisitorPage.getByRole('button', { name: '打开这间小窝' }).click()
  await expect(passwordVisitorPage.getByRole('heading', { name: petName, exact: true })).toBeVisible()
  await passwordVisitor.close()

  await page.getByLabel('仅自己可见').check()
  await page.getByRole('button', { name: '保存草稿' }).click()
  await expect(page.getByText('已切换为仅自己可见并暂时下线。选择一种可分享范围后即可恢复原链接。')).toBeVisible()
  await expect(page.getByRole('button', { name: '选择分享范围后恢复' })).toBeDisabled()

  const privateVisitor = await browser.newContext()
  const privateVisitorPage = await privateVisitor.newPage()
  await privateVisitorPage.goto(shareHref || '/')
  await expect(privateVisitorPage.getByRole('heading', { name: '暂时没有找到这间小窝。' })).toBeVisible()
  await privateVisitor.close()

  await page.getByLabel('公开纪念页').check()
  await page.getByRole('button', { name: '保存草稿' }).click()
  page.once('dialog', (dialog) => dialog.accept())
  await page.getByRole('button', { name: '恢复分享' }).click()
  await expect(page.getByText('已恢复分享，原链接可以再次访问。')).toBeVisible()

  const publicVisitor = await browser.newContext()
  const publicVisitorPage = await publicVisitor.newPage()
  await publicVisitorPage.goto(shareHref || '/')
  await expect(publicVisitorPage.getByRole('heading', { name: petName, exact: true })).toBeVisible()
  await publicVisitorPage.getByRole('button', { name: /放大查看/ }).first().click()
  await expect(publicVisitorPage.getByRole('dialog', { name: '相册大图' })).toBeVisible()
  await publicVisitorPage.keyboard.press('ArrowRight')
  await expect(publicVisitorPage.getByRole('dialog').locator('.photo-dialog-toolbar').first()).toContainText('2 /')
  await publicVisitorPage.keyboard.press('Escape')
  await expect(publicVisitorPage.getByRole('dialog')).toBeHidden()
  for (const width of [390, 1440]) {
    await publicVisitorPage.setViewportSize({ width, height: 900 })
    await publicVisitorPage.screenshot({ path: `../docs/night-review/public-${width}.png`, fullPage: true })
  }
  await publicVisitor.close()
})
