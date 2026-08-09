package org.kansei.wirehood.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Client-assigned PK (day), one row per calendar day - no single @Id column, reads/writes go through SongOfTheDayRepository's custom @Query methods (upsert), not ReactiveCrudRepository.save()
 */
@Table("song_of_the_day")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SongOfTheDay {

    private LocalDate day;

    private UUID trackId;
}
