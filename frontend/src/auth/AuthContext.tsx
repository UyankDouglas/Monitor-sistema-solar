import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api } from '../api/client'
import { tokenStore } from './tokens'
import type { AuthResponse, UserInfo } from './types'

interface AuthContextValue {
  user: UserInfo | null
  /** true enquanto restauramos a sessão persistida no primeiro load */
  initializing: boolean
  login: (username: string, password: string) => Promise<UserInfo>
  logout: () => Promise<void>
  changePassword: (currentPassword: string, newPassword: string) => Promise<UserInfo>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserInfo | null>(null)
  const [initializing, setInitializing] = useState(true)

  // Restaura a sessão: com tokens salvos, /api/auth/me valida e devolve o
  // usuário (o interceptor renova o access token se preciso).
  useEffect(() => {
    if (!tokenStore.getAccess()) {
      setInitializing(false)
      return
    }
    api.get<UserInfo>('/api/auth/me')
      .then(({ data }) => setUser(data))
      .catch(() => tokenStore.clear())
      .finally(() => setInitializing(false))
  }, [])

  const login = useCallback(async (username: string, password: string) => {
    const { data } = await api.post<AuthResponse>('/api/auth/login', { username, password })
    tokenStore.set(data.accessToken, data.refreshToken)
    setUser(data.user)
    return data.user
  }, [])

  const logout = useCallback(async () => {
    const refresh = tokenStore.getRefresh()
    if (refresh) {
      // Revogação é cortesia: falha de rede não impede o logout local.
      await api.post('/api/auth/logout', { refreshToken: refresh }).catch(() => undefined)
    }
    tokenStore.clear()
    setUser(null)
  }, [])

  const changePassword = useCallback(async (currentPassword: string, newPassword: string) => {
    const { data } = await api.post<AuthResponse>('/api/auth/change-password', {
      currentPassword,
      newPassword,
    })
    tokenStore.set(data.accessToken, data.refreshToken)
    setUser(data.user)
    return data.user
  }, [])

  const value = useMemo(
    () => ({ user, initializing, login, logout, changePassword }),
    [user, initializing, login, logout, changePassword],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth precisa estar dentro de <AuthProvider>')
  return ctx
}
