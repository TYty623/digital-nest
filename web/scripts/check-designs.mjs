import { chromium } from '@playwright/test'
import { mkdir } from 'node:fs/promises'
const browser = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })
const page = await browser.newPage()
const errors = []
page.on('pageerror', error => errors.push(error.message))
const output = new URL('../../docs/design-previews/', import.meta.url).pathname.replace(/^\/(\w:)/, '$1')
await mkdir(output, { recursive: true })
try {
  for (const variant of ['index', 'a', 'b', 'c']) {
    for (const width of [390, 1440]) {
      await page.setViewportSize({ width, height: 960 })
      const response = await page.goto('http://127.0.0.1:5173/designs/' + variant + '.html')
      if (response.status() !== 200) throw new Error(variant + ': HTTP ' + response.status())
      await page.locator('h1').waitFor()
      const layout = await page.evaluate(() => ({
        overflow: document.documentElement.scrollWidth > innerWidth,
        brokenImages: [...document.images].filter(img => img.getAttribute('src') && (!img.complete || !img.naturalWidth)).map(img => img.src),
      }))
      if (layout.overflow || layout.brokenImages.length) throw new Error(variant + ' ' + width + ': ' + JSON.stringify(layout))
      await page.screenshot({ path: output + variant + '-' + width + '.png', fullPage: true })
      console.log(variant + ' ' + width + ': layout and images OK')
    }
    if (variant !== 'index') {
      await page.locator('[data-create]').first().click()
      await page.getByLabel('TA 的昵称').fill('预览小麦')
      await page.getByRole('button', { name: '看看 TA 的小窝' }).click()
      if (await page.locator('.demo-result h3').textContent() !== '预览小麦') throw new Error('Preview form failed')
      await page.keyboard.press('Escape')
      await page.locator('[data-light]').click()
      if (await page.locator('[data-light]').getAttribute('aria-pressed') !== 'true') throw new Error('Light failed')
      await page.locator('summary').first().click()
      if (!(await page.locator('details').first().getAttribute('open') === '')) throw new Error('FAQ failed')
      console.log(variant + ': dialog, light and FAQ OK')
    }
  }
  await page.goto('http://127.0.0.1:5173/designs/index.html')
  await page.locator('[data-pick]').first().click()
  await page.reload()
  if (!((await page.locator('[role=status]').textContent()).includes('A · 暖纸相册'))) throw new Error('Choice persistence failed')
  if (errors.length) throw new Error(errors.join('\n'))
  console.log('PASS: 8 viewports, 3 interaction flows, saved choice, no page errors.')
} finally {
  await browser.close()
}
