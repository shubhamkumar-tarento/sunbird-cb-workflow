package org.sunbird.workflow.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.sunbird.workflow.config.Constants;
import org.sunbird.workflow.models.KeyData;


import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Component
public class KeyManager {

  private static Logger logger = LoggerFactory.getLogger(KeyManager.class.getName());
  private static PropertiesCache propertiesCache = PropertiesCache.getInstance();

  private static Map<String, KeyData> keyMap = new HashMap<>();

  @PostConstruct
  public void init() {
    String basePath = propertiesCache.getProperty(Constants.ACCESS_TOKEN_PUBLICKEY_BASEPATH);
    try (Stream<Path> walk = Files.walk(Paths.get(basePath))) {
      List<String> result =
              walk.filter(Files::isRegularFile).map(x -> x.toString()).toList();
      result.forEach(
              file -> {
                try {
                  StringBuilder contentBuilder = new StringBuilder();
                  Path path = Paths.get(file);
                  Files.lines(path, StandardCharsets.UTF_8)
                          .forEach(contentBuilder::append);
                  KeyData keyData =
                          new KeyData(
                                  path.getFileName().toString(), loadPublicKey(contentBuilder.toString()));
                  keyMap.put(path.getFileName().toString(), keyData);
                } catch (Exception e) {
                  logger.error("KeyManager:init: exception in reading public keys ", e);
                }
              });
    } catch (Exception e) {
      logger.error("KeyManager:init: exception in loading publickeys ", e);
    }
  }

  public KeyData getPublicKey(String keyId) {
    return keyMap.get(keyId);
  }

  public static PublicKey loadPublicKey(String key) throws GeneralSecurityException {
    String publicKey = new String(key.getBytes(), StandardCharsets.UTF_8);
    publicKey = removePemArmor(publicKey, "BEGIN PUBLIC KEY");
    publicKey = removePemArmor(publicKey, "END PUBLIC KEY");
    publicKey = stripLineBreaks(publicKey);
    byte[] keyBytes = Base64Util.decode(publicKey.getBytes(StandardCharsets.UTF_8), Base64Util.DEFAULT);

    X509EncodedKeySpec X509publicKey = new X509EncodedKeySpec(keyBytes);
    KeyFactory kf = KeyFactory.getInstance("RSA");
    return kf.generatePublic(X509publicKey);
  }

  /**
   * Removes a PEM armor marker (e.g. "BEGIN PUBLIC KEY") along with any dashes
   * immediately surrounding it, without using a regular expression.
   */
  private static String removePemArmor(String input, String marker) {
    int markerStart = input.indexOf(marker);
    if (markerStart < 0) {
      return input;
    }
    int start = markerStart;
    while (start > 0 && input.charAt(start - 1) == '-') {
      start--;
    }
    int end = markerStart + marker.length();
    while (end < input.length() && input.charAt(end) == '-') {
      end++;
    }
    return input.substring(0, start) + input.substring(end);
  }

  private static String stripLineBreaks(String input) {
    StringBuilder result = new StringBuilder(input.length());
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      if (c != '\r' && c != '\n') {
        result.append(c);
      }
    }
    return result.toString();
  }
}