package com.digitalnest.petmemorial.digitallife;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Local-only placeholder. It never calls an AI vendor and never speaks as the memorialized life.
 * It selects confirmed facts and makes every returned sentence traceable to a user-approved source.
 */
@Component
public class DeterministicMemoryAnswerProvider implements MemoryAnswerProvider {
    @Override
    public DigitalLifeAnswer answer(String question, List<DigitalLifeFact> confirmedFacts) {
        String normalized = question.toLowerCase(Locale.ROOT);
        List<DigitalLifeFact> matching = confirmedFacts.stream()
                .filter(fact -> matches(normalized, fact))
                .sorted(Comparator.comparing(DigitalLifeFact::updatedAt).reversed())
                .limit(3)
                .toList();
        if (matching.isEmpty()) {
            return new DigitalLifeAnswer("已有的记忆中还没有足够资料回答这个问题。你可以先补充一段生活细节，或把已有资料确认后再来问。",
                    "DETERMINISTIC_MOCK", List.of(), "这是根据已授权资料整理的记忆问答，不代表 TA 的意识、声音或意愿。");
        }
        List<DigitalLifeAnswer.Citation> citations = new ArrayList<>();
        StringBuilder response = new StringBuilder("已有的记忆中记录了：");
        for (int index = 0; index < matching.size(); index++) {
            DigitalLifeFact fact = matching.get(index);
            if (index > 0) response.append(index == matching.size() - 1 ? "；以及" : "；");
            response.append(fact.statement());
            citations.add(new DigitalLifeAnswer.Citation(fact.sourceType(), fact.sourceLabel(), fact.statement()));
        }
        return new DigitalLifeAnswer(response.append("。").toString(), "DETERMINISTIC_MOCK", List.copyOf(citations),
                "这是根据已授权、已确认资料整理的记忆问答，不代表 TA 的意识、声音或意愿。");
    }

    private boolean matches(String question, DigitalLifeFact fact) {
        String haystack = (fact.statement() + " " + fact.sourceLabel() + " " + fact.factType()).toLowerCase(Locale.ROOT);
        return question.chars().filter(Character::isLetterOrDigit).mapToObj(c -> String.valueOf((char) c))
                .distinct().anyMatch(haystack::contains);
    }
}
