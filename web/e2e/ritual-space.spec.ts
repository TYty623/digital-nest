import { expect, test, type Page } from '@playwright/test'

async function createOwnerMemorial(page: Page) {
  await page.goto('/login')
  await page.request.get('/api/v1/auth/csrf')
  const token = (await page.context().cookies()).find(
    (cookie) => cookie.name === 'XSRF-TOKEN',
  )?.value
  expect(token).toBeTruthy()
  const registered = await page.request.post('/api/v1/auth/register', {
    headers: { 'X-XSRF-TOKEN': decodeURIComponent(token!) },
    data: {
      email: `ritual-${Date.now()}@example.test`,
      password: 'ritual-space-password',
      displayName: '仪式验收家人',
    },
  })
  expect(registered.status()).toBe(201)
  await page.request.get('/api/v1/auth/csrf')
  const csrf = (await page.context().cookies()).find(
    (cookie) => cookie.name === 'XSRF-TOKEN',
  )?.value
  const created = await page.request.post('/api/v1/memorials', {
    headers: { 'X-XSRF-TOKEN': decodeURIComponent(csrf!) },
    data: {
      petName: '仪式小满',
      species: '小狗',
      visibility: 'PRIVATE',
      theme: 'NIGHT',
      version: 0,
    },
  })
  expect(created.status()).toBe(201)
  return (await created.json()).data as { id: string }
}

test('owner can complete a private ritual and organize archive calendar content', async ({
  page,
}) => {
  // eslint-disable-next-line playwright/no-skipped-test
  test.skip(process.env.E2E_FULL_STACK !== 'true', 'Requires the local Java API and database.')
  test.setTimeout(60_000)
  const memorial = await createOwnerMemorial(page)

  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto(`/editor/${memorial.id}/room`)
  await expect(page.getByRole('heading', { name: /仪式小满 的生活，不是一张功能清单/ })).toBeVisible()
  await expect(page.locator('.room-scene')).toBeVisible()
  await expect(page.getByText('它睡着后喜欢把头靠在哪里？')).toBeVisible()
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= document.documentElement.clientWidth,
    ),
  ).toBeTruthy()
  await page.screenshot({ path: '../docs/night-review/memorial-room-390.png', fullPage: true })
  await page.getByRole('button', { name: '列表模式' }).click()
  await expect(page.getByRole('navigation', { name: '纪念小屋内容列表' })).toBeVisible()
  await page.setViewportSize({ width: 768, height: 900 })
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= document.documentElement.clientWidth,
    ),
  ).toBeTruthy()
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.getByRole('button', { name: '场景' }).click()
  await expect(page.locator('.room-scene')).toBeVisible()
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= document.documentElement.clientWidth,
    ),
  ).toBeTruthy()
  await page.screenshot({ path: '../docs/night-review/memorial-room-1440.png', fullPage: true })

  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto(`/editor/${memorial.id}/ritual`)
  await expect(page.getByRole('heading', { name: /留一点时间/ })).toBeVisible()
  await page.getByRole('button', { name: '安静地进入' }).click()
  await page.getByRole('button', { name: '带着这段记忆继续' }).click()
  await page.getByRole('button', { name: '完成这次纪念' }).click()
  await expect(
    page.getByText('今天，我们又想起了 TA。仪式记录只保存在你的纪念空间里。'),
  ).toBeVisible()
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= document.documentElement.clientWidth,
    ),
  ).toBeTruthy()
  await page.screenshot({ path: '../docs/night-review/ritual-close-390.png', fullPage: true })

  await page.goto(`/editor/${memorial.id}/archive`)
  await page.getByLabel('给它一个称呼').fill('阳台最左边的垫子')
  await page.getByLabel('这段记忆的细节').fill('下午会在这里晒太阳。')
  await page.getByRole('button', { name: '放进生命档案' }).click()
  await expect(page.getByText('这段记忆已经放进生命档案。')).toBeVisible()
  await expect(
    page.getByLabel('生命档案概览').getByRole('heading', { name: '阳台最左边的垫子' }),
  ).toBeVisible()
  await expect(
    page.locator('.archive-entry').getByRole('heading', { name: '阳台最左边的垫子' }),
  ).toBeVisible()
  await page.screenshot({ path: '../docs/night-review/life-archive-390.png', fullPage: true })

  await page.goto(`/editor/${memorial.id}/calendar`)
  await page.getByLabel('给这一天的称呼').fill('第一次回家')
  await page.getByLabel('发生日期').fill('2023-05-20')
  await page.getByRole('button', { name: '收好这个日期' }).click()
  await expect(page.getByText('纪念日已收好，提醒仍保持关闭。')).toBeVisible()
  await page.getByLabel('写给未来的标题').fill('明年春天再读')
  await page.getByLabel('开启日期').fill('2030-05-20')
  await page.getByLabel('想留给未来的话').fill('希望你还记得窗边的阳光。')
  await page.getByRole('button', { name: '封存这封信' }).click()
  await expect(page.getByText('这封信已经交给未来的你。')).toBeVisible()
})
