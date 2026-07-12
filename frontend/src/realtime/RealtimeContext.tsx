import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { Client } from '@stomp/stompjs'
import { tokenStore } from '../auth/tokens'
import type { ReadingEvent } from './types'

/** Evento de alerta publicado pelo backend em /topic/alerts. */
export interface AlertEvent {
  action: 'CREATED' | 'RESOLVED'
  alertId: number
  inverterId: number
  type: string
  severity: 'INFO' | 'WARNING' | 'CRITICAL'
  status: string
  message: string
  triggeredAt: string
}

interface RealtimeValue {
  connected: boolean
  latest: ReadingEvent | null
  lastAlert: AlertEvent | null
}

const RealtimeContext = createContext<RealtimeValue | null>(null)

/**
 * Conexão STOMP única da aplicação (tópicos de leituras e de alertas),
 * montada dentro da área autenticada. URL recalculada em beforeConnect
 * (token de 15 min sempre corrente); reconexão a cada 5 s; heartbeats
 * 10s/10s negociados com o broker detectam conexões mortas.
 */
export function RealtimeProvider({ children }: { children: ReactNode }) {
  const [connected, setConnected] = useState(false)
  const [latest, setLatest] = useState<ReadingEvent | null>(null)
  const [lastAlert, setLastAlert] = useState<AlertEvent | null>(null)

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
          } catch { /* frame malformado não derruba a conexão */ }
        })
        client.subscribe('/topic/alerts', message => {
          try {
            setLastAlert(JSON.parse(message.body) as AlertEvent)
          } catch { /* idem */ }
        })
      },
      onWebSocketClose: () => {
        setConnected(false)
        // Sem limpar, uma leitura antiga bloquearia o fallback de polling.
        setLatest(null)
      },
      onStompError: () => {
        setConnected(false)
        setLatest(null)
      },
    })
    client.activate()
    return () => {
      void client.deactivate()
    }
  }, [])

  const value = useMemo(() => ({ connected, latest, lastAlert }),
    [connected, latest, lastAlert])

  return <RealtimeContext.Provider value={value}>{children}</RealtimeContext.Provider>
}

export function useRealtime(): RealtimeValue {
  const ctx = useContext(RealtimeContext)
  if (!ctx) throw new Error('useRealtime precisa estar dentro de <RealtimeProvider>')
  return ctx
}
