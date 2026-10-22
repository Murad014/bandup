package com.bandup.api.identity.domain.session.aggregate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.bandup.api.identity.domain.session.entity.RefreshToken;
import com.bandup.api.identity.domain.session.valueobject.SessionId;
import com.bandup.api.identity.domain.session.valueobject.TokenHash;
import com.bandup.api.identity.domain.user.valueobject.UserId;
import com.bandup.api.shared.domain.ddd.AggregateRoot;

/**
 * One login session (aggregate root): the family of refresh tokens created by
 * one sign-in and its rotations (requirements 8.4, 8.5). It guards the
 * session's rules as a whole:
 *
 * <ul>
 *   <li>Presenting a usable token rotates it: the token is marked replaced and
 *       a new one is added.</li>
 *   <li>Presenting a replaced token means it was copied: the whole session is
 *       revoked, including the newest token (the real user signs in again).</li>
 *   <li>Expired or revoked tokens are rejected and nothing changes.</li>
 *   <li>Logging out revokes the whole session.</li>
 * </ul>
 *
 * Loaded and saved as one unit; references its user only by {@link UserId}.
 */
@AggregateRoot
public final class Session {

    public enum Reason {
        EXPIRED,
        REVOKED,
        REUSED
    }

    /** The result of presenting a refresh token. Either way, save the session. */
    public sealed interface Outcome permits Rotated, Rejected {
    }

    public record Rotated(RefreshToken next) implements Outcome {
    }

    public record Rejected(Reason reason) implements Outcome {
    }

    private final SessionId id;
    private final UserId userId;
    private final List<RefreshToken> tokens;

    private Session(SessionId id, UserId userId, List<RefreshToken> tokens) {
        this.id = Objects.requireNonNull(id, "id");
        this.userId = Objects.requireNonNull(userId, "userId");
        if (tokens == null || tokens.isEmpty()) {
            throw new IllegalArgumentException("A session has at least one token");
        }
        Set<TokenHash> hashes = new HashSet<>();
        tokens.forEach(t -> {
            if (!hashes.add(t.hash())) {
                throw new IllegalArgumentException("Token " + t.hash() + " appears twice in session " + id);
            }
        });
        tokens.forEach(t -> t.replacedBy().ifPresent(next -> {
            if (!hashes.contains(next)) {
                throw new IllegalArgumentException("Token " + t.hash() + " was replaced by a token outside session " + id);
            }
        }));
        this.tokens = new ArrayList<>(tokens);
    }

    /** A new sign-in: a session with its first token. */
    public static Session start(UserId userId, TokenHash firstToken, Instant now, Duration lifetime) {
        return new Session(SessionId.newId(), userId, List.of(RefreshToken.issue(firstToken, now, lifetime)));
    }

    /** Rebuilds a stored session (for the persistence adapter); tokens in the order they were issued. */
    public static Session restore(SessionId id, UserId userId, List<RefreshToken> tokens) {
        return new Session(id, userId, tokens);
    }

    /**
     * A browser presents one of this session's tokens.
     *
     * @param presented the token the browser sent (must belong to this session)
     * @param next      hash of the new token to hand out if rotation succeeds
     */
    public Outcome present(TokenHash presented, TokenHash next, Instant now, Duration lifetime) {
        RefreshToken token = token(presented)
                .orElseThrow(() -> new IllegalArgumentException("Token " + presented + " is not part of session " + id));
        return switch (token.stateAt(now)) {
            case USABLE -> {
                RefreshToken successor = RefreshToken.issue(next, now, lifetime);
                tokens.set(tokens.indexOf(token), token.withReplacedBy(next));
                tokens.add(successor);
                yield new Rotated(successor);
            }
            case REPLACED -> {
                revoke(now);
                yield new Rejected(Reason.REUSED);
            }
            case EXPIRED -> new Rejected(Reason.EXPIRED);
            case REVOKED -> new Rejected(Reason.REVOKED);
        };
    }

    /** Logout, theft, or a disabled user: every token of the session stops working. */
    public void revoke(Instant now) {
        tokens.replaceAll(token -> token.revoked(now));
    }

    public boolean isRevoked() {
        return tokens.stream().allMatch(token -> token.revokedAt().isPresent());
    }

    public Optional<RefreshToken> token(TokenHash hash) {
        return tokens.stream().filter(token -> token.hash().equals(hash)).findFirst();
    }

    /** The most recently issued token. */
    public RefreshToken newestToken() {
        return tokens.get(tokens.size() - 1);
    }

    public SessionId id() {
        return id;
    }

    public UserId userId() {
        return userId;
    }

    /** Every token, in the order issued. */
    public List<RefreshToken> tokens() {
        return List.copyOf(tokens);
    }

    /** Aggregates are equal by identity. */
    @Override
    public boolean equals(Object other) {
        return other instanceof Session session && id.equals(session.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
