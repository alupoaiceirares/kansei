package org.kansei.tailwind.service;

import org.kansei.tailwind.repository.CsvImportRepository;
import org.kansei.tailwind.repository.FlightJoinRequestRepository;
import org.kansei.tailwind.repository.FlightRepository;
import org.kansei.tailwind.repository.FriendshipRepository;
import org.kansei.tailwind.repository.JourneyRepository;
import org.kansei.tailwind.repository.TailwindUserRepository;
import org.kansei.tailwind.repository.UserFlightRepository;
import org.kansei.tailwind.repository.YearlyRecapRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Removes everything a set of users owns, used when shieldwall no longer has the account. Canonical API
 * flights stay, they belong to nobody, manual flights go with their owner. New per-user tables get added here.
 */
@Service
public class UserDataCleaner {

    private final UserFlightRepository userFlightRepository;
    private final JourneyRepository journeyRepository;
    private final FlightRepository flightRepository;
    private final TailwindUserRepository tailwindUserRepository;
    private final FriendshipRepository friendshipRepository;
    private final CsvImportRepository csvImportRepository;
    private final FlightJoinRequestRepository joinRequestRepository;
    private final YearlyRecapRepository yearlyRecapRepository;

    public UserDataCleaner(UserFlightRepository userFlightRepository, JourneyRepository journeyRepository, FlightRepository flightRepository,
                           TailwindUserRepository tailwindUserRepository, FriendshipRepository friendshipRepository,
                           CsvImportRepository csvImportRepository, FlightJoinRequestRepository joinRequestRepository,
                           YearlyRecapRepository yearlyRecapRepository) {
        this.userFlightRepository = userFlightRepository;
        this.journeyRepository = journeyRepository;
        this.flightRepository = flightRepository;
        this.tailwindUserRepository = tailwindUserRepository;
        this.friendshipRepository = friendshipRepository;
        this.csvImportRepository = csvImportRepository;
        this.joinRequestRepository = joinRequestRepository;
        this.yearlyRecapRepository = yearlyRecapRepository;
    }

    @Transactional
    public void deleteAllFor(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return;
        }
        List<Long> flightIds = userFlightRepository.findFlightIdsByUserIds(userIds);
        // Requests they sent, the ones waiting on their own entries go with those entries
        joinRequestRepository.deleteByRequesterIds(userIds);
        userFlightRepository.deleteByUserIds(userIds);
        journeyRepository.deleteByUserIds(userIds);
        if (!flightIds.isEmpty()) {
            flightRepository.deleteManualByIds(flightIds);
        }
        friendshipRepository.deleteByUserIds(userIds);
        csvImportRepository.deleteByTargetUserIds(userIds);
        yearlyRecapRepository.deleteByUserIds(userIds);
        tailwindUserRepository.deleteAllById(userIds);
    }
}
