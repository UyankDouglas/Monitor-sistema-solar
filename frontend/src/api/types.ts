/** DTOs da API — espelham os records do backend. */

export interface Dashboard {
  inverterId: number
  inverterName: string
  inverterStatus: string
  lastSeenAt: string | null
  sampledAt: string | null
  currentPowerW: number | null
  loadPowerW: number | null
  exportPowerW: number | null
  importPowerW: number | null
  batteryPowerW: number | null
  batterySocPct: number | null
  batteryVoltage: number | null
  batteryTemperatureC: number | null
  gridVoltageL1: number | null
  gridVoltageL2: number | null
  gridVoltageL3: number | null
  gridFrequencyHz: number | null
  inverterTemperatureC: number | null
  todayEnergyKwh: number | null
  monthEnergyKwh: number | null
  totalEnergyKwh: number | null
  currency: string
  todaySavings: number | null
  monthSavings: number | null
  totalSavingsEstimate: number | null
  totalCo2AvoidedKgEstimate: number | null
  activeAlerts: number
}

export interface DailyGeneration {
  date: string
  energyKwh: number | null
  peakPowerW: number | null
  peakAt: string | null
  minPowerW: number | null
  consumptionKwh: number | null
  exportKwh: number | null
  importKwh: number | null
  selfConsumptionKwh: number | null
  selfSufficiencyPct: number | null
  savings: number | null
  co2AvoidedKg: number | null
}

export interface MonthlyGeneration {
  year: number
  month: number
  energyKwh: number | null
  consumptionKwh: number | null
  exportKwh: number | null
  importKwh: number | null
  savings: number | null
  co2AvoidedKg: number | null
}

export interface CurrentEnergy {
  inverterId: number
  inverterName: string
  inverterStatus: string
  sampledAt: string
  acPowerW: number | null
  loadPowerW: number | null
  exportPowerW: number | null
  importPowerW: number | null
  batteryPowerW: number | null
  batterySocPct: number | null
  dailyEnergyKwh: number | null
  totalEnergyKwh: number | null
  inverterTemperatureC: number | null
  mppt: Array<{
    stringIndex: number
    voltage: number | null
    currentA: number | null
    powerW: number | null
  }>
}

export interface Statistics {
  from: string
  to: string
  daysWithData: number
  totalEnergyKwh: number | null
  totalSavings: number | null
  totalCo2AvoidedKg: number | null
  bestDay: { date: string; energyKwh: number } | null
  worstDay: { date: string; energyKwh: number } | null
  maxPeak: { powerW: number; date: string; at: string | null } | null
  minPeak: { powerW: number; date: string; at: string | null } | null
  avgDailyKwh: number | null
  avgMonthlyKwh: number | null
  kwhPerKwp: number | null
  capacityFactorPct: number | null
}

export interface Alert {
  id: number
  inverterId: number
  type: string
  severity: 'INFO' | 'WARNING' | 'CRITICAL'
  status: 'ACTIVE' | 'ACKNOWLEDGED' | 'RESOLVED'
  message: string
  details: Record<string, unknown> | null
  triggeredAt: string
  acknowledgedAt: string | null
  resolvedAt: string | null
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface Configuration {
  key: string
  value: string
  valueType: string
  secret: boolean
  updatedAt: string | null
}

export interface EnergySeries {
  inverterId: number
  from: string
  to: string
  bucketSeconds: number
  points: Array<{
    timestamp: string
    acPowerW: number | null
    loadPowerW: number | null
    batterySocPct: number | null
  }>
}
