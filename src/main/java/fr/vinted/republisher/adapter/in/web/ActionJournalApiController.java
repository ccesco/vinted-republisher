package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.adapter.in.web.api.ActionJournalApi;
import fr.vinted.republisher.adapter.in.web.api.model.ActionJournalDto;
import fr.vinted.republisher.adapter.in.web.api.model.ActionOutcomeDto;
import fr.vinted.republisher.adapter.in.web.api.model.JournalEntryDto;
import fr.vinted.republisher.adapter.in.web.api.model.JournaledActionDto;
import fr.vinted.republisher.application.journal.ShowActionJournal;
import fr.vinted.republisher.application.journal.ShownJournal;
import fr.vinted.republisher.domain.journal.ActionRecord;
import fr.vinted.republisher.domain.journal.JournalEntry;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;
import java.util.Optional;
import java.util.OptionalLong;

@RestController
public class ActionJournalApiController implements ActionJournalApi {

    private final ShowActionJournal showActionJournal;

    public ActionJournalApiController(ShowActionJournal showActionJournal) {
        this.showActionJournal = showActionJournal;
    }

    @Override
    public ResponseEntity<ActionJournalDto> showActionJournal(Long before) {
        ShownJournal shown = showActionJournal.show(
                before == null ? OptionalLong.empty() : OptionalLong.of(before));
        return ResponseEntity.ok(new ActionJournalDto()
                .entries(shown.page().entries().stream()
                        .map(entry -> toDto(entry, entry.record().listingId().map(shown.titles()::get)))
                        .toList())
                .hasMore(shown.page().hasMore()));
    }

    static JournalEntryDto toDto(JournalEntry entry, Optional<String> listingTitle) {
        ActionRecord record = entry.record();
        return new JournalEntryDto()
                .entryId(entry.id())
                .occurredAt(record.occurredAt().atOffset(ZoneOffset.UTC))
                .action(JournaledActionDto.valueOf(record.action().name()))
                .outcome(ActionOutcomeDto.valueOf(record.outcome().name()))
                .listingId(record.listingId().map(VintedListingId::value).orElse(null))
                .listingTitle(listingTitle.orElse(null))
                .reason(record.reason().orElse(null));
    }
}
