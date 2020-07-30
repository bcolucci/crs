package org.example.crs.reservation.utils;

import static java.time.LocalDate.now;
import static java.time.Period.ofDays;

import java.util.Optional;

import lombok.experimental.UtilityClass;

import org.example.crs.reservation.Reservation;
import org.example.crs.reservation.command.param.ReservationCreateBody;
import org.example.crs.reservation.command.param.ReservationUpdateBody;

import com.github.javafaker.Faker;

@UtilityClass
public final class ReservationGenerationUtils
{
  public static ReservationCreateBody generateCreateBody()
  {
    var faker = Faker.instance();

    var arrivalDate = now()
        .plus(ofDays(1))
        .plus(ofDays(faker.random().nextInt(10)));

    var departureDate = arrivalDate
        .plus(ofDays(1))
        .plus(ofDays(faker.random().nextInt(2)));

    var address = faker.address();

    return ReservationCreateBody.builder()
        .clientEmail(faker.internet().emailAddress())
        .clientName(address.firstName() + " " + address.lastName())
        .arrivalDate(arrivalDate)
        .departureDate(departureDate)
        .build();
  }

  public static ReservationUpdateBody generateUpdateBody(Reservation reservation)
  {
    var arrivalDate = reservation.getArrivalDate().plus(ofDays(1));
    var departureDate = arrivalDate.plus(ofDays(1));

    return ReservationUpdateBody.builder()
        .arrivalDate(Optional.of(arrivalDate))
        .departureDate(Optional.of(departureDate))
        .build();
  }
}
