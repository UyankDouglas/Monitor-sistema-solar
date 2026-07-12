export interface UserInfo {
  id: number
  username: string
  fullName: string
  email: string
  roles: string[]
  mustChangePassword: boolean
}

export interface AuthResponse {
  accessToken: string
  expiresInSeconds: number
  refreshToken: string
  user: UserInfo
}
