import axios from 'axios'

// baseURL vazio: em dev o Vite faz proxy de /api; em produção o Nginx faz.
// Na Etapa 6 adicionamos interceptors para JWT + refresh transparente.
export const api = axios.create({
  baseURL: '',
  timeout: 10_000,
})
