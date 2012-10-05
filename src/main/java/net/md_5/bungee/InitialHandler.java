package net.md_5.bungee;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import javax.crypto.SecretKey;
import net.md_5.bungee.packet.Packet2Handshake;
import net.md_5.bungee.packet.PacketFCEncryptionResponse;
import net.md_5.bungee.packet.PacketFDEncryptionRequest;
import net.md_5.bungee.packet.PacketFFKick;
import net.md_5.bungee.packet.PacketInputStream;
import org.bouncycastle.crypto.io.CipherInputStream;
import org.bouncycastle.crypto.io.CipherOutputStream;

public class InitialHandler implements Runnable {

    private final Socket socket;
    private PacketInputStream in;
    private OutputStream out;

    public InitialHandler(Socket socket) throws IOException {
        this.socket = socket;
        in = new PacketInputStream(socket.getInputStream());
        out = socket.getOutputStream();
    }

    @Override
    public void run() {
        try {
            byte[] packet = in.readPacket();
            int id = Util.getId(packet);
            switch (id) {
                case 0x02:
                    Packet2Handshake handshake = new Packet2Handshake(packet);
                    PacketFDEncryptionRequest request = EncryptionUtil.encryptRequest();
                    out.write(request.getPacket());
                    PacketFCEncryptionResponse response = new PacketFCEncryptionResponse(in.readPacket());

                    SecretKey shared = EncryptionUtil.getSecret(response, request);
                    if (!EncryptionUtil.isAuthenticated(handshake.username, request.serverId, shared)) {
                        throw new KickException("Not authenticated with minecraft.net");
                    }

                    out.write(new PacketFCEncryptionResponse().getPacket());
                    in = new PacketInputStream(new CipherInputStream(socket.getInputStream(), EncryptionUtil.getCipher(false, shared)));
                    out = new BufferedOutputStream(new CipherOutputStream(socket.getOutputStream(), EncryptionUtil.getCipher(true, shared)), 5120);

                    int ciphId = Util.getId(in.readPacket());
                    if (ciphId != 0xCD) {
                        throw new KickException("Unable to receive encrypted client status");
                    }

                    break;
                case 0xFE:
                    throw new KickException(BungeeCord.instance.config.motd + ChatColor.COLOR_CHAR + BungeeCord.instance.getOnlinePlayers() + ChatColor.COLOR_CHAR + BungeeCord.instance.config.maxPlayers);
                default:
                    throw new IllegalArgumentException("Wasn't ready for packet id " + Util.hex(id));
            }
        } catch (KickException ex) {
            kick(ex.getMessage());
        } catch (Exception ex) {
            kick("[Proxy Error] " + Util.exception(ex));
        }
    }

    private void kick(String message) {
        try {
            out.write(new PacketFFKick(message).getPacket());
        } catch (IOException ioe) {
        } finally {
            try {
                socket.close();
            } catch (IOException ioe2) {
            }
        }
    }

    private class KickException extends RuntimeException {

        public KickException(String message) {
            super(message);
        }
    }
}
