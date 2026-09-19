package org.kansei.tailwind.repository;

import org.kansei.tailwind.model.Country;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CountryRepository extends JpaRepository<Country, String> {
}
