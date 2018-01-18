package com.grooble.test;

import java.io.ByteArrayOutputStream;
import java.security.AlgorithmParameters;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;

public class SurrogateTest {
    private static final String alphanumeric =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
            + "abcdefghijklmnopqrstuvwxyz"
            + "1234567890"
            + "!#$%&()+*<>?_-=^~|";

    private static String plainText = "I am the very model of a modern major general";
    private static String cipherText = "";
    private static SecureRandom rnd = new SecureRandom();

    private static byte[] salt;

    public static void main(String[] args) {
        // arguments are password and recovery string
        if(args.length>1){            
            System.out.println("password: " + args[0] + "; recovery: " + args[1]);
        }
        String password = args[0];
        
        // passwordKey
        SecretKey passwordKey = getKey(password);
        System.out.println("passwordKey: " + DatatypeConverter.printBase64Binary(passwordKey.getEncoded()));
        
        // Generate surrogate encryption key from random string
        String rand = randomString(24);
        SecretKey surrogateKey = getKey(rand);
        byte[] surrogateByteArray = surrogateKey.getEncoded();
        System.out.println("surrogate: " + DatatypeConverter.printBase64Binary(surrogateByteArray));
        
        // encrypt plainText
        System.out.println("text to encrypt: " + plainText);
        cipherText = encryptWithKey(plainText, surrogateKey);

        // XOR surrogateKey with passwordKey to get storedKey
        SecretKey storedKey = xorWithKey(surrogateKey, passwordKey);
        String storedKeyString = DatatypeConverter.printBase64Binary(storedKey.getEncoded());
        System.out.println("storedKey: "+ storedKeyString);
        
        byte[] storedKey2Array = DatatypeConverter.parseBase64Binary(storedKeyString);
        SecretKey storedKey2 = new SecretKeySpec(storedKey2Array, 0, storedKey2Array.length, "AES");
        String storedKey2String = DatatypeConverter.printBase64Binary(storedKey2.getEncoded());
        System.out.println("storedKey->String->key->string: " + storedKey2String);
        
        // recover surrogateKey from storedKey2
        SecretKey password2Key = getKey(password);
        System.out.println("password2Key: " + DatatypeConverter.printBase64Binary(password2Key.getEncoded()));
        SecretKey surrogate2Key = xorWithKey(storedKey2, password2Key);
        System.out.println("surrogate2 (recovered): " + DatatypeConverter.printBase64Binary(surrogate2Key.getEncoded()));
        
        // decrypt text
        String decryptedText = decryptWithKey(cipherText, surrogate2Key);
        System.out.println("decryptedText: " + decryptedText);
    }
    
    private static SecretKey xorWithKey(SecretKey a, SecretKey b) {
        byte[] out = new byte[b.getEncoded().length];
        for (int i = 0; i < b.getEncoded().length; i++) {
            out[i] = (byte) (b.getEncoded()[i] ^ a.getEncoded()[i%a.getEncoded().length]);
        }
        SecretKey outKey = new SecretKeySpec(out, 0, out.length, "AES");

        return outKey;
    }
    
    private static String randomString(int length){
        StringBuilder sb = new StringBuilder(length);
        for( int i = 0; i < length; i++ ) 
           sb.append( alphanumeric.charAt( rnd.nextInt(alphanumeric.length()) ) );
        return sb.toString();
    }

    // return encryption key
    private static SecretKey getKey(String password){
        try {
            SecureRandom random = new SecureRandom();
            salt = new byte[16];
            random.nextBytes(salt);
            
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            // obtain secret key
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, 256);
            SecretKey tmp = factory.generateSecret(spec);
            SecretKey secret = new SecretKeySpec(tmp.getEncoded(), "AES");

            return secret;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static String encryptWithKey(String str, SecretKey secret) {
        try {            
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secret);
            AlgorithmParameters params = cipher.getParameters();
            byte[] iv = params.getParameterSpec(IvParameterSpec.class).getIV();
            byte[] encryptedText = cipher.doFinal(str.getBytes("UTF-8")); // encrypt the message str here
            
            // concatenate salt + iv + ciphertext
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            
            outputStream.write(salt);
            outputStream.write(iv);
            outputStream.write(encryptedText);
            
            // properly encode the complete ciphertext
            String encrypted = DatatypeConverter.printBase64Binary(outputStream.toByteArray());
            return encrypted;
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    
    private static String decryptWithKey(String str, SecretKey secret) {
        try {
            byte[] ciphertext = DatatypeConverter.parseBase64Binary(str);
            if (ciphertext.length < 48) {
                return null;
            }
            salt = Arrays.copyOfRange(ciphertext, 0, 16);
            byte[] iv = Arrays.copyOfRange(ciphertext, 16, 32);
            byte[] ct = Arrays.copyOfRange(ciphertext, 32, ciphertext.length);
            
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            
            cipher.init(Cipher.DECRYPT_MODE, secret, new IvParameterSpec(iv));
            byte[] plaintext = cipher.doFinal(ct);
            
            return new String(plaintext, "UTF-8");
      
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}
