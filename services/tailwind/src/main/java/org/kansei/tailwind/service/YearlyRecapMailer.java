package org.kansei.tailwind.service;

import lombok.extern.slf4j.Slf4j;
import org.kansei.tailwind.client.ShieldwallUserClient;
import org.kansei.tailwind.dto.YearlyRecapResponse;
import org.kansei.tailwind.model.TailwindUser;
import org.kansei.tailwind.model.YearlyRecap;
import org.kansei.tailwind.repository.TailwindUserRepository;
import org.kansei.tailwind.repository.YearlyRecapRepository;
import org.kansei.tailwind.stats.StatsFlightLoader;
import org.kansei.tailwind.stats.StatsModels;
import org.kansei.tailwind.stats.VisitedCountriesCalculator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The January recap email for last year. Runs daily through January: stores a recap for every opted-in user who
 * flew that year and mails the ones not sent yet, so a shieldwall or broker outage only delays the mail a day.
 * Email addresses come from shieldwall at send time and are never stored.
 */
@Slf4j
@Service
public class YearlyRecapMailer {

    private static final int CONTACT_CHUNK = 200;

    private final TailwindUserRepository tailwindUserRepository;
    private final YearlyRecapRepository yearlyRecapRepository;
    private final StatsFlightLoader statsFlightLoader;
    private final RecapService recapService;
    private final ShieldwallUserClient shieldwallUserClient;
    private final MailEventPublisher mailEventPublisher;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String blackbirdUrl;

    public YearlyRecapMailer(TailwindUserRepository tailwindUserRepository, YearlyRecapRepository yearlyRecapRepository,
                             StatsFlightLoader statsFlightLoader, RecapService recapService, ShieldwallUserClient shieldwallUserClient,
                             MailEventPublisher mailEventPublisher, ObjectMapper objectMapper, Clock clock,
                             @Value("${tailwind.blackbird-url}") String blackbirdUrl) {
        this.tailwindUserRepository = tailwindUserRepository;
        this.yearlyRecapRepository = yearlyRecapRepository;
        this.statsFlightLoader = statsFlightLoader;
        this.recapService = recapService;
        this.shieldwallUserClient = shieldwallUserClient;
        this.mailEventPublisher = mailEventPublisher;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.blackbirdUrl = blackbirdUrl.endsWith("/") ? blackbirdUrl.substring(0, blackbirdUrl.length() - 1) : blackbirdUrl;
    }

    // Returns how many recap emails went out this run
    public int sendLastYear() {
        int year = LocalDate.now(clock).getYear() - 1;
        storeMissing(year);
        return mailDue(year);
    }

    private void storeMissing(int year) {
        Map<String, VisitedCountriesCalculator.CountryInfo> countries = recapService.countryInfo();
        for (TailwindUser user : tailwindUserRepository.findAll()) {
            if (!user.isEnabled() || !user.isRecapEmails() || yearlyRecapRepository.findByUserIdAndYear(user.getUserId(), year).isPresent()) {
                continue;
            }
            try {
                YearlyRecapResponse recap = RecapService.build(
                        statsFlightLoader.load(user.getUserId(), user.getUserId(), StatsModels.Period.ALL_TIME), year, countries);
                if (recap.flightCount() == 0) {
                    continue;
                }
                yearlyRecapRepository.save(YearlyRecap.builder()
                        .userId(user.getUserId())
                        .year(year)
                        .payload(objectMapper.writeValueAsString(recap))
                        .createdAt(clock.instant())
                        .build());
            } catch (RuntimeException ex) {
                log.warn("could not build the {} recap for {}: {}", year, user.getUserId(), ex.toString());
            }
        }
    }

    private int mailDue(int year) {
        // A user who opted out or got disabled after the recap was stored is skipped, the row stays unsent
        Set<UUID> reachable = tailwindUserRepository.findAll().stream().filter(u -> u.isEnabled() && u.isRecapEmails())
                .map(TailwindUser::getUserId).collect(Collectors.toSet());
        List<YearlyRecap> due = yearlyRecapRepository.findByYearAndEmailedAtIsNull(year).stream()
                .filter(recap -> reachable.contains(recap.getUserId())).toList();

        int sent = 0;
        for (int from = 0; from < due.size(); from += CONTACT_CHUNK) {
            List<YearlyRecap> chunk = due.subList(from, Math.min(from + CONTACT_CHUNK, due.size()));
            Map<UUID, ShieldwallUserClient.Contact> contacts;
            try {
                contacts = shieldwallUserClient.findContacts(chunk.stream().map(YearlyRecap::getUserId).toList()).stream()
                        .collect(Collectors.toMap(ShieldwallUserClient.Contact::id, c -> c));
            } catch (RuntimeException ex) {
                log.warn("recap mail paused, shieldwall contact lookup failed: {}", ex.toString());
                return sent;
            }
            for (YearlyRecap recap : chunk) {
                ShieldwallUserClient.Contact contact = contacts.get(recap.getUserId());
                // No verified address yet, tried again tomorrow
                if (contact == null) {
                    continue;
                }
                try {
                    YearlyRecapResponse payload = objectMapper.readValue(recap.getPayload(), YearlyRecapResponse.class);
                    mailEventPublisher.publishYearlyRecap(contact.email(), vars(contact.username(), payload));
                    recap.setEmailedAt(clock.instant());
                    yearlyRecapRepository.save(recap);
                    sent++;
                } catch (RuntimeException ex) {
                    log.warn("could not send the {} recap to {}: {}", year, recap.getUserId(), ex.toString());
                }
            }
        }
        log.info("yearly recap run for {}: {} emails sent", year, sent);
        return sent;
    }

    // Everything the template shows, preformatted so the template stays plain text substitution
    Map<String, Object> vars(String username, YearlyRecapResponse recap) {
        Map<String, Object> vars = new java.util.HashMap<>();
        vars.put("username", username == null ? "there" : username);
        vars.put("year", String.valueOf(recap.year()));
        vars.put("flights", String.valueOf(recap.flightCount()));
        vars.put("distance", String.format(Locale.UK, "%,d km", Math.round(recap.distanceKm())));
        vars.put("earthLaps", String.format(Locale.UK, "%.1f", recap.earthLaps()));
        vars.put("moonTrips", String.format(Locale.UK, "%.2f", recap.moonTrips()));
        vars.put("hours", String.valueOf(Math.round(recap.timeInAirMinutes() / 60.0)));
        vars.put("countries", String.valueOf(recap.countryCount()));
        vars.put("newCountries", recap.newCountries().stream().map(YearlyRecapResponse.NamedCode::name).collect(Collectors.joining(", ")));
        vars.put("longestRoute", recap.longestFlight() == null ? "" : recap.longestFlight().route());
        vars.put("longestDistance", recap.longestFlight() == null ? "" : String.format(Locale.UK, "%,d km", Math.round(recap.longestFlight().distanceKm())));
        vars.put("topAirline", recap.topAirline() == null ? "" : recap.topAirline().name());
        vars.put("recapUrl", blackbirdUrl + "/recap?year=" + recap.year());
        vars.put("profileUrl", blackbirdUrl + "/profile");
        return vars;
    }
}
