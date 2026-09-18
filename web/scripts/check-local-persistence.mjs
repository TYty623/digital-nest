// Read-only check of the explicitly named local test account. Do not use production credentials.
import { request } from '@playwright/test'
const email = process.env.PERSISTENCE_TEST_EMAIL
const password = process.env.PERSISTENCE_TEST_PASSWORD
if (!email || !password) throw new Error('Set PERSISTENCE_TEST_EMAIL and PERSISTENCE_TEST_PASSWORD')
const api = await request.newContext({ baseURL: 'http://127.0.0.1:5173' })
try {
  await api.get('/api/v1/auth/csrf')
  const state = await api.storageState()
  const token = state.cookies.find(cookie => cookie.name === 'XSRF-TOKEN')?.value
  const login = await api.post('/api/v1/auth/login', { headers: { 'X-XSRF-TOKEN': decodeURIComponent(token) }, data: { email, password } })
  if (!login.ok()) throw new Error(`Test account login failed: ${login.status()}`)
  const orders = await api.get('/api/v1/billing/orders')
  if (!orders.ok()) throw new Error(`Orders unavailable: ${orders.status()}`)
  const ids = (await orders.json()).data.map(order => order.id).sort()
  console.log(JSON.stringify({ login: 'ok', orderIds: ids }))
} finally { await api.dispose() }
