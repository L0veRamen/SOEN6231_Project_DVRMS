package server;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import model.Reservation;

final class ReservationQueries {

  private ReservationQueries() {}

  static Optional<Reservation> findReservation(List<Reservation> list, String customerID) {
    if (list == null) {
      return Optional.empty();
    }
    return list.stream().filter(r -> r.getCustomerID().equals(customerID)).findFirst();
  }

  static boolean hasExistingReservation(
      Map<String, List<Reservation>> reservations, String customerID, String vehicleID) {
    return findReservation(reservations.get(vehicleID), customerID).isPresent();
  }

  static boolean hasAnyOverlap(
      Map<String, List<Reservation>> reservations,
      DateRules dateRules,
      String vehicleID,
      LocalDate start,
      LocalDate end,
      Reservation exclude) {
    List<Reservation> vehicleReservations = reservations.get(vehicleID);
    if (vehicleReservations == null) {
      return false;
    }

    synchronized (vehicleReservations) {
      for (Reservation reservation : vehicleReservations) {
        if (exclude != null && reservation == exclude) {
          continue;
        }
        LocalDate existingStart = dateRules.parseDate(reservation.getStartDate());
        LocalDate existingEnd = dateRules.parseDate(reservation.getEndDate());
        if (existingStart == null || existingEnd == null) {
          continue;
        }
        if (dateRules.datesOverlapInclusive(start, end, existingStart, existingEnd)) {
          return true;
        }
      }
    }
    return false;
  }
}
