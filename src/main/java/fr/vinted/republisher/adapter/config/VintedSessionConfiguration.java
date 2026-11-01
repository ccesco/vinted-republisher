package fr.vinted.republisher.adapter.config;

import fr.vinted.republisher.adapter.out.browser.Chrome;
import fr.vinted.republisher.adapter.out.browser.ChromeProperties;
import fr.vinted.republisher.adapter.out.browser.PlaywrightVintedAccount;
import fr.vinted.republisher.adapter.out.browser.VintedSiteProperties;
import fr.vinted.republisher.application.session.CheckVintedSession;
import fr.vinted.republisher.application.session.LogInToVinted;
import fr.vinted.republisher.application.session.ShowVintedSession;
import fr.vinted.republisher.domain.session.VintedAccount;
import fr.vinted.republisher.domain.session.VintedSession;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.concurrent.Executor;

/** Wires the Vinted session use cases: the application layer stays free of Spring. */
@Configuration
@EnableConfigurationProperties({VintedSiteProperties.class, ChromeProperties.class})
public class VintedSessionConfiguration {

    @Bean
    VintedSession vintedSession() {
        return new VintedSession();
    }

    @Bean
    VintedAccount vintedAccount(VintedSiteProperties site, ChromeProperties chrome,
                                @Value("${app.data-dir}") Path dataDirectory) {
        Path profileDirectory = dataDirectory.resolve("browser-profile");
        return new PlaywrightVintedAccount(site, new Chrome(chrome.path(), profileDirectory, chrome.debugPort()));
    }

    @Bean
    ShowVintedSession showVintedSession(VintedSession session) {
        return new ShowVintedSession(session);
    }

    // Browser actions can last minutes (manual login): they run in the background.
    @Bean
    CheckVintedSession checkVintedSession(VintedSession session, VintedAccount account,
                                          @Qualifier("applicationTaskExecutor") Executor background) {
        return new CheckVintedSession(session, account, new FailureLoggingExecutor(background));
    }

    @Bean
    LogInToVinted logInToVinted(VintedSession session, VintedAccount account,
                                @Qualifier("applicationTaskExecutor") Executor background) {
        return new LogInToVinted(session, account, new FailureLoggingExecutor(background));
    }
}
