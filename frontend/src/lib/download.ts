import { api } from '../api/client'

/**
 * Baixa um arquivo autenticado (o bearer vai pelo interceptor) e dispara o
 * "Salvar como" do browser preservando o nome vindo do Content-Disposition.
 */
export async function downloadFile(url: string, params: Record<string, string>): Promise<void> {
  const response = await api.get(url, { params, responseType: 'blob', timeout: 60_000 })

  const disposition = String(response.headers['content-disposition'] ?? '')
  const match = /filename="?([^";]+)"?/.exec(disposition)
  const filename = match?.[1] ?? 'export'

  const blobUrl = URL.createObjectURL(response.data as Blob)
  try {
    const anchor = document.createElement('a')
    anchor.href = blobUrl
    anchor.download = filename
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
  } finally {
    // Delay: revogar imediatamente cancela o download em alguns browsers.
    setTimeout(() => URL.revokeObjectURL(blobUrl), 10_000)
  }
}
