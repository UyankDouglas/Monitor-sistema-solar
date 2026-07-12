package com.solarmonitor.provider.local;

import com.solarmonitor.provider.ProviderException;

import java.io.ByteArrayOutputStream;

/**
 * Framing do protocolo proprietário Solarman V5, usado pelos data loggers
 * Wi-Fi (porta TCP 8899) para encapsular frames Modbus RTU.
 *
 * <p>Estrutura (multi-byte em little-endian, salvo o Modbus interno):</p>
 * <pre>
 * A5 | len(2) | control(2) | sequence(2) | loggerSerial(4) | payload(len) | checksum(1) | 15
 * </pre>
 * <ul>
 *   <li>control: 0x4510 (requisição) / 0x1510 (resposta)</li>
 *   <li>payload da requisição: frameType(1)=0x02, sensorType(2)=0, três campos
 *       de tempo(4+4+4)=0, e o frame Modbus RTU</li>
 *   <li>payload da resposta: frameType(1), status(1), três campos de tempo,
 *       e o frame Modbus RTU a partir do offset 14</li>
 *   <li>checksum: soma de todos os bytes entre o A5 (exclusivo) e o próprio
 *       checksum (exclusivo), &amp; 0xFF</li>
 * </ul>
 */
final class SolarmanV5Frame {

    static final byte START = (byte) 0xA5;
    static final byte END = 0x15;
    static final int CONTROL_REQUEST = 0x4510;
    static final int CONTROL_RESPONSE = 0x1510;
    /** Offset do frame Modbus dentro do payload de resposta. */
    private static final int RESPONSE_MODBUS_OFFSET = 14;

    private SolarmanV5Frame() {
    }

    /** Encapsula um frame Modbus RTU numa requisição V5 para o logger informado. */
    static byte[] wrapRequest(long loggerSerial, int sequence, byte[] modbusFrame) {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write(0x02);                       // frame type: solicitação de dados
        writeLe(payload, 0, 2);                    // sensor type
        writeLe(payload, 0, 4);                    // total working time
        writeLe(payload, 0, 4);                    // power-on time
        writeLe(payload, 0, 4);                    // offset time
        payload.writeBytes(modbusFrame);
        byte[] body = payload.toByteArray();

        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(START);
        writeLe(frame, body.length, 2);
        writeLe(frame, CONTROL_REQUEST, 2);
        writeLe(frame, sequence, 2);
        writeLe(frame, loggerSerial, 4);
        frame.writeBytes(body);
        byte[] withoutTrailer = frame.toByteArray();
        frame.write(checksum(withoutTrailer, 1, withoutTrailer.length));
        frame.write(END);
        return frame.toByteArray();
    }

    /** Valida um frame V5 de resposta e extrai o frame Modbus RTU interno. */
    static byte[] unwrapResponse(byte[] frame) throws ProviderException {
        if (frame == null || frame.length < 13 + RESPONSE_MODBUS_OFFSET) {
            throw new ProviderException("Frame V5 curto demais: "
                    + (frame == null ? 0 : frame.length) + " bytes");
        }
        if (frame[0] != START || frame[frame.length - 1] != END) {
            throw new ProviderException("Delimitadores V5 inválidos (esperado A5...15)");
        }
        int expectedChecksum = checksum(frame, 1, frame.length - 2);
        int receivedChecksum = frame[frame.length - 2] & 0xFF;
        if (expectedChecksum != receivedChecksum) {
            throw new ProviderException(String.format(
                    "Checksum V5 inválido: recebido 0x%02X, calculado 0x%02X",
                    receivedChecksum, expectedChecksum));
        }
        int control = (frame[1 + 2] & 0xFF) | ((frame[1 + 3] & 0xFF) << 8);
        if (control != CONTROL_RESPONSE) {
            throw new ProviderException("Código de controle V5 inesperado: 0x"
                    + Integer.toHexString(control));
        }
        int declaredLength = (frame[1] & 0xFF) | ((frame[2] & 0xFF) << 8);
        int actualPayloadLength = frame.length - 13; // 11 de cabeçalho + checksum + end
        if (declaredLength != actualPayloadLength) {
            throw new ProviderException("Tamanho V5 inconsistente: declarado " + declaredLength
                    + ", real " + actualPayloadLength);
        }
        int modbusStart = 11 + RESPONSE_MODBUS_OFFSET;
        int modbusLength = frame.length - modbusStart - 2; // menos checksum e END
        if (modbusLength < 5) {
            throw new ProviderException("Payload V5 sem frame Modbus válido");
        }
        byte[] modbus = new byte[modbusLength];
        System.arraycopy(frame, modbusStart, modbus, 0, modbusLength);
        return modbus;
    }

    /** Soma bytes de {@code from} (inclusivo) a {@code to} (exclusivo), &amp; 0xFF. */
    static int checksum(byte[] frame, int from, int to) {
        int sum = 0;
        for (int i = from; i < to; i++) {
            sum += frame[i] & 0xFF;
        }
        return sum & 0xFF;
    }

    private static void writeLe(ByteArrayOutputStream out, long value, int bytes) {
        for (int i = 0; i < bytes; i++) {
            out.write((int) (value >> (8 * i)) & 0xFF);
        }
    }
}
