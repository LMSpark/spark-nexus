export const API_BASE = import.meta.env.VITE_API_BASE || ''

type LoginResult = {
  token: string
  username: string
  displayName: string
  role: string
  communityId: number
  tenantId: number
  capabilities?: {
    role: string
    tenantId: number
    navKeys: string[]
    capabilities: Array<{
      dataScope: string
      permissionCode: string
      communityCount: number
    }>
  }
  tenants: Array<{
    tenantId: number
    tenantName: string
    tenantType: string
    roleCode: string
    isDefault: number
  }>
}

export const session = {
  token: localStorage.getItem('dsyg_token') || '',
  user: JSON.parse(localStorage.getItem('dsyg_user') || 'null') as LoginResult | null,
  set(result: LoginResult) {
    this.token = result.token
    this.user = result
    localStorage.setItem('dsyg_token', result.token)
    localStorage.setItem('dsyg_user', JSON.stringify(result))
  },
  clear() {
    this.token = ''
    this.user = null
    localStorage.removeItem('dsyg_token')
    localStorage.removeItem('dsyg_user')
  }
}

export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(session.token ? { Authorization: `Bearer ${session.token}` } : {}),
      ...(options.headers || {})
    }
  })
  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: '请求失败' }))
    throw new Error(error.message || '请求失败')
  }
  return response.json()
}

export function login(username: string, password: string) {
  return api<LoginResult>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password })
  })
}

export function switchTenant(tenantId: number) {
  return api<LoginResult>('/api/auth/switch-tenant', {
    method: 'POST',
    body: JSON.stringify({ tenantId })
  })
}

export function capabilities() {
  return api<NonNullable<LoginResult['capabilities']>>('/api/auth/capabilities')
}

export async function apiBlob(path: string, options: RequestInit = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      ...(session.token ? { Authorization: `Bearer ${session.token}` } : {}),
      ...(options.headers || {})
    }
  })
  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: '请求失败' }))
    throw new Error(error.message || '请求失败')
  }
  return response.blob()
}
