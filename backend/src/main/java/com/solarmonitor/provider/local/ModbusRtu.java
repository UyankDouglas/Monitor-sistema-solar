package com.solarmonitor.provider.local;

import com.solarmonitor.provider.ProviderException;

/**
 * Codificação/decodificação Modbus RTU mínima para o que o projeto precisa:
 * função 0x03 (Read Holding Registers) com CRC-16/MODBUS (poly 0xA001,
 * init 0xFFFF, CRC anexado em little-endian).
 */
final class ModbusRtu {

    static final byte SLAVE_ADDRESS = 0x01;
    static final byte FUNCTION_READ_HOLDING = 0x03;

    private ModbusRtu() {
    }

    /** Monta o frame de leitura de {@code count} registradores a partir de {@code startRegister}. */
    static byte[] buildReadRequest(int startRegister, int count) {
        byte[] frame = new byte[8];
        frame[0] = SLAVE_ADDRESS;
        frame[1] = FUNCTION_READ_HOLDING;
        frame[2] = (byte) (startRegister >> 8);
        frame[3] = (byte) startRegister;
        frame[4] = (byte) (count >> 8);
        frame[5] = (byte) count;
        int crc = crc16(frame, 6);
        frame[6] = (byte) crc;          // CRC low primeiro (little-endian)
        frame[7] = (byte) (crc >> 8);
        return frame;
    }

    /**
     * Valida CRC/estrutura da resposta e devolve os registradores como ints
     * sem sinal (0..65535), na ordem lida.
     */
    static int[] parseReadResponse(byte[] frame, int expectedCount) throws ProviderException {
        if (frame == null || frame.length < 5) {
            throw new ProviderException("Resposta Modbus curta demais: "
                    + (frame == null ? 0 : frame.length) + " bytes");
        }
        int crcReceived = ((frame[frame.length - 1] & 0xFF) << 8) | (frame[frame.length - 2] & 0xFF);
        int crcComputed = crc16(frame, frame.length - 2);
        if (crcReceived != crcComputed) {
            throw new ProviderException(String.format(
                    "CRC Modbus inválido: recebido 0x%04X, calculado 0x%04X", crcReceived, crcComputed));
        }
        int function = frame[1] & 0xFF;
        if ((function & 0x80) != 0) {
            throw new ProviderException("Inversor devolveu exceção Modbus, código " + (frame[2] & 0xFF));
        }
        if (function != FUNCTION_READ_HOLDING) {
            throw new ProviderException("Função Modbus inesperada na resposta: 0x" + Integer.toHexString(function));
        }
        int byteCount = frame[2] & 0xFF;
        if (byteCount != expectedCount * 2 || frame.length != 3 + byteCount + 2) {
            throw new ProviderException("Tamanho Modbus inconsistente: byteCount=" + byteCount
                    + ", esperado " + expectedCount * 2 + " bytes de dados");
        }
        int[] registers = new int[expectedCount];
        for (int i = 0; i < expectedCount; i++) {
            registers[i] = ((frame[3 + i * 2] & 0xFF) << 8) | (frame[4 + i * 2] & 0xFF);
        }
        return registers;
    }

    /** CRC-16/MODBUS sobre os primeiros {@code length} bytes. */
    static int crc16(byte[] data, int length) {
        int crc = 0xFFFF;
        for (int i = 0; i < length; i++) {
            crc ^= data[i] & 0xFF;
            for (int bit = 0; bit < 8; bit++) {
                if ((crc & 0x01) != 0) {
                    crc = (crc >> 1) ^ 0xA001;
                } else {
                    crc >>= 1;
                }
            }
        }
        return crc;
    }
}
