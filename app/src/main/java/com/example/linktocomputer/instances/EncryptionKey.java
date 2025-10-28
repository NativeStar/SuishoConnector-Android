package com.example.linktocomputer.instances;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public class EncryptionKey {
    SecretKey encryptKey;
    byte[] iv=new byte[16];
    private void init(String algorithm,int size) throws NoSuchAlgorithmException {
        KeyGenerator keyGenerator=KeyGenerator.getInstance(algorithm);
        keyGenerator.init(size);
        encryptKey = keyGenerator.generateKey();
        SecureRandom secureRandom=new SecureRandom();
        secureRandom.nextBytes(iv);
    }
    public static EncryptionKey getInstance(String algorithm,int size) throws NoSuchAlgorithmException {
        EncryptionKey instance=new EncryptionKey();
        instance.init(algorithm,size);
        return instance;
    }
    public String getKeyBase64() {
        return Base64.getEncoder().encodeToString(encryptKey.getEncoded());
    }
    public byte[] getKeyEncoded(){
        return encryptKey.getEncoded();
    }
    public byte[] getIv(){
        return iv;
    }
    public String getIvBase64(){
        return Base64.getEncoder().encodeToString(iv);
    }
}
