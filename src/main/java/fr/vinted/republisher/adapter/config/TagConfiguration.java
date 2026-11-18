package fr.vinted.republisher.adapter.config;

import fr.vinted.republisher.application.tag.EditListingTags;
import fr.vinted.republisher.application.tag.ShowListingTags;
import fr.vinted.republisher.domain.tag.ListingTagRepository;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class TagConfiguration {

    @Bean
    EditListingTags editListingTags(ListingRepository listings, ListingTagRepository tags) {
        return new EditListingTags(listings, tags);
    }

    @Bean
    ShowListingTags showListingTags(ListingTagRepository tags) {
        return new ShowListingTags(tags);
    }
}
