package org.example.crs.reservation.command.param;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Describes an availability period.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Availability
{
  private LocalDate from;
  private LocalDate to;

  /**
   * @return The number of days of the period.
   */
  @JsonProperty
  public long nbDays()
  {
    return from.until(to, ChronoUnit.DAYS);
  }
}
