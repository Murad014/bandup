package com.bandup.api.grammar.application.port;

/**
 * Port: cleans a lesson text before it is stored (grammar requirement 9.8):
 * only {@code <b>}, {@code <i>} and {@code <br>} survive, without attributes.
 */
public interface TextSanitizer {

    String clean(String html);
}
