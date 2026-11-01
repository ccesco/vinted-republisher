package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.adapter.in.web.api.model.BrowserActionDto;
import fr.vinted.republisher.adapter.in.web.api.model.SessionProblemDto;
import fr.vinted.republisher.adapter.in.web.api.model.SessionStatusDto;
import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.SessionProblem;
import fr.vinted.republisher.domain.session.SessionStatus;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** The API maps domain values by name: the contract must know every one of them. */
class ContractEnumsTest {

    @Test
    void contract_knows_every_session_status() {
        assertThat(names(SessionStatusDto.values())).containsExactlyInAnyOrder(names(SessionStatus.values()));
    }

    @Test
    void contract_knows_every_browser_action() {
        assertThat(names(BrowserActionDto.values())).containsExactlyInAnyOrder(names(BrowserAction.values()));
    }

    @Test
    void contract_knows_every_session_problem() {
        assertThat(names(SessionProblemDto.values())).containsExactlyInAnyOrder(names(SessionProblem.values()));
    }

    private static String[] names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toArray(String[]::new);
    }
}
