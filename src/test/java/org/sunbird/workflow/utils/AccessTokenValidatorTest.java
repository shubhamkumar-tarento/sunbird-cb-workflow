package org.sunbird.workflow.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bouncycastle.operator.KeyWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.common.util.Time;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sunbird.workflow.config.Constants;
import org.sunbird.workflow.models.KeyData;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.PublicKey;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessTokenValidatorTest {

    @Mock
    private KeyManager keyManager;

    @Mock
    private KeyWrapper mockKeyWrapper;

    @Mock
    private PublicKey mockPublicKey;

    @InjectMocks
    private AccessTokenValidator accessTokenValidator;

    private AccessTokenValidator spyAccessTokenValidator;

    private static final ObjectMapper mapper = new ObjectMapper();

    String validToken;
    private String expiredToken;
    private String invalidSignatureToken;
    private String invalidIssuerToken;

    AccessTokenValidator validator;


    @BeforeEach
    void setUp() throws Exception {
        spyAccessTokenValidator = org.mockito.Mockito.spy(new AccessTokenValidator(keyManager));

        // Mock PropertiesCache.getInstance().getProperty(...) if needed
        // Generate tokens for different scenarios
        validToken = generateToken("validUserId", Time.currentTime() + 1000, "expectedIssuer");
        expiredToken = generateToken("expiredUserId", Time.currentTime() - 1000, "expectedIssuer");
        invalidSignatureToken = generateToken("invalidSignatureUserId", Time.currentTime() + 1000, "expectedIssuer");
        invalidIssuerToken = generateToken("invalidIssuerUserId", Time.currentTime() + 1000, "invalidIssuer");

    }

    @Test
    void testVerifyUserToken_ExpiredToken() {
        String userId = accessTokenValidator.verifyUserToken(expiredToken);
        assertEquals(Constants.UNAUTHORIZED_USER_ID, userId);
    }

    @Test
    void testVerifyUserToken_InvalidSignature() {
        String userId = accessTokenValidator.verifyUserToken(invalidSignatureToken);
        assertEquals(Constants.UNAUTHORIZED_USER_ID, userId);
    }

    @Test
    void testVerifyUserToken_InvalidIssuer() {
        String userId = accessTokenValidator.verifyUserToken(invalidIssuerToken);
        assertEquals(Constants.UNAUTHORIZED_USER_ID, userId);
    }

    @Test
    void testFetchUserIdFromAccessToken_NullToken() {
        String userId = accessTokenValidator.fetchUserIdFromAccessToken(null);
        assertNull(userId);
    }

    // Helper method to generate tokens
    private String generateToken(String userId, int exp, String issuer) throws Exception {
        Map<String, Object> header = new HashMap<>();
        header.put("alg", "RS256");
        header.put("typ", "JWT");
        header.put("kid", "testKeyId");

        Map<String, Object> body = new HashMap<>();
        body.put("sub", "user:" + userId);
        body.put("exp", exp);
        body.put("iss", issuer);

        String headerJson = mapper.writeValueAsString(header);
        String bodyJson = mapper.writeValueAsString(body);

        String encodedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(headerJson.getBytes());
        String encodedBody = Base64.getUrlEncoder().withoutPadding().encodeToString(bodyJson.getBytes());

        String unsignedToken = encodedHeader + "." + encodedBody;

        // For simplicity, we're not signing the token here
        // (length must not be ≡1 mod 4, or Base64Util.decode throws "bad base-64")
        String signature = "testSignatureX";

        return unsignedToken + "." + signature;
    }

    @Test
    void fetchUserIdFromAccessToken_validToken_returnsUserId() {
        String accessToken = "validToken";
        String expectedUserId = "user123";


        doReturn(expectedUserId).when(spyAccessTokenValidator).verifyUserToken(accessToken);

        String actualUserId = spyAccessTokenValidator.fetchUserIdFromAccessToken(accessToken);

        assertEquals(expectedUserId, actualUserId);
    }

    @Test
    void fetchUserIdFromAccessToken_unauthorizedToken_returnsNull() {
        String accessToken = "unauthorizedToken";

        doReturn("UNAUTHORIZED").when(spyAccessTokenValidator).verifyUserToken(accessToken);

        String actualUserId = spyAccessTokenValidator.fetchUserIdFromAccessToken(accessToken);

        assertNull(actualUserId);
    }

    @Test
    void fetchUserIdFromAccessToken_nullToken_returnsNull() {
        String actualUserId = spyAccessTokenValidator.fetchUserIdFromAccessToken(null);
        assertNull(actualUserId);
    }

    @Test
    void fetchUserIdFromAccessToken_exceptionThrown_returnsNull() {
        String accessToken = "token";

        doThrow(new RuntimeException("some error")).when(spyAccessTokenValidator).verifyUserToken(accessToken);

        String actualUserId = spyAccessTokenValidator.fetchUserIdFromAccessToken(accessToken);

        assertNull(actualUserId);
    }

    @Test
    void testCheckIss_invalidIssuer_returnsFalse() throws Exception {
        validator = new AccessTokenValidator(keyManager);

        Method method = AccessTokenValidator.class.getDeclaredMethod("checkIss", String.class);
        method.setAccessible(true);

        boolean result = (boolean) method.invoke(validator, "invalid-issuer");

        assertFalse(result, "Expected false when issuer does not match REALM_URL");
    }

    @Test
    void testCheckIss_blankIssuer_returnsFalse() throws Exception {
        validator = new AccessTokenValidator(keyManager);

        Method method = AccessTokenValidator.class.getDeclaredMethod("checkIss", String.class);
        method.setAccessible(true);

        boolean result = (boolean) method.invoke(validator, "");

        assertFalse(result, "Expected false when issuer is blank");
    }

    private PropertiesCache swapStaticCache(PropertiesCache replacement) throws Exception {
        Field cacheField = AccessTokenValidator.class.getDeclaredField("cache");
        cacheField.setAccessible(true);
        PropertiesCache original = (PropertiesCache) cacheField.get(null);
        cacheField.set(null, replacement);
        return original;
    }

    private PropertiesCache originalCache;

    @AfterEach
    void restoreStaticCache() throws Exception {
        if (originalCache != null) {
            Field cacheField = AccessTokenValidator.class.getDeclaredField("cache");
            cacheField.setAccessible(true);
            cacheField.set(null, originalCache);
            originalCache = null;
        }
    }

    @Test
    void testCheckIss_matchingIssuer_returnsTrue() throws Exception {
        PropertiesCache mockCache = org.mockito.Mockito.mock(PropertiesCache.class);
        when(mockCache.getProperty(Constants.SSO_URL)).thenReturn("https://sso/");
        when(mockCache.getProperty(Constants.SSO_REALM)).thenReturn("sunbird");
        originalCache = swapStaticCache(mockCache);

        validator = new AccessTokenValidator(keyManager);
        Method method = AccessTokenValidator.class.getDeclaredMethod("checkIss", String.class);
        method.setAccessible(true);

        boolean result = (boolean) method.invoke(validator, "https://sso/realms/sunbird");

        assertTrue(result, "Expected true when issuer matches the composed realm URL");
    }

    @Test
    void testVerifyUserToken_validSignatureAndIssuer_returnsUserId() throws Exception {
        PropertiesCache mockCache = org.mockito.Mockito.mock(PropertiesCache.class);
        when(mockCache.getProperty(Constants.SSO_URL)).thenReturn("https://sso/");
        when(mockCache.getProperty(Constants.SSO_REALM)).thenReturn("sunbird");
        originalCache = swapStaticCache(mockCache);

        String matchingIssuerToken = generateToken("validUserId", Time.currentTime() + 1000, "https://sso/realms/sunbird");

        KeyData keyData = new KeyData("testKeyId", mockPublicKey);
        doReturn(keyData).when(keyManager).getPublicKey("testKeyId");

        try (MockedStatic<CryptoUtil> cryptoUtilStatic = mockStatic(CryptoUtil.class)) {
            cryptoUtilStatic.when(() -> CryptoUtil.verifyRSASign(any(), any(), any(), anyString())).thenReturn(true);

            String userId = accessTokenValidator.verifyUserToken(matchingIssuerToken);

            assertEquals("validUserId", userId);
        }
    }

    @Test
    void testVerifyUserToken_validSignature_expiredToken_returnsUnauthorized() throws Exception {
        String matchingIssuerExpiredToken = generateToken("expiredUserId", Time.currentTime() - 1000, "https://sso/realms/sunbird");

        KeyData keyData = new KeyData("testKeyId", mockPublicKey);
        doReturn(keyData).when(keyManager).getPublicKey("testKeyId");

        try (MockedStatic<CryptoUtil> cryptoUtilStatic = mockStatic(CryptoUtil.class)) {
            cryptoUtilStatic.when(() -> CryptoUtil.verifyRSASign(any(), any(), any(), anyString())).thenReturn(true);

            String userId = accessTokenValidator.verifyUserToken(matchingIssuerExpiredToken);

            assertEquals(Constants.UNAUTHORIZED_USER_ID, userId);
        }
    }

    @Test
    void testFetchUserRolesFromToken_invalidSignature_emptyPayload_returnsEmptyList() {
        KeyData keyData = new KeyData("testKeyId", mockPublicKey);
        doReturn(keyData).when(keyManager).getPublicKey("testKeyId");

        try (MockedStatic<CryptoUtil> cryptoUtilStatic = mockStatic(CryptoUtil.class)) {
            cryptoUtilStatic.when(() -> CryptoUtil.verifyRSASign(any(), any(), any(), anyString())).thenReturn(false);

            List<String> roles = accessTokenValidator.fetchUserRolesFromToken(invalidSignatureToken);

            assertTrue(roles.isEmpty());
        }
    }

    @Test
    void testFetchUserRolesFromToken_malformedToken_exceptionCaught_returnsEmptyList() {
        List<String> roles = accessTokenValidator.fetchUserRolesFromToken("not-a-valid-jwt");

        assertTrue(roles.isEmpty());
    }

    @Test
    void testFetchUserRolesFromToken_rolesPresent_returnsRoles() throws Exception {
        Map<String, Object> header = new HashMap<>();
        header.put("alg", "RS256");
        header.put("typ", "JWT");
        header.put("kid", "testKeyId");
        Map<String, Object> body = new HashMap<>();
        body.put("sub", "user:rolesUser");
        body.put("exp", Time.currentTime() + 1000);
        body.put("iss", "https://sso/realms/sunbird");
        body.put("user_roles", List.of("ADMIN", "MEMBER"));
        String headerJson = mapper.writeValueAsString(header);
        String bodyJson = mapper.writeValueAsString(body);
        String encodedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(headerJson.getBytes());
        String encodedBody = Base64.getUrlEncoder().withoutPadding().encodeToString(bodyJson.getBytes());
        String tokenWithRoles = encodedHeader + "." + encodedBody + "." + "testSignatureX";

        KeyData keyData = new KeyData("testKeyId", mockPublicKey);
        doReturn(keyData).when(keyManager).getPublicKey("testKeyId");

        try (MockedStatic<CryptoUtil> cryptoUtilStatic = mockStatic(CryptoUtil.class)) {
            cryptoUtilStatic.when(() -> CryptoUtil.verifyRSASign(any(), any(), any(), anyString())).thenReturn(true);

            List<String> roles = accessTokenValidator.fetchUserRolesFromToken(tokenWithRoles);

            assertEquals(List.of("ADMIN", "MEMBER"), roles);
        }
    }
}
