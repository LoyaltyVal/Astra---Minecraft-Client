package dev.astra.client.cosmetics;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * HTTPS for old Java 8 builds. Minecraft 1.8.9 runs on Mojang's Java 8u51, whose certificate list
 * predates Let's Encrypt and Google Trust Services, so it can't open most https:// sites today
 * (including Cloudflare Tunnel addresses). This trusts Java's own list plus a few current roots
 * bundled in assets/astra/roots.pem. Only used for the Astra server connection.
 */
final class AstraTrust {

    private static SSLSocketFactory factory;
    private static boolean tried;

    private AstraTrust() {
    }

    /** A socket factory that also trusts the bundled roots, or null to use Java's default. */
    static synchronized SSLSocketFactory socketFactory() {
        if (tried) {
            return factory;
        }
        tried = true;
        try {
            final X509TrustManager system = trustManager(null);
            KeyStore extra = KeyStore.getInstance(KeyStore.getDefaultType());
            extra.load(null, null);
            int i = 0;
            for (X509Certificate cert : bundled()) {
                extra.setCertificateEntry("astra-" + (i++), cert);
            }
            final X509TrustManager bundled = trustManager(extra);
            X509TrustManager both = new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    system.checkClientTrusted(chain, authType);
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    try {
                        system.checkServerTrusted(chain, authType);
                    } catch (CertificateException e) {
                        bundled.checkServerTrusted(chain, authType);
                    }
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    X509Certificate[] a = system.getAcceptedIssuers();
                    X509Certificate[] b = bundled.getAcceptedIssuers();
                    X509Certificate[] all = new X509Certificate[a.length + b.length];
                    System.arraycopy(a, 0, all, 0, a.length);
                    System.arraycopy(b, 0, all, a.length, b.length);
                    return all;
                }
            };
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{both}, null);
            factory = ctx.getSocketFactory();
        } catch (Exception e) {
            System.err.println("[Astra] Couldn't load the bundled certificates, using Java's own: " + e);
            factory = null;
        }
        return factory;
    }

    private static X509TrustManager trustManager(KeyStore store) throws Exception {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(store);
        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager) {
                return (X509TrustManager) tm;
            }
        }
        throw new IllegalStateException("no X509 trust manager");
    }

    private static List<X509Certificate> bundled() throws Exception {
        List<X509Certificate> out = new ArrayList<X509Certificate>();
        InputStream in = AstraTrust.class.getResourceAsStream("/assets/astra/roots.pem");
        if (in == null) {
            return out;
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try {
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) > 0) {
                buf.write(chunk, 0, n);
            }
        } finally {
            in.close();
        }
        String pem = new String(buf.toByteArray(), Charset.forName("US-ASCII"));
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        String begin = "-----BEGIN CERTIFICATE-----";
        String end = "-----END CERTIFICATE-----";
        int from = 0;
        while (true) {
            int b = pem.indexOf(begin, from);
            if (b < 0) {
                break;
            }
            int e = pem.indexOf(end, b);
            if (e < 0) {
                break;
            }
            byte[] der = Base64.getMimeDecoder().decode(pem.substring(b + begin.length(), e));
            out.add((X509Certificate) cf.generateCertificate(new ByteArrayInputStream(der)));
            from = e + end.length();
        }
        return out;
    }
}
