package com.solarmonitor.provider.local;

import com.solarmonitor.provider.ProviderException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModbusRtuTest {

    @Test
    void crc16MatchesKnownVector() {
        // Vetor clássico: 01 03 00 00 00 0A → CRC 0xCDC5 (low C5, high CD no fio).
        byte[] frame = {0x01, 0x03, 0x00, 0x00, 0x00, 0x0A};
        assertThat(ModbusRtu.crc16(frame, frame.length)).isEqualTo(0xCDC5);
    }

    @Test
    void buildsReadRequestWithCrcLittleEndian() {
        byte[] request = ModbusRtu.buildReadRequest(500, 42);

        assertThat(request).hasSize(8);
        assertThat(request[0]).isEqualTo((byte) 0x01);           // slave
        assertThat(request[1]).isEqualTo((byte) 0x03);           // função
        assertThat(((request[2] & 0xFF) << 8) | (request[3] & 0xFF)).isEqualTo(500);
        assertThat(((request[4] & 0xFF) << 8) | (request[5] & 0xFF)).isEqualTo(42);
        int crc = ModbusRtu.crc16(request, 6);
        assertThat(request[6]).isEqualTo((byte) (crc & 0xFF));   // CRC low primeiro
        assertThat(request[7]).isEqualTo((byte) (crc >> 8));
    }

    @Test
    void parsesReadResponseRegisters() throws Exception {
        byte[] response = buildResponse(new int[]{1234, 0, 65535});

        int[] registers = ModbusRtu.parseReadResponse(response, 3);

        assertThat(registers).containsExactly(1234, 0, 65535);
    }

    @Test
    void rejectsCorruptedCrc() {
        byte[] response = buildResponse(new int[]{42});
        response[3] ^= 0x01; // corrompe um byte de dado sem atualizar o CRC

        assertThatThrownBy(() -> ModbusRtu.parseReadResponse(response, 1))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("CRC");
    }

    @Test
    void surfacesModbusExceptionCode() {
        // Resposta de exceção: função com bit 0x80 + código 0x02 (endereço ilegal).
        byte[] frame = {0x01, (byte) 0x83, 0x02, 0, 0};
        int crc = ModbusRtu.crc16(frame, 3);
        frame[3] = (byte) (crc & 0xFF);
        frame[4] = (byte) (crc >> 8);

        assertThatThrownBy(() -> ModbusRtu.parseReadResponse(frame, 1))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("exceção Modbus");
    }

    /** Resposta 0x03 válida com CRC correto para os registradores dados. */
    static byte[] buildResponse(int[] registers) {
        byte[] frame = new byte[3 + registers.length * 2 + 2];
        frame[0] = 0x01;
        frame[1] = 0x03;
        frame[2] = (byte) (registers.length * 2);
        for (int i = 0; i < registers.length; i++) {
            frame[3 + i * 2] = (byte) (registers[i] >> 8);
            frame[4 + i * 2] = (byte) registers[i];
        }
        int crc = ModbusRtu.crc16(frame, frame.length - 2);
        frame[frame.length - 2] = (byte) (crc & 0xFF);
        frame[frame.length - 1] = (byte) (crc >> 8);
        return frame;
    }
}
