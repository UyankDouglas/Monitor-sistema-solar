import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { tokenStore } from '../auth/tokens'
import type { AuthResponse } from '../auth/types'

// baseURL vazio: em dev o Vite faz proxy de /api; em produção o Nginx faz.
export const api = axios.create({
  baseURL: '',
  timeout: 10_000,
})

/** Endpoints que não recebem bearer (públicos por natureza). */
const PUBLIC_PATHS = ['/api/auth/login', '/api/auth/refresh', '/api/auth/logout', '/api/ping']

/**
 * Endpoints cujo 401 é SEMÂNTICO (ex.: senha atual incorreta no
 * change-password) — nunca devem disparar refresh + reenvio silencioso.
 */
const NO_REFRESH_PATHS = [...PUBLIC_PATHS, '/api/auth/change-password']

api.interceptors.request.use(config => {
  const access = tokenStore.getAccess()
  if (access && !PUBLIC_PATHS.some(p => config.url?.startsWith(p))) {
    config.headers.Authorization = `Bearer ${access}`
  }
  return config
})

// Refresh "single-flight" em DUAS camadas:
//  - intra-aba: promise compartilhada (N requisições com 401 → 1 refresh);
//  - entre abas: Web Locks API — sem o lock, duas abas rotacionariam o mesmo
//    refresh token e a segunda seria tratada como REUSO pelo backend,
//    revogando TODAS as sessões do usuário.
let refreshing: Promise<string> | null = null

async function refreshAccessToken(): Promise<string> {
  const refreshAtRequest = tokenStore.getRefresh()
  if (!refreshAtRequest) throw new Error('sem refresh token')

  const run = async (): Promise<string> => {
    const current = tokenStore.getRefresh()
    if (!current) throw new Error('sem refresh token')
    if (current !== refreshAtRequest) {
      // Outra aba rotacionou enquanto aguardávamos o lock: reusa o resultado.
      const access = tokenStore.getAccess()
      if (access) return access
    }
    // axios "cru" (sem interceptors) para não recursionar
    const { data } = await axios.post<AuthResponse>(
      '/api/auth/refresh',
      { refreshToken: current },
      { timeout: 10_000 },
    )
    tokenStore.set(data.accessToken, data.refreshToken)
    return data.accessToken
  }

  if (typeof navigator !== 'undefined' && 'locks' in navigator) {
    // await achata o Promise aninhado da tipagem do Web Locks
    return await navigator.locks.request('solar.token-refresh', run)
  }
  return run()
}

api.interceptors.response.use(
  response => response,
  async (error: AxiosError) => {
    const config = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined
    const status = error.response?.status
    const skipRefresh = NO_REFRESH_PATHS.some(p => config?.url?.startsWith(p))

    if (status === 401 && config && !config._retried && !skipRefresh && tokenStore.getRefresh()) {
      config._retried = true
      try {
        refreshing = refreshing ?? refreshAccessToken()
        const newAccess = await refreshing
        config.headers.Authorization = `Bearer ${newAccess}`
        return api.request(config)
      } catch (refreshError) {
        // Só descarta a sessão quando o SERVIDOR rejeitou o refresh token;
        // falha de rede/5xx mantém os tokens — o próximo ciclo tenta de novo.
        const refreshStatus = axios.isAxiosError(refreshError)
          ? refreshError.response?.status
          : undefined
        if (refreshStatus === 401 || refreshStatus === 403) {
          tokenStore.clear()
          window.location.assign('/login')
        }
        return Promise.reject(error)
      } finally {
        refreshing = null
      }
    }
    return Promise.reject(error)
  },
)
