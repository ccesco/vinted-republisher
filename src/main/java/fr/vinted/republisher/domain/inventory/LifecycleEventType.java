package fr.vinted.republisher.domain.inventory;

/** An established fact in the life of a listing. */
public enum LifecycleEventType {
    FIRST_SEEN,
    DELETED,
    REPUBLISHED,
    LEFT_WARDROBE
}
