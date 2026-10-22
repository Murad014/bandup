package com.bandup.api.identity.application.port;

import com.bandup.api.identity.domain.session.valueobject.TokenHash;

/**
 * Port: creates refresh-token secrets and hashes them. The raw value goes into
 * the browser's cookie; only its hash is stored.
 */
public interface TokenGenerator {

    GeneratedToken generate();

    /** The hash of a raw value the browser sent back. */
    TokenHash hash(String rawToken);

    record GeneratedToken(String raw, TokenHash hash) {

        @Override
        public String toString() {
            return "GeneratedToken[hash=" + hash + "]";
        }
    }
}
