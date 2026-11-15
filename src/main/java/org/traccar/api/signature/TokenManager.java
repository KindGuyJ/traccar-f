/*
 * Copyright 2022 - 2025 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.api.signature;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.binary.Base64;
import org.traccar.model.RevokedToken;
import org.traccar.storage.Storage;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Singleton
public class TokenManager {

    private static final int DEFAULT_EXPIRATION_DAYS = 7;
    private static final int RESET_EXPIRATION_MINUTES = 60;

    public static final String PURPOSE_RESET = "reset";

    private final ObjectMapper objectMapper;
    private final CryptoManager cryptoManager;
    private final Storage storage;

    private final SecureRandom random = new SecureRandom();

    public static class TokenData {
        @JsonProperty("i")
        private long id;
        @JsonProperty("u")
        private long userId;
        @JsonProperty("e")
        private Date expiration;
        @JsonProperty("p")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String purpose;

        public long getId() {
            return id;
        }

        public long getUserId() {
            return userId;
        }

        public Date getExpiration() {
            return expiration;
        }

        public String getPurpose() {
            return purpose;
        }
    }

    @Inject
    public TokenManager(ObjectMapper objectMapper, CryptoManager cryptoManager, Storage storage) {
        this.objectMapper = objectMapper;
        this.cryptoManager = cryptoManager;
        this.storage = storage;
    }

    public String generateToken(long userId) throws IOException, GeneralSecurityException, StorageException {
        return generateToken(userId, null);
    }

    public String generateToken(
            long userId, Date expiration) throws IOException, GeneralSecurityException, StorageException {
        return generateToken(userId, expiration, null);
    }

    /**
     * Token for the password reset mail. It can only set a new password: it is short lived and
     * {@link #verifyToken} rejects it, so it never opens a session.
     */
    public String generateResetToken(long userId) throws IOException, GeneralSecurityException, StorageException {
        Date expiration = new Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(RESET_EXPIRATION_MINUTES));
        return generateToken(userId, expiration, PURPOSE_RESET);
    }

    private String generateToken(
            long userId, Date expiration, String purpose)
            throws IOException, GeneralSecurityException, StorageException {
        TokenData data = new TokenData();
        data.userId = userId;
        data.purpose = purpose;
        data.id = random.nextLong() & Long.MAX_VALUE;
        if (expiration != null) {
            data.expiration = expiration;
        } else {
            data.expiration = new Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(DEFAULT_EXPIRATION_DAYS));
        }
        byte[] encoded = objectMapper.writeValueAsBytes(data);
        return Base64.encodeBase64URLSafeString(cryptoManager.sign(encoded));
    }

    /**
     * Verifies a token used to log in or to authenticate a request. Tokens issued for another
     * purpose (password reset) are rejected.
     */
    public TokenData verifyToken(String token) throws IOException, GeneralSecurityException, StorageException {
        return verifyToken(token, null);
    }

    /**
     * Verifies a password reset token. Any other token is rejected, so a login token can't be
     * used to set a password.
     */
    public TokenData verifyResetToken(String token) throws IOException, GeneralSecurityException, StorageException {
        return verifyToken(token, PURPOSE_RESET);
    }

    public void revokeToken(TokenData data) throws StorageException {
        RevokedToken revokedToken = new RevokedToken();
        revokedToken.setId(data.getId());
        storage.addObject(revokedToken, new Request(new Columns.Include("id")));
    }

    private TokenData verifyToken(
            String token, String purpose) throws IOException, GeneralSecurityException, StorageException {
        TokenData data = decodeToken(token);
        if (!Objects.equals(data.purpose, purpose)) {
            throw new SecurityException("Token not valid for this use");
        }
        if (data.expiration.before(new Date())) {
            throw new SecurityException("Token has expired");
        }
        var revoked = storage.getObject(RevokedToken.class, new Request(
                new Columns.All(), new Condition.Equals("id", data.getId())));
        if (revoked != null) {
            throw new SecurityException("Token has been revoked");
        }
        return data;
    }

    public TokenData decodeToken(String token) throws IOException, GeneralSecurityException, StorageException {
        byte[] encoded = cryptoManager.verify(Base64.decodeBase64(token));
        return objectMapper.readValue(encoded, TokenData.class);
    }

}
