package com.solarmonitor.provider.local;

import com.solarmonitor.config.service.ConfigurationService;
import com.solarmonitor.plant.domain.Inverter;
import com.solarmonitor.provider.EnergyProvider;
import com.solarmonitor.provider.EnergyReading;
import com.solarmonitor.provider.ProviderException;
import com.solarmonitor.provider.ProviderMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Coleta local direta do data logger Solarman (porta TCP 8899) via protocolo
 * V5 encapsulando Modbus RTU — sem dependência de nuvem.
 *
 * <p>Configurações usadas (tela de Configurações / tabela {@code configurations}):
 * {@code provider.local.logger-ip}, {@code provider.local.logger-port} e
 * {@code provider.local.logger-serial} (serial NUMÉRICO do stick logger,
 * impresso na etiqueta do equipamento).</p>
 *
 * <p>Uma conexão TCP nova por ciclo de leitura: o logger derruba conexões
 * ociosas e o custo de handshake na LAN é desprezível frente ao intervalo
 * de 5 s.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SolarmanLocalProvider implements EnergyProvider {

    private static final int CONNECT_TIMEOUT_MS = 3_000;
    private static final int READ_TIMEOUT_MS = 5_000;
    private static final int MAX_FRAME_SIZE = 1024;

    private final ConfigurationService configurations;
    private final AtomicInteger sequence = new AtomicInteger(1);

    @Override
    public ProviderMode mode() {
        return ProviderMode.LOCAL;
    }

    @Override
    public EnergyReading read(Inverter inverter) throws ProviderException {
        String ip = configurations.getString("provider.local.logger-ip")
                .filter(s -> !s.isBlank())
                .orElseThrow(() -> new ProviderException(
                        "provider.local.logger-ip não configurado — informe o IP do logger na tela de Configurações"));
        int port = configurations.getInt("provider.local.logger-port").orElse(8899);
        long loggerSerial = configurations.getLong("provider.local.logger-serial")
                .orElseThrow(() -> new ProviderException(
                        "provider.local.logger-serial não configurado — serial numérico do stick logger"));

        Map<Integer, Integer> registers = new HashMap<>();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);
            for (int[] range : DeyeRegisters.READ_RANGES) {
                readRange(socket, loggerSerial, range[0], range[1], registers);
            }
        } catch (IOException e) {
            throw new ProviderException("Falha de comunicação com o logger " + ip + ":" + port
                    + " — " + e.getMessage(), e);
        }
        return DeyeRegisters.decode(registers, Instant.now().truncatedTo(ChronoUnit.MICROS));
    }

    private void readRange(Socket socket, long loggerSerial, int start, int count,
                           Map<Integer, Integer> out) throws IOException, ProviderException {
        byte[] modbusRequest = ModbusRtu.buildReadRequest(start, count);
        byte[] v5Request = SolarmanV5Frame.wrapRequest(loggerSerial,
                sequence.getAndIncrement() & 0xFFFF, modbusRequest);

        OutputStream os = socket.getOutputStream();
        os.write(v5Request);
        os.flush();

        byte[] v5Response = readFrame(socket.getInputStream());
        byte[] modbusResponse = SolarmanV5Frame.unwrapResponse(v5Response);
        int[] values = ModbusRtu.parseReadResponse(modbusResponse, count);
        for (int i = 0; i < values.length; i++) {
            out.put(start + i, values[i]);
        }
    }

    /**
     * Lê um frame V5 completo: cabeçalho fixo de 11 bytes traz o tamanho do
     * payload; depois payload + checksum + byte final.
     */
    private byte[] readFrame(InputStream in) throws IOException, ProviderException {
        DataInputStream din = new DataInputStream(in);
        byte[] header = new byte[11];
        din.readFully(header);
        if (header[0] != SolarmanV5Frame.START) {
            throw new ProviderException("Resposta não inicia com 0xA5 — logger em modo incompatível?");
        }
        int payloadLength = (header[1] & 0xFF) | ((header[2] & 0xFF) << 8);
        int total = 11 + payloadLength + 2;
        if (total > MAX_FRAME_SIZE) {
            throw new ProviderException("Frame V5 anuncia tamanho suspeito: " + total + " bytes");
        }
        byte[] frame = new byte[total];
        System.arraycopy(header, 0, frame, 0, 11);
        din.readFully(frame, 11, total - 11);
        return frame;
    }
}
