package org.kansei.tailwind.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A committed admin CSV import run, the history the admin screen lists.
 */
@Entity
@Table(name = "csv_imports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CsvImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_user_id")
    private UUID adminUserId;

    @Column(name = "target_user_id")
    private UUID targetUserId;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "total_rows")
    private int totalRows;

    @Column(name = "imported_rows")
    private int importedRows;

    @Column(name = "duplicate_rows")
    private int duplicateRows;

    @Column(name = "error_rows")
    private int errorRows;

    // [{line, message}] for the first rejected rows
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String errors;

    @Column(name = "created_at")
    private Instant createdAt;
}
