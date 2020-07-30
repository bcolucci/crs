package org.example.crs.reservation;

import static java.util.function.Predicate.not;
import static org.example.crs.reservation.Reservation.ReservationStatus.ACTIVE;
import static org.example.crs.reservation.Reservation.ReservationStatus.CANCELED;

import java.time.LocalDate;
import java.time.Period;
import java.util.UUID;
import java.util.function.Predicate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.example.crs.reservation.command.param.ReservationCreateBody;
import org.example.crs.reservation.command.param.ReservationUpdateBody;
import org.example.crs.reservation.utils.ReservationPeriodUtils.ReservationPeriod;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Describes a Reservation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Reservation implements ReservationPeriod
{
  public static enum ReservationStatus
  {
    ACTIVE,
    CANCELED
  }

  private UUID id;

  // @TODO add a validation
  private String clientEmail;

  // @TODO add a validation
  private String clientName;

  private LocalDate arrivalDate;

  private LocalDate departureDate;

  @Builder.Default
  private ReservationStatus status = ACTIVE;

  /**
   * @return The number of days of the reservation.
   */
  @JsonIgnore
  public int getNbDays()
  {
    return Period.between(arrivalDate, departureDate).getDays();
  }

  /**
   * @param body The reservation create body.
   * @return A reservation created based on a create body.
   */
  public static Reservation fromCreate(ReservationCreateBody body)
  {
    return Reservation.builder()
        .clientEmail(body.getClientEmail())
        .clientName(body.getClientName())
        .arrivalDate(body.getArrivalDate())
        .departureDate(body.getDepartureDate())
        .build();
  }

  /**
   * Applies optional updates on a reservation.
   *
   * @param body The reservation update body.
   * @return The current reservation, updated.
   */
  public Reservation applyUpdate(ReservationUpdateBody body)
  {
    body.getArrivalDate().ifPresent(this::setArrivalDate);
    body.getDepartureDate().ifPresent(this::setDepartureDate);
    body.getStatus().ifPresent(this::setStatus);

    return this;
  }

  /**
   * @return A predicate to filter and take only the ACTIVE reservations.
   */
  public static Predicate<Reservation> activeOnly()
  {
    return not(r -> r.getStatus() == CANCELED);
  }
}
