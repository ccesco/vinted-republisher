package fr.vinted.republisher.adapter.config;

import fr.vinted.republisher.application.deletion.DeleteListing;
import fr.vinted.republisher.application.deletion.RecheckListingDeletion;
import fr.vinted.republisher.application.inventory.ShowListingDetail;
import fr.vinted.republisher.application.relaunch.RelaunchListing;
import fr.vinted.republisher.application.republication.RepublishListing;
import fr.vinted.republisher.application.republication.VerifyListingRepublication;
import fr.vinted.republisher.domain.deletion.ListingDeletionRepository;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RelaunchConfiguration {

    // Like the actions it launches, a relaunch runs within the request.
    @Bean
    RelaunchListing relaunchListing(ShowListingDetail showListingDetail, ListingDeletionRepository deletions,
                                    RepublicationDelay republicationDelay, DeleteListing deleteListing,
                                    RecheckListingDeletion recheckListingDeletion, RepublishListing republishListing,
                                    VerifyListingRepublication verifyListingRepublication) {
        return new RelaunchListing(showListingDetail, deletions, republicationDelay, deleteListing,
                recheckListingDeletion, republishListing, verifyListingRepublication);
    }
}
