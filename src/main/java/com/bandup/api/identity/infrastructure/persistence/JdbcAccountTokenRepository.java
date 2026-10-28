package com.bandup.api.identity.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.api.identity.domain.token.aggregate.AccountToken;
import com.bandup.api.identity.domain.token.repository.AccountTokenRepository;
import com.bandup.api.identity.domain.token.valueobject.LinkHash;
import com.bandup.api.identity.domain.token.valueobject.TokenPurpose;
import com.bandup.api.identity.domain.user.valueobject.UserId;

/** {@link AccountTokenRepository} on {@code account_token} with plain SQL. */
@Repository
@RequiredArgsConstructor
class JdbcAccountTokenRepository implements AccountTokenRepository {

    private static final String USER = "(select id from app_user where public_id = :user)";

    private final JdbcClient jdbc;

    @Override
    @Transactional
    public void save(AccountToken token) {
        int updated = jdbc.sql("update account_token set used_at = :usedAt where token_hash = :hash")
                .param("usedAt", token.usedAt().map(Timestamp::from).orElse(null))
                .param("hash", token.hash().value()).update();
        if (updated == 0) {
            jdbc.sql("insert into account_token (user_id, purpose, token_hash, issued_at, expires_at, used_at) values ("
                            + USER + ", :purpose, :hash, :issuedAt, :expiresAt, :usedAt)")
                    .param("user", token.userId().value()).param("purpose", token.purpose().name())
                    .param("hash", token.hash().value()).param("issuedAt", Timestamp.from(token.issuedAt()))
                    .param("expiresAt", Timestamp.from(token.expiresAt()))
                    .param("usedAt", token.usedAt().map(Timestamp::from).orElse(null))
                    .update();
        }
    }

    @Override
    public Optional<AccountToken> findByHash(LinkHash hash) {
        return jdbc.sql("""
                select u.public_id, t.purpose, t.token_hash, t.issued_at, t.expires_at, t.used_at
                from account_token t join app_user u on u.id = t.user_id
                where t.token_hash = :hash""")
                .param("hash", hash.value())
                .query((rs, i) -> AccountToken.restore(new UserId(rs.getObject("public_id", UUID.class)),
                        TokenPurpose.valueOf(rs.getString("purpose")), new LinkHash(rs.getString("token_hash")),
                        rs.getTimestamp("issued_at").toInstant(), rs.getTimestamp("expires_at").toInstant(),
                        rs.getTimestamp("used_at") == null ? null : rs.getTimestamp("used_at").toInstant()))
                .optional();
    }

    @Override
    public Optional<Instant> latestIssuedAt(UserId userId, TokenPurpose purpose) {
        // No link of this kind yet → no row (max() would give a null row).
        return jdbc.sql("select issued_at from account_token where user_id = " + USER
                        + " and purpose = :purpose order by issued_at desc limit 1")
                .param("user", userId.value()).param("purpose", purpose.name())
                .query(Timestamp.class).optional().map(Timestamp::toInstant);
    }

    @Override
    @Transactional
    public void invalidateOpen(UserId userId, TokenPurpose purpose, Instant now) {
        jdbc.sql("update account_token set used_at = :now where used_at is null and user_id = " + USER
                        + " and purpose = :purpose")
                .param("now", Timestamp.from(now)).param("user", userId.value()).param("purpose", purpose.name())
                .update();
    }

    @Override
    @Transactional
    public int deleteEndedBefore(Instant cutoff) {
        return jdbc.sql("delete from account_token where expires_at < :cutoff or used_at < :cutoff")
                .param("cutoff", Timestamp.from(cutoff)).update();
    }
}
