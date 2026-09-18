package com.digitalnest.petmemorial.digitallife;

import java.util.List;

/** Port for a future approved retrieval / LLM provider. No vendor SDK enters the domain service. */
public interface MemoryAnswerProvider {
    DigitalLifeAnswer answer(String question, List<DigitalLifeFact> confirmedFacts);
}
