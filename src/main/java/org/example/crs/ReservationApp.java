package org.example.crs;

import static akka.http.javadsl.ConnectHttp.toHost;

import java.text.SimpleDateFormat;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import org.example.crs.reservation.ReservationMapRepository;
import org.example.crs.reservation.ReservationRegistry;
import org.example.crs.reservation.ReservationRoute;
import org.example.crs.reservation.ReservationService;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Adapter;
import akka.actor.typed.javadsl.Behaviors;
import akka.http.javadsl.Http;
import akka.stream.Materializer;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.ConfigFactory;

@Slf4j
@UtilityClass
public class ReservationApp
{
  /**
   * The great and unique object mapper of the application!
   */
  public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .findAndRegisterModules()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .setSerializationInclusion(Include.NON_NULL)
      .setSerializationInclusion(Include.NON_ABSENT)
      .setDateFormat(new SimpleDateFormat("yyyy-MM-dd"));

  /**
   * @return The main App actor.
   */
  public static Behavior createActor()
  {
    var mapRepository = new ReservationMapRepository();
    var service = new ReservationService(mapRepository);

    return Behaviors.setup(ctx ->
    {
      var system = ctx.getSystem();
      var config = ConfigFactory.load();

      var registry = ctx.spawn(ReservationRegistry.create(service), "ReservationRegistry");
      var route = new ReservationRoute(system, registry);

      var classicSystem = Adapter.toClassic(system);
      var http = Http.get(classicSystem);
      var materializer = Materializer.matFromSystem(system);

      var routeFlow = route.getRoute().flow(classicSystem, materializer);

      var connectHttp = toHost(config.getString("server.host"), config.getInt("server.port"));
      http.bindAndHandle(routeFlow, connectHttp, materializer).whenComplete((binding, ex) ->
      {
        if (ex != null)
        {
          log.error("Failed to bind HTTP endpoint, terminating system", ex);
          system.terminate();
          return;
        }

        var address = binding.localAddress();
        log.info("Server online at http://{}:{}/", address.getHostString(), address.getPort());
      });

      return Behaviors.empty();
    });
  }

  public static void main(String[] args)
  {
    ActorSystem.create(createActor(), "CRS");
  }
}
