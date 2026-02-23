package com.ttu.etl.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class DateParser {

    private final List<DateTimeFormatter> formatters = Arrays.asList(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yy"),
            DateTimeFormatter.ofPattern("dd-MM-yy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy")
    );

    public LocalDate parse(String dateString) throws DateTimeParseException {
        if (dateString == null || dateString.trim().isEmpty()) {
            throw new DateTimeParseException("Date string is null or empty", "", 0);
        }

        String cleanDate = dateString.trim();

        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(cleanDate, formatter);
            } catch (DateTimeParseException e) {
                // Try next formatter
            }
        }

        throw new DateTimeParseException("Unable to parse date: " + dateString, dateString, 0);
    }

    public boolean isValidDate(String dateString, int maxYearsBack) {
        try {
            LocalDate date = parse(dateString);
            LocalDate now = LocalDate.now();
            LocalDate minDate = now.minusYears(maxYearsBack);

            if (date.isAfter(now)) {
                return false; // Future date
            }

            if (date.isBefore(minDate)) {
                return false; // Too old
            }

            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
