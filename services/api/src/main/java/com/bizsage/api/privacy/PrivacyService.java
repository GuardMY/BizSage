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
  // AES-GCM 推荐 12 字节 IV，并使用 128 位认证标签。
  private static final int IV_LENGTH = 12;
  private static final int TAG_LENGTH_BITS = 128;
  private final SecretKeySpec key;
  private final SecureRandom random = new SecureRandom();

  public PrivacyService(String rawKey) {
    // 这里要求 32 个 UTF-8 字节，对应 AES-256；启动期拒绝错误长度密钥。
    if (rawKey == null || rawKey.getBytes(StandardCharsets.UTF_8).length != 32) {
      throw new IllegalArgumentException("AES key must be exactly 32 UTF-8 bytes");
    }
    this.key = new SecretKeySpec(rawKey.getBytes(StandardCharsets.UTF_8), "AES");
  }

  public String encrypt(String plainText) {
    // 空值/空白直接返回，避免把可空字段加密成不可区分的密文。
    if (plainText == null || plainText.isBlank()) {
      return plainText;
    }
    try {
      byte[] iv = new byte[IV_LENGTH];
      random.nextBytes(iv);
      // 每次加密使用随机 IV；最终密文格式为 Base64(IV + ciphertext + tag)。
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
    // 与 encrypt 对称处理空值，便于调用方对可选字段透明使用。
    if (encryptedText == null || encryptedText.isBlank()) {
      return encryptedText;
    }
    try {
      byte[] bytes = Base64.getDecoder().decode(encryptedText);
      ByteBuffer buffer = ByteBuffer.wrap(bytes);
      byte[] iv = new byte[IV_LENGTH];
      // 前 12 字节是 IV，其余为 GCM 密文和认证标签。
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
    // 仅保留前三后四，适合管理端列表和日志展示。
    if (phone == null || phone.length() < 7) {
      return phone;
    }
    return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
  }

  public String maskIdentity(String identity) {
    // 身份证等长标识保留前六后四，中间统一打码。
    if (identity == null || identity.length() < 10) {
      return identity;
    }
    return identity.substring(0, 6) + "********" + identity.substring(identity.length() - 4);
  }
}
