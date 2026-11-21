package fr.vinted.republisher.domain.wardrobe;

import java.util.Objects;
import java.util.UUID;

/** The identity of an article: never changes, whatever listings it is put on sale by (US-53). */
public record ArticleId(UUID value) {
    public ArticleId {
        Objects.requireNonNull(value);
    }

    public static ArticleId newArticle() {
        return new ArticleId(UUID.randomUUID());
    }
}
