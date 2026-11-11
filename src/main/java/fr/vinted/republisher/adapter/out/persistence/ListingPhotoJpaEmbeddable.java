package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.backup.ListingPhoto;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A photo of a listing backup; its rank is its place in the list. */
@Embeddable
class ListingPhotoJpaEmbeddable {

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(nullable = false, length = 4)
    private String extension;

    protected ListingPhotoJpaEmbeddable() {
    }

    ListingPhotoJpaEmbeddable(ListingPhoto photo) {
        sha256 = photo.sha256();
        extension = photo.extension();
    }

    ListingPhoto toListingPhoto(int rank) {
        return new ListingPhoto(rank, sha256, extension);
    }
}
