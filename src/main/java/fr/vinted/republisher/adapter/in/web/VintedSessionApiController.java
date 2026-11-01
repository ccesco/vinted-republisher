package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.adapter.in.web.api.VintedSessionApi;
import fr.vinted.republisher.adapter.in.web.api.model.BrowserActionDto;
import fr.vinted.republisher.adapter.in.web.api.model.SessionOverviewDto;
import fr.vinted.republisher.adapter.in.web.api.model.SessionProblemDto;
import fr.vinted.republisher.adapter.in.web.api.model.SessionStatusDto;
import fr.vinted.republisher.application.session.CheckVintedSession;
import fr.vinted.republisher.application.session.LogInToVinted;
import fr.vinted.republisher.application.session.ShowVintedSession;
import fr.vinted.republisher.domain.session.SessionOverview;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implements the contract-first API (vinted-republisher.yaml). Browser actions run in the background:
 * the page follows the overview.
 */
@RestController
public class VintedSessionApiController implements VintedSessionApi {

    private final ShowVintedSession showVintedSession;
    private final CheckVintedSession checkVintedSession;
    private final LogInToVinted logInToVinted;

    public VintedSessionApiController(ShowVintedSession showVintedSession,
                                      CheckVintedSession checkVintedSession,
                                      LogInToVinted logInToVinted) {
        this.showVintedSession = showVintedSession;
        this.checkVintedSession = checkVintedSession;
        this.logInToVinted = logInToVinted;
    }

    @Override
    public ResponseEntity<SessionOverviewDto> showVintedSession() {
        return ResponseEntity.ok(toDto(showVintedSession.show()));
    }

    @Override
    public ResponseEntity<SessionOverviewDto> checkVintedSession() {
        checkVintedSession.check();
        return accepted();
    }

    @Override
    public ResponseEntity<SessionOverviewDto> logInToVinted() {
        logInToVinted.logIn();
        return accepted();
    }

    /** The action started (or the one already in progress): the page shows it without reading again. */
    private ResponseEntity<SessionOverviewDto> accepted() {
        return ResponseEntity.accepted().body(toDto(showVintedSession.show()));
    }

    private static SessionOverviewDto toDto(SessionOverview overview) {
        return new SessionOverviewDto()
                .status(overview.status().map(status -> SessionStatusDto.valueOf(status.name())).orElse(null))
                .currentAction(overview.currentAction().map(action -> BrowserActionDto.valueOf(action.name())).orElse(null))
                .problem(overview.problem().map(problem -> SessionProblemDto.valueOf(problem.name())).orElse(null));
    }
}
