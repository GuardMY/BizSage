package com.bizsage.api.privacy;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class PrivacyService {
  private static final int IV_LENGTH = 12;
  private static final int TAG_LENGTH_BITS = 128;
  private final SecretKeySpec key;
  private final SecureRandom random = new SecureRandom();

  public PrivacyService(String rawKey) {
    if (rawKey == null || rawKey.getBytes(StandardCharsets.UTF_8).length != 32) {
      throw new IllegalArgumentException("AES key must be exactly 32 UTF-8 bytes");
    }
    this.key = new SecretKeySpec(rawKey.getBytes(StandardCharsets.UTF_8), "AES");
  }

  public String encrypt(String plainText) {
    if (plainText == null || plainText.isBlank()) {
      return plainText;
    }
    try {
      byte[] iv = new byte[IV_LENGTH];
      random.nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
      ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
      buffer.put(iv);
      buffer.put(encrypted);
      return Base64.getEncoder().encodeToString(buffer.array());
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("encryption failed", exception);
    }
  }

  public String decrypt(String encryptedText) {
    if (encryptedText == null || encryptedText.isBlank()) {
      return encryptedText;
    }
    try {
      byte[] bytes = Base64.getDecoder().decode(encryptedText);
      ByteBuffer buffer = ByteBuffer.wrap(bytes);
      byte[] iv = new byte[IV_LENGTH];
      buffer.get(iv);
      byte[] encrypted = new byte[buffer.remaining()];
      buffer.get(encrypted);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("decryption failed", exception);
    }
  }

  public String maskPhone(String phone) {
    if (phone == null || phone.length() < 7) {
      return phone;
    }
    return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
  }

  public String maskIdentity(String identity) {
    if (identity == null || identity.length() < 10) {
      return identity;
    }
    return identity.substring(0, 6) + "********" + identity.substring(identity.length() - 4);
  }
}
