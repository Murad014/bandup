package com.bandup.api.grammar.application.port;

import java.time.LocalDate;

/** Port: today in the grammar day zone (grammar requirement definitions: "Day"). */
public interface GrammarDays {

    LocalDate today();
}
