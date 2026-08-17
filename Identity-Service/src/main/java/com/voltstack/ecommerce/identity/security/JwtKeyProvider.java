package com.voltstack.ecommerce.identity.security;

import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Component
public class JwtKeyProvider {

    private final RSAPublicKey publicKey;
    private final RSAKey rsaKey;

    public JwtKeyProvider(@Value("${jwt.private-key-path}") String privateKeyPath,
                          @Value("${jwt.public-key-path}") String publicKeyPath) {
        RSAPrivateKey privateKey = readPrivateKey(privateKeyPath);
        this.publicKey = readPublicKey(publicKeyPath);
        this.rsaKey = new RSAKey.Builder(this.publicKey)
                .privateKey(privateKey)
                .keyID("identity-rsa-key")
                .build();
    }

    public RSAPublicKey getPublicKey() {
        return publicKey;
    }

    public RSAKey getRsaKey() {
        return rsaKey;
    }

    private RSAPrivateKey readPrivateKey(String path) {
        try {
            String pem = Files.readString(Path.of(path));
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(parsePem(pem, "PRIVATE KEY"));
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception e) {
            throw new IllegalStateException("Không đọc được JWT private key từ " + path, e);
        }
    }

    private RSAPublicKey readPublicKey(String path) {
        try {
            String pem = Files.readString(Path.of(path));
            X509EncodedKeySpec spec = new X509EncodedKeySpec(parsePem(pem, "PUBLIC KEY"));
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception e) {
            throw new IllegalStateException("Không đọc được JWT public key từ " + path, e);
        }
    }

    private byte[] parsePem(String pem, String type) {
        String body = pem
                .replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }
}
