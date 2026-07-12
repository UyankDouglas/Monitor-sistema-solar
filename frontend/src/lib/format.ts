/** Formatação pt-BR compartilhada. */

const nf0 = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 0 })
const nf1 = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 })
const nf2 = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

export function fmtW(value: number | null | undefined): string {
  if (value == null) return '—'
  return Math.abs(value) >= 10_000
    ? `${nf1.format(value / 1000)} kW`
    : `${nf0.format(value)} W`
}

export function fmtKwh(value: number | null | undefined): string {
  return value == null ? '—' : `${nf1.format(value)} kWh`
}

export function fmtMoney(value: number | null | undefined, currency = 'BRL'): string {
  if (value == null) return '—'
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency }).format(value)
}

export function fmtPct(value: number | null | undefined): string {
  return value == null ? '—' : `${nf1.format(value)}%`
}

export function fmtKg(value: number | null | undefined): string {
  if (value == null) return '—'
  return value >= 1000 ? `${nf2.format(value / 1000)} t` : `${nf1.format(value)} kg`
}

export function fmtDate(iso: string): string {
  const [y, m, d] = iso.split('-')
  return `${d}/${m}/${y}`
}

export function fmtDateTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('pt-BR', {
    day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit',
  })
}

/** Date → 'YYYY-MM-DD' no fuso local (input type=date). */
export function toIsoDate(date: Date): string {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}
