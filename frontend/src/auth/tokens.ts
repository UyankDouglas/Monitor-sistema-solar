// Armazenamento dos tokens. localStorage: sobrevive a refresh/reabertura —
// trade-off consciente para app pessoal (XSS é mitigado por não haver
// conteúdo de terceiros; ver docs/architecture.md).

const ACCESS_KEY = 'solar.accessToken'
const REFRESH_KEY = 'solar.refreshToken'

export const tokenStore = {
  getAccess: () => localStorage.getItem(ACCESS_KEY),
  getRefresh: () => localStorage.getItem(REFRESH_KEY),
  set(access: string, refresh: string) {
    localStorage.setItem(ACCESS_KEY, access)
    localStorage.setItem(REFRESH_KEY, refresh)
  },
  clear() {
    localStorage.removeItem(ACCESS_KEY)
    localStorage.removeItem(REFRESH_KEY)
  },
}
