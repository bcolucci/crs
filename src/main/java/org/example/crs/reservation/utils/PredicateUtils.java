package org.example.crs.reservation.utils;

import java.util.function.Predicate;
import java.util.function.Supplier;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PredicateUtils
{
  /**
   * The predicate which always return true.
   */
  public static final Predicate ALWAYS_TRUE = x -> true;

  /**
   * A "always return true predicate" supplier.
   *
   * @param <T> Type of the predicate value,
   * @return A predicate supplier.
   */
  public static final <T> Supplier<Predicate<T>> alwaysTrue()
  {
    return () -> ALWAYS_TRUE;
  }
}
