package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.backup.CategoryDetails;
import fr.vinted.republisher.domain.backup.FormOption;
import fr.vinted.republisher.domain.backup.FormOptions;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.ListingContent;
import fr.vinted.republisher.domain.wardrobe.Price;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/** One column per value of the listing form, so that each keeps its own type, and the photos kept in their order. */
@Entity
@Table(name = "listing_content")
class ListingBackupJpaEntity {

    @Id
    @Column(name = "listing_id")
    private Long listingId;

    @Column(name = "saved_at", nullable = false)
    private Instant savedAt;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private BigDecimal price;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "catalog_id", nullable = false)
    private Long catalogId;

    @Column(name = "category_label", nullable = false)
    private String categoryLabel;

    @Column(name = "brand_id")
    private Long brandId;

    @Column(name = "brand_label")
    private String brandLabel;

    @Column(name = "condition_id", nullable = false)
    private Long conditionId;

    @Column(name = "condition_label", nullable = false)
    private String conditionLabel;

    @Column(name = "size_id")
    private Long sizeId;

    @Column(name = "size_label")
    private String sizeLabel;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "color_ids")
    private List<Long> colorIds;

    @Column(name = "color_label")
    private String colorLabel;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "material_ids")
    private List<Long> materialIds;

    @Column(name = "material_label")
    private String materialLabel;

    @Column(name = "package_size_id", nullable = false)
    private Long packageSizeId;

    @Column(name = "package_size_label", nullable = false)
    private String packageSizeLabel;

    private String isbn;

    @Column(name = "book_title")
    private String bookTitle;

    private String author;

    private String manufacturer;

    @Column(name = "manufacturer_label")
    private String manufacturerLabel;

    private String model;

    @Column(name = "measurement_width")
    private BigDecimal measurementWidth;

    @Column(name = "measurement_length")
    private BigDecimal measurementLength;

    @Column(name = "measurement_unit")
    private String measurementUnit;

    // Fetched with the backups in one more query, not one per listing: the dressing page reads them all often.
    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @CollectionTable(name = "listing_photo", joinColumns = @JoinColumn(name = "listing_id"))
    @OrderColumn(name = "photo_rank")
    private List<ListingPhotoJpaEmbeddable> photos = new ArrayList<>();

    protected ListingBackupJpaEntity() {
    }

    ListingBackupJpaEntity(ListingBackup backup) {
        ListingContent content = backup.content();
        listingId = content.id().value();
        savedAt = backup.savedAt();
        photos = new ArrayList<>(backup.photos().stream().map(ListingPhotoJpaEmbeddable::new).toList());
        title = content.title();
        description = content.description();
        price = content.price().amount();
        currency = content.price().currency();
        catalogId = content.category().id();
        categoryLabel = content.category().label();
        brandId = content.brand().map(FormOption::id).orElse(null);
        brandLabel = content.brand().map(FormOption::label).orElse(null);
        conditionId = content.condition().id();
        conditionLabel = content.condition().label();
        sizeId = content.size().map(FormOption::id).orElse(null);
        sizeLabel = content.size().map(FormOption::label).orElse(null);
        colorIds = content.colors().map(FormOptions::ids).orElse(null);
        colorLabel = content.colors().map(FormOptions::label).orElse(null);
        materialIds = content.materials().map(FormOptions::ids).orElse(null);
        materialLabel = content.materials().map(FormOptions::label).orElse(null);
        packageSizeId = content.packageSize().id();
        packageSizeLabel = content.packageSize().label();
        CategoryDetails details = content.details();
        isbn = details.isbn().orElse(null);
        bookTitle = details.bookTitle().orElse(null);
        author = details.author().orElse(null);
        manufacturer = details.manufacturer().orElse(null);
        manufacturerLabel = details.manufacturerLabel().orElse(null);
        model = details.model().orElse(null);
        measurementWidth = details.measurementWidth().orElse(null);
        measurementLength = details.measurementLength().orElse(null);
        measurementUnit = details.measurementUnit().orElse(null);
    }

    ListingBackup toListingBackup() {
        ListingContent content = new ListingContent(new VintedListingId(listingId), title, description,
                new Price(price, currency),
                new FormOption(catalogId, categoryLabel),
                option(brandId, brandLabel),
                new FormOption(conditionId, conditionLabel),
                option(sizeId, sizeLabel),
                options(colorIds, colorLabel),
                options(materialIds, materialLabel),
                new FormOption(packageSizeId, packageSizeLabel),
                new CategoryDetails(Optional.ofNullable(isbn), Optional.ofNullable(bookTitle),
                        Optional.ofNullable(author), Optional.ofNullable(manufacturer),
                        Optional.ofNullable(manufacturerLabel), Optional.ofNullable(model),
                        Optional.ofNullable(measurementWidth), Optional.ofNullable(measurementLength),
                        Optional.ofNullable(measurementUnit)));
        return new ListingBackup(content, IntStream.range(0, photos.size())
                .mapToObj(rank -> photos.get(rank).toListingPhoto(rank)).toList(), savedAt);
    }

    private static Optional<FormOption> option(Long id, String label) {
        return Optional.ofNullable(id).map(present -> new FormOption(present, label));
    }

    private static Optional<FormOptions> options(List<Long> ids, String label) {
        return Optional.ofNullable(ids).map(present -> new FormOptions(present, label));
    }
}
