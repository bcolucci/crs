package org.example.crs.reservation;

import static akka.http.javadsl.model.StatusCodes.CREATED;
import static akka.http.javadsl.model.StatusCodes.OK;
import static java.lang.String.format;
import static java.time.LocalDate.now;
import static java.time.Period.ofDays;
import static java.util.Collections.shuffle;
import static java.util.stream.Collectors.averagingLong;
import static org.example.crs.ReservationApp.OBJECT_MAPPER;
import static org.example.crs.reservation.utils.ReservationGenerationUtils.generateUpdateBody;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import org.example.crs.ReservationApp;
import org.example.crs.reservation.command.ReservationCommands.CancelReservationResponse;
import org.example.crs.reservation.command.ReservationCommands.CreateReservationResponse;
import org.example.crs.reservation.command.ReservationCommands.GetAvailabilitiesResponse;
import org.example.crs.reservation.command.ReservationCommands.GetReservationResponse;
import org.example.crs.reservation.command.ReservationCommands.UpdateReservationResponse;
import org.example.crs.reservation.command.param.Availability;
import org.example.crs.reservation.command.param.ReservationCreateBody;

import akka.actor.typed.ActorSystem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.javafaker.Faker;
import com.typesafe.config.ConfigFactory;

@Slf4j
public class LoadTest
{
  private static String serverBaseUri;

  private static final HttpClient httpClient = HttpClient.newHttpClient();
  private static final Faker faker = Faker.instance();

  private static final AtomicInteger nbRequests = new AtomicInteger();

  private static final List<UUID> reservationIds = new CopyOnWriteArrayList();

  private static final List<Long> creationExecutionTimes = new CopyOnWriteArrayList();
  private static final List<Long> retrievalExecutionTimes = new CopyOnWriteArrayList();
  private static final List<Long> updateExecutionTimes = new CopyOnWriteArrayList();
  private static final List<Long> cancelationExecutionTimes = new CopyOnWriteArrayList();
  private static final List<Long> availabilitiesExecutionTimes = new CopyOnWriteArrayList();

  @Data
  private static class LoadConfig
  {
    private long interval;
    private int size;
  }

  public static void main(String... args) throws Exception
  {
    var actor = ActorSystem.create(ReservationApp.createActor(), "CRS");
    Thread.sleep(5 * 1000);

    var config = ConfigFactory.load();

    serverBaseUri = format("http://%s:%d", config.getString("server.host"), config.getInt("server.port"));

    var createConfig = OBJECT_MAPPER.convertValue(config.getAnyRef("load_test.create"), LoadConfig.class);
    var retrieveConfig = OBJECT_MAPPER.convertValue(config.getAnyRef("load_test.retrieve"), LoadConfig.class);
    var updateConfig = OBJECT_MAPPER.convertValue(config.getAnyRef("load_test.update"), LoadConfig.class);
    var cancelConfig = OBJECT_MAPPER.convertValue(config.getAnyRef("load_test.cancel"), LoadConfig.class);
    var availabilityConfig = OBJECT_MAPPER.convertValue(config.getAnyRef("load_test.availability"), LoadConfig.class);

    var t = new Timer();

    long startTime = System.nanoTime();

    createSomeFollowingReservations(10).join();

    t.scheduleAtFixedRate(createRandomReservationsTask(createConfig.getSize()), 0, createConfig.getInterval());
    t.scheduleAtFixedRate(retrieveRandomReservationsTask(retrieveConfig.getSize()), 0, retrieveConfig.getInterval());
    t.scheduleAtFixedRate(updateRandomReservationsTask(updateConfig.getSize()), 0, updateConfig.getInterval());
    t.scheduleAtFixedRate(cancelRandomReservationsTask(cancelConfig.getSize()), 0, cancelConfig.getInterval());
    t.scheduleAtFixedRate(doRandomAvailabilitiesChecksTask(availabilityConfig.getSize()), 0, availabilityConfig.getInterval());

    Thread.sleep(config.getLong("load_test.duration"));

    var executionTimeMs = (System.nanoTime() - startTime) / 1000000;

    t.cancel();

    Thread.sleep(3 * 1000);

    actor.terminate();

    var averageCreationTimeMs = creationExecutionTimes.stream()
        .collect(averagingLong(x -> x)) / 1000000;
    var averageRetrievalTimeMs = retrievalExecutionTimes.stream()
        .collect(averagingLong(x -> x)) / 1000000;
    var averageUpdateTimeMs = updateExecutionTimes.stream()
        .collect(averagingLong(x -> x)) / 1000000;
    var averageCanceletionTimeMs = cancelationExecutionTimes.stream()
        .collect(averagingLong(x -> x)) / 1000000;
    var averageAvailabilitiesTimeMs = availabilitiesExecutionTimes.stream()
        .collect(averagingLong(x -> x)) / 1000000;

    // --- Report
    Stream.of(createConfig, retrieveConfig, updateConfig, cancelConfig)
        .forEach(c -> log.info("{}", c));;

    log.info("CREATE avg time = {}ms", averageCreationTimeMs);
    log.info("RETRIEVE avg time = {}ms", averageRetrievalTimeMs);
    log.info("UPDATE avg time = {}ms", averageUpdateTimeMs);
    log.info("CANCEL avg time = {}ms", averageCanceletionTimeMs);
    log.info("AVAILABILITIES avg time = {}ms", averageAvailabilitiesTimeMs);

    log.info("number of requests = {}", nbRequests.get());
    log.info("execution time = {}ms", executionTimeMs);
    log.info("requests per second = {}", Math.ceil(nbRequests.get() / (executionTimeMs / 1000)));
  }

  private static TimerTask createRandomReservationsTask(int nbToCreate)
  {
    return new TimerTask()
    {
      @Override
      public void run()
      {
        CompletableFuture.allOf(IntStream.range(0, nbToCreate)
            .mapToObj(i -> createRandomReservation())
            .toArray(CompletableFuture[]::new))
            .join();
      }
    };
  }

  private static CompletableFuture<Optional<Reservation>> createRandomReservation()
  {
    return createReservation(Optional.empty(), Optional.empty());
  }

  private static CompletableFuture<Optional<Reservation>> createReservation(
      Optional<LocalDate> maybeArrivalDate,
      Optional<LocalDate> maybeDepartureDate)
  {
    LocalDate arrivalDate;

    // let's make bad creations a few times
    if (Math.random() < 0.15)
    {
      arrivalDate = now();
    }
    else
    {
      arrivalDate = maybeArrivalDate.orElseGet(() -> now().plus(ofDays(1 + faker.random().nextInt(26))));
    }

    var departureDate = maybeDepartureDate.orElseGet(() -> arrivalDate.plus(ofDays(1 + faker.random().nextInt(2))));

    try
    {
      var requestBody = OBJECT_MAPPER.writeValueAsString(ReservationCreateBody.builder()
          .clientEmail(faker.internet().emailAddress())
          .arrivalDate(arrivalDate)
          .departureDate(departureDate)
          .build());

      var request = defaultHttpBuilder("/reservations")
          .POST(HttpRequest.BodyPublishers.ofString(requestBody))
          .build();

      long startTime = System.nanoTime();

      return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
          .thenApplyAsync(response ->
          {
            nbRequests.incrementAndGet();
            creationExecutionTimes.add(System.nanoTime() - startTime);

            if (response.statusCode() != CREATED.intValue())
            {
              logError(response);

              return Optional.empty();
            }

            try
            {
              var reservation = OBJECT_MAPPER.readValue(response.body(), CreateReservationResponse.class)
                  .getReservation();
              reservationIds.add(reservation.getId());

              log.debug("Created: {}", reservation);

              return Optional.of(reservation);
            }
            catch (JsonProcessingException ex)
            {
            }

            return Optional.empty();
          });
    }
    catch (JsonProcessingException ex)
    {
      return CompletableFuture.completedFuture(Optional.empty());
    }
  }

  private static CompletableFuture createSomeFollowingReservations(int nbToCreate)
  {
    var dayCursor = new AtomicInteger(1);

    return CompletableFuture.allOf(
        IntStream.range(0, nbToCreate).mapToObj(i ->
        {
          var skip = faker.random().nextInt(2);
          var duration = 1 + faker.random().nextInt(2);

          var counterValue = dayCursor.addAndGet(skip + duration);

          var arrivalDate = now().plus(ofDays(counterValue - duration));
          var departureDate = arrivalDate.plus(ofDays(duration));

          return createReservation(Optional.of(arrivalDate), Optional.of(departureDate));
        }).toArray(CompletableFuture<?>[]::new)
    );
  }

  private static TimerTask updateRandomReservationsTask(int nbToUpdate)
  {
    return new TimerTask()
    {
      @Override
      public void run()
      {
        CompletableFuture.allOf(IntStream.range(0, nbToUpdate)
            .mapToObj(i -> updateRandomReservation())
            .toArray(CompletableFuture[]::new))
            .join();
      }
    };
  }

  private static CompletableFuture<Optional<Reservation>> updateRandomReservation()
  {
    return retrieveRandomCreatedReservation().thenComposeAsync(maybeReservation ->
    {
      if (maybeReservation.isEmpty())
      {
        return CompletableFuture.completedFuture(Optional.empty());
      }

      var reservation = maybeReservation.get();

      try
      {
        var requestBody = OBJECT_MAPPER.writeValueAsString(generateUpdateBody(reservation));

        var request = defaultHttpBuilder(format("/reservations/%s", reservation.getId()))
            .PUT(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        long startTime = System.nanoTime();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApplyAsync(response ->
            {
              nbRequests.incrementAndGet();
              updateExecutionTimes.add(System.nanoTime() - startTime);

              if (response.statusCode() != OK.intValue())
              {
                logError(response);

                return Optional.empty();
              }

              try
              {
                var updated = OBJECT_MAPPER.readValue(response.body(), UpdateReservationResponse.class)
                    .getReservation();

                log.debug("Updated: {}", updated);

                return Optional.of(updated);
              }
              catch (JsonProcessingException ex)
              {
              }

              return Optional.empty();
            });
      }
      catch (JsonProcessingException ex)
      {
      }

      return CompletableFuture.completedFuture(Optional.empty());
    });
  }

  private static TimerTask cancelRandomReservationsTask(int nbToCancel)
  {
    return new TimerTask()
    {
      @Override
      public void run()
      {
        CompletableFuture.allOf(IntStream.range(0, nbToCancel)
            .mapToObj(i -> cancelRandomReservation())
            .toArray(CompletableFuture[]::new))
            .join();
      }
    };
  }

  private static CompletableFuture<Optional<Reservation>> cancelRandomReservation()
  {
    var id = getRandomReservationId();

    var request = defaultHttpBuilder(format("/reservations/%s", id))
        .DELETE()
        .build();

    long startTime = System.nanoTime();

    return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
        .thenApplyAsync(response ->
        {
          nbRequests.incrementAndGet();
          cancelationExecutionTimes.add(System.nanoTime() - startTime);

          if (response.statusCode() != OK.intValue())
          {
            logError(response);

            return Optional.empty();
          }

          try
          {
            var reservation = OBJECT_MAPPER.readValue(response.body(), CancelReservationResponse.class)
                .getReservation();

            log.debug("Canceled: {}", reservation);

            return Optional.of(reservation);
          }
          catch (JsonProcessingException ex)
          {
          }

          return Optional.empty();
        });
  }

  private static TimerTask retrieveRandomReservationsTask(int nbToRetrieve)
  {
    return new TimerTask()
    {
      @Override
      public void run()
      {
        CompletableFuture.allOf(IntStream.range(0, nbToRetrieve)
            .mapToObj(i -> retrieveRandomCreatedReservation())
            .toArray(CompletableFuture[]::new))
            .join();
      }
    };
  }

  private static UUID getRandomReservationId()
  {
    if (reservationIds.isEmpty())
    {
      return UUID.randomUUID();
    }

    if (Math.random() < 0.15)
    {
      // generating some 404
      return UUID.randomUUID();
    }

    // had an error when using Stream + random sort...
    shuffle(reservationIds);

    return reservationIds.get(0);
  }

  private static CompletableFuture<Optional<Reservation>> retrieveRandomCreatedReservation()
  {
    var id = getRandomReservationId();

    var request = defaultHttpBuilder(format("/reservations/%s", id))
        .GET()
        .build();

    long startTime = System.nanoTime();

    return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
        .thenApplyAsync(response ->
        {
          nbRequests.incrementAndGet();
          retrievalExecutionTimes.add(System.nanoTime() - startTime);

          if (response.statusCode() != OK.intValue())
          {
            logError(response);

            return Optional.empty();
          }

          try
          {
            var reservation = OBJECT_MAPPER.readValue(response.body(), GetReservationResponse.class)
                .getReservation();

            log.debug("Retrieved: {}", reservation);

            return Optional.of(reservation);
          }
          catch (JsonProcessingException ex)
          {
          }

          return Optional.empty();
        });
  }

  private static TimerTask doRandomAvailabilitiesChecksTask(int nbToRetrieve)
  {
    return new TimerTask()
    {
      @Override
      public void run()
      {
        CompletableFuture.allOf(IntStream.range(0, nbToRetrieve)
            .mapToObj(i -> doRandomAvailabilitiesCheck())
            .toArray(CompletableFuture[]::new))
            .join();
      }
    };
  }

  private static CompletableFuture<Optional<List<Availability>>> doRandomAvailabilitiesCheck()
  {
    var checkFrom = now().plus(ofDays(faker.random().nextInt(25)));
    var checkTo = checkFrom.plus(ofDays(faker.random().nextInt(40))); // 40 -> it will trigger some bad requests

    var request = defaultHttpBuilder(format("/reservations?from=%s&to=%s", checkFrom, checkTo))
        .GET()
        .build();

    long startTime = System.nanoTime();

    return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
        .thenApplyAsync(response ->
        {
          nbRequests.incrementAndGet();
          availabilitiesExecutionTimes.add(System.nanoTime() - startTime);

          if (response.statusCode() != OK.intValue())
          {
            logError(response);

            return Optional.empty();
          }

          try
          {
            var availabilities = OBJECT_MAPPER.readValue(response.body(), GetAvailabilitiesResponse.class)
                .getAvailabilities();

            log.debug("Availabilities: {}", availabilities);

            return Optional.of(availabilities);
          }
          catch (JsonProcessingException ex)
          {
          }

          return Optional.empty();
        });
  }

  private static HttpRequest.Builder defaultHttpBuilder(String uri)
  {
    return HttpRequest.newBuilder(URI.create(serverBaseUri + uri))
        .header("Content-Type", "application/json");
  }

  private static void logError(HttpResponse<String> response)
  {
    try
    {
      var map = OBJECT_MAPPER.readValue(response.body(), LinkedHashMap.class);

      var statusCode = (int) map.get("statusCode");
      var error = Optional.ofNullable(map.get("error")).map(String.class::cast).orElse("<empty>");

      log.debug("{} {}", statusCode, error);
    }
    catch (JsonProcessingException ex)
    {
    }
  }
}
