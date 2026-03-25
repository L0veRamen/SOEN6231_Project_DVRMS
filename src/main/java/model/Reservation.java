package model;

import java.io.Serializable;

/**
 * Represents a vehicle reservation by a customer for a date range.
 *
 * <p>Assignment 2 clarification: "scheduling is strictly date-based. If the end date of a vehicle
 * assigned to User 1 is the same as the start date requested by User 2, the vehicle will not be
 * reassigned."
 *
 * <p>Date format: ddmmyyyy (e.g., 01022026 = February 1, 2026)
 */
public class Reservation implements Serializable {

  private final String customerID; // e.g., MTLU1111
  private final String vehicleID; // e.g., MTL1012
  private String startDate; // ddmmyyyy
  private String endDate; // ddmmyyyy
  private final double pricePaid; // price at the time of booking

  public Reservation(String customerID, String vehicleID, String startDate, String endDate) {
    this(customerID, vehicleID, startDate, endDate, 0);
  }

  public Reservation(
      String customerID, String vehicleID, String startDate, String endDate, double pricePaid) {
    this.customerID = customerID;
    this.vehicleID = vehicleID;
    this.startDate = startDate;
    this.endDate = endDate;
    this.pricePaid = pricePaid;
  }

  // ==================== Getters ====================

  public String getCustomerID() {
    return customerID;
  }

  public String getVehicleID() {
    return vehicleID;
  }

  public String getStartDate() {
    return startDate;
  }

  public void setStartDate(String startDate) {
    this.startDate = startDate;
  }

  public String getEndDate() {
    return endDate;
  }

  // ==================== Setters ====================

  public void setEndDate(String endDate) {
    this.endDate = endDate;
  }

  public double getPricePaid() {
    return pricePaid;
  }

  // ==================== Display ====================

  @Override
  public String toString() {
    return "Reservation{" + customerID + ", " + vehicleID + ", " + startDate + "-" + endDate + "}";
  }
}
