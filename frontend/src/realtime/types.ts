/** Leitura publicada pelo backend em /topic/readings (record EnergyReading). */
export interface LiveReading {
  sampledAt: string
  status: string
  acPowerW: number | null
  loadPowerW: number | null
  exportPowerW: number | null
  importPowerW: number | null
  batteryPowerW: number | null
  dailyEnergyKwh: number | null
  totalEnergyKwh: number | null
  batterySocPct: number | null
  batteryTemperatureC: number | null
  inverterTemperatureC: number | null
  mpptReadings: Array<{
    stringIndex: number
    voltage: number | null
    currentA: number | null
    powerW: number | null
  }> | null
}

export interface ReadingEvent {
  inverterId: number
  reading: LiveReading
}
