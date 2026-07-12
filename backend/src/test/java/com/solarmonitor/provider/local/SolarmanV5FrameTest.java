package com.solarmonitor.provider.local;

import com.solarmonitor.provider.ProviderException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SolarmanV5FrameTest {

    @Test
    void wrapsRequestWithValidStructureAndChecksum() {
        byte[] modbus = ModbusRtu.buildReadRequest(500, 42);

        byte[] frame = SolarmanV5Frame.wrapRequest(2712345678L, 7, modbus);

        assertThat(frame[0]).isEqualTo(SolarmanV5Frame.START);
        assertThat(frame[frame.length - 1]).isEqualTo(SolarmanV5Frame.END);
        // controle 0x4510 em little-endian
        assertThat(frame[3] & 0xFF).isEqualTo(0x10);
        assertThat(frame[4] & 0xFF).isEqualTo(0x45);
        // length = payload (15 fixos + modbus)
        int declared = (frame[1] & 0xFF) | ((frame[2] & 0xFF) << 8);
        assertThat(declared).isEqualTo(15 + modbus.length);
        // checksum confere
        int checksum = SolarmanV5Frame.checksum(frame, 1, frame.length - 2);
        assertThat(frame[frame.length - 2] & 0xFF).isEqualTo(checksum);
        // serial do logger em little-endian
        long serial = (frame[7] & 0xFFL) | ((frame[8] & 0xFFL) << 8)
                | ((frame[9] & 0xFFL) << 16) | ((frame[10] & 0xFFL) << 24);
        assertThat(serial).isEqualTo(2712345678L);
    }

    @Test
    void unwrapsResponseToInnerModbusFrame() throws Exception {
        byte[] modbus = ModbusRtuTest.buildResponse(new int[]{1234, 5678});
        byte[] frame = buildResponseFrame(modbus);

        byte[] unwrapped = SolarmanV5Frame.unwrapResponse(frame);

        assertThat(unwrapped).isEqualTo(modbus);
    }

    @Test
    void rejectsCorruptedChecksum() {
        byte[] frame = buildResponseFrame(ModbusRtuTest.buildResponse(new int[]{1}));
        frame[frame.length - 2] ^= 0x5A;

        assertThatThrownBy(() -> SolarmanV5Frame.unwrapResponse(frame))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("Checksum");
    }

    @Test
    void rejectsRequestControlCodeOnResponsePath() {
        byte[] modbus = ModbusRtuTest.buildResponse(new int[]{1});
        // Um frame de REQUISIÇÃO não pode passar por resposta.
        byte[] frame = SolarmanV5Frame.wrapRequest(1L, 1, modbus);

        assertThatThrownBy(() -> SolarmanV5Frame.unwrapResponse(frame))
                .isInstanceOf(ProviderException.class);
    }

    /** Monta um frame V5 de resposta (0x1510) válido em torno do Modbus dado. */
    static byte[] buildResponseFrame(byte[] modbus) {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write(0x02);                 // frame type
        payload.write(0x01);                 // status
        for (int i = 0; i < 12; i++) {       // total/power-on/offset time
            payload.write(0x00);
        }
        payload.writeBytes(modbus);
        byte[] body = payload.toByteArray();

        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(SolarmanV5Frame.START);
        frame.write(body.length & 0xFF);
        frame.write((body.length >> 8) & 0xFF);
        frame.write(0x10);                   // controle 0x1510 LE
        frame.write(0x15);
        frame.write(0x01);                   // sequência
        frame.write(0x00);
        for (int i = 0; i < 4; i++) {        // serial do logger
            frame.write(0x11);
        }
        frame.writeBytes(body);
        byte[] soFar = frame.toByteArray();
        frame.write(SolarmanV5Frame.checksum(soFar, 1, soFar.length));
        frame.write(SolarmanV5Frame.END);
        return frame.toByteArray();
    }
}
