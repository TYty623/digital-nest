import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api, uploadWithProgress } from '../client'

class FakeUpload extends EventTarget {
  static latest: FakeUpload
  upload = new EventTarget()
  status = 200
  responseText = ''
  timeout = 0
  withCredentials = false
  constructor() {
    super()
    FakeUpload.latest = this
  }
  open() {}
  send() {}
  setRequestHeader() {}
}

beforeEach(() => {
  FakeUpload.latest = undefined!
  vi.stubGlobal('XMLHttpRequest', FakeUpload)
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => null }),
  )
})
afterEach(() => vi.unstubAllGlobals())

describe('API response and upload failures', () => {
  it('rejects malformed successful JSON instead of returning undefined', async () => {
    await expect(api('/test')).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
  })
  it.each(['timeout', 'abort', 'error', 'load'])(
    'settles the promise when an upload receives %s',
    async (event) => {
      const result = uploadWithProgress('/upload', new FormData())
      await vi.waitFor(() => {
        if (!FakeUpload.latest) throw new Error('上传请求尚未创建')
      })
      FakeUpload.latest.dispatchEvent(new Event(event))
      await expect(result).rejects.toBeInstanceOf(Error)
    },
  )
  it('returns upload data and reports real progress', async () => {
    const progress = vi.fn<(percent: number, total: number) => void>()
    const result = uploadWithProgress('/upload', new FormData(), progress)
    await Promise.resolve()
    await Promise.resolve()
    FakeUpload.latest.upload.dispatchEvent(
      new ProgressEvent('progress', { lengthComputable: true, loaded: 30, total: 100 }),
    )
    FakeUpload.latest.responseText = JSON.stringify({ data: { id: 'image' } })
    FakeUpload.latest.dispatchEvent(new Event('load'))
    await expect(result).resolves.toEqual({ id: 'image' })
    expect(progress).toHaveBeenCalledWith(30, 100)
  })
})
