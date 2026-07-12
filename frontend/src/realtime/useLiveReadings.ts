import { useEffect, useRef, useState } from 'react'
import { Client } from '@stomp/stompjs'
import { tokenStore } from '../auth/tokens'
import type { ReadingEvent } from './types'

/**
 * Conexão STOMP com o backend (tópico /topic/readings).
 *
 * <p>O handshake exige `?token=<accessToken>` e o token expira em 15 min —
 * por isso a URL é recalculada em `beforeConnect` a cada (re)tentativa,
 * pegando sempre o token corrente do storage (o interceptor HTTP o renova
 * no polling normal da página). Reconexão automática a cada 5 s.</p>
 */
export function useLiveReadings(): { connected: boolean; latest: ReadingEvent | null } {
  const [connected, setConnected] = useState(false)
  const [latest, setLatest] = useState<ReadingEvent | null>(null)
  const clientRef = useRef<Client | null>(null)

  useEffect(() => {
    const client = new Client({
      reconnectDelay: 5_000,
      beforeConnect: () => {
        const token = tokenStore.getAccess() ?? ''
        const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws'
        client.brokerURL =
          `${scheme}://${window.location.host}/ws?token=${encodeURIComponent(token)}`
      },
      onConnect: () => {
        setConnected(true)
        client.subscribe('/topic/readings', message => {
          try {
            setLatest(JSON.parse(message.body) as ReadingEvent)
          } catch {
            // frame malformado não derruba a conexão
          }
        })
      },
      onWebSocketClose: () => {
        setConnected(false)
        // Sem limpar, uma leitura antiga bloquearia o fallback de polling
        // nos cards para sempre (?? nunca alcançaria o dashboard).
        setLatest(null)
      },
      onStompError: () => {
        setConnected(false)
        setLatest(null)
      },
    })
    clientRef.current = client
    client.activate()

    return () => {
      clientRef.current = null
      void client.deactivate()
    }
  }, [])

  return { connected, latest }
}
