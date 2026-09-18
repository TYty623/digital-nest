export type ApiResponse<T> = {
  data: T
  requestId: string
}

export type UploadProgressHandler = (loadedBytes: number, totalBytes: number) => void

type ApiError = {
  code: string
  message: string
}

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? '/api/v1'
let csrfReady = false

function readCookie(name: string) {
  return document.cookie
    .split('; ')
    .find((item) => item.startsWith(`${name}=`))
    ?.slice(name.length + 1)
}

async function ensureCsrf() {
  if (csrfReady && readCookie('XSRF-TOKEN')) return

  const response = await fetch(`${apiBaseUrl}/auth/csrf`, { credentials: 'include' })
  if (!response.ok) throw new Error('暂时无法建立安全连接，请稍后重试。')
  csrfReady = true
}

export async function api<T>(path: string, init: RequestInit = {}) {
  const method = (init.method ?? 'GET').toUpperCase()
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) await ensureCsrf()

  const headers = new Headers(init.headers)
  const csrfToken = readCookie('XSRF-TOKEN')
  if (csrfToken) headers.set('X-XSRF-TOKEN', decodeURIComponent(csrfToken))
  if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...init,
    headers,
    credentials: 'include',
  })

  const payload = (await response.json().catch(() => null)) as ApiResponse<T | ApiError> | null
  if (!response.ok) {
    const error = payload?.data as ApiError | undefined
    throw new ApiRequestError(
      error?.message ?? '请求没有完成，请稍后再试。',
      response.status,
      error?.code,
    )
  }
  if (!payload || !Object.prototype.hasOwnProperty.call(payload, 'data')) {
    throw new ApiRequestError(
      '服务返回了无法识别的数据，请稍后重试。',
      response.status,
      'INVALID_RESPONSE',
    )
  }
  return payload.data as T
}

/**
 * Multipart uploads need XHR so the editor can show the browser's real upload
 * progress. Other API calls continue to use fetch through `api` above.
 */
export async function uploadWithProgress<T>(
  path: string,
  form: FormData,
  onProgress?: UploadProgressHandler,
) {
  await ensureCsrf()
  const csrfToken = readCookie('XSRF-TOKEN')

  return new Promise<T>((resolve, reject) => {
    const request = new XMLHttpRequest()
    request.open('POST', `${apiBaseUrl}${path}`)
    request.withCredentials = true
    request.timeout = 120_000
    if (csrfToken) request.setRequestHeader('X-XSRF-TOKEN', decodeURIComponent(csrfToken))

    request.upload.addEventListener('progress', (event) => {
      if (event.lengthComputable) onProgress?.(event.loaded, event.total)
    })
    request.addEventListener('error', () => {
      reject(new ApiRequestError('上传连接中断，请检查网络后重试。', 0, 'UPLOAD_NETWORK_ERROR'))
    })
    request.addEventListener('timeout', () =>
      reject(new ApiRequestError('上传超时，请检查网络后重试。', 0, 'UPLOAD_TIMEOUT')),
    )
    request.addEventListener('abort', () =>
      reject(new ApiRequestError('上传已取消，可以重新选择文件。', 0, 'UPLOAD_ABORTED')),
    )
    request.addEventListener('load', () => {
      const payload = (() => {
        try {
          return JSON.parse(request.responseText) as ApiResponse<T | ApiError>
        } catch {
          return null
        }
      })()
      if (request.status < 200 || request.status >= 300) {
        const error = payload?.data as ApiError | undefined
        reject(
          new ApiRequestError(
            error?.message ?? '上传没有完成，请稍后重试。',
            request.status,
            error?.code,
          ),
        )
        return
      }
      if (!payload || !Object.prototype.hasOwnProperty.call(payload, 'data')) {
        reject(
          new ApiRequestError('上传响应异常，请稍后重试。', request.status, 'INVALID_RESPONSE'),
        )
        return
      }
      resolve(payload.data as T)
    })
    request.send(form)
  })
}

export class ApiRequestError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly code?: string,
  ) {
    super(message)
  }
}
