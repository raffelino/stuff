import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;

/** Signiert eine APK (Schema v2) mit der apksig-Bibliothek und prüft das Ergebnis. */
public class Sign {
    public static void main(String[] args) throws Exception {
        if (args.length < 6) {
            System.err.println("Sign <in.apk> <out.apk> <keystore> <passwort> <alias> <minSdk>");
            System.exit(2);
        }
        File in = new File(args[0]);
        File out = new File(args[1]);
        char[] pw = args[3].toCharArray();
        int minSdk = Integer.parseInt(args[5]);

        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(args[2])) {
            ks.load(fis, pw);
        }
        PrivateKey key = (PrivateKey) ks.getKey(args[4], pw);
        X509Certificate cert = (X509Certificate) ks.getCertificate(args[4]);

        ApkSigner.SignerConfig signer = new ApkSigner.SignerConfig.Builder(
                "release", key, Collections.singletonList(cert)).build();
        new ApkSigner.Builder(Collections.singletonList(signer))
                .setInputApk(in)
                .setOutputApk(out)
                .setMinSdkVersion(minSdk)
                // v1 (JAR-Signatur) braucht in apksig 2.3.0 entfernte JDK-Interna; ab minSdk 24 genügt v2.
                .setV1SigningEnabled(false)
                .setV2SigningEnabled(true)
                .build()
                .sign();

        ApkVerifier.Result result = new ApkVerifier.Builder(out)
                .setMinCheckedPlatformVersion(minSdk)
                .build()
                .verify();
        System.out.println("Signatur geprüft: verified=" + result.isVerified()
                + " v1=" + result.isVerifiedUsingV1Scheme()
                + " v2=" + result.isVerifiedUsingV2Scheme());
        for (ApkVerifier.IssueWithParams e : result.getErrors()) {
            System.out.println("FEHLER: " + e);
        }
        if (!result.isVerified()) System.exit(1);
    }
}
