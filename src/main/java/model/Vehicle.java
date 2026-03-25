package model;

import java.io.Serializable;

/**
 * Represents a unique vehicle in the DVRMS system.
 *
 * <p>Assignment 2 clarification: "The term 'quantity' should be ignored in this system,
 * as every vehicle is unique. Vehicles are identified solely by their Vehicle ID."
 *
 * <p>Changes from Assignment 1:
 * <ul>
 *   <li>Removed: quantity field, incrementQuantity(), decrementQuantity(), isAvailable()</li>
 *   <li>Renamed: vehicleNumber → licensePlate ("Vehicle Number is referred to as License Plate")</li>
 * </ul>
 */
public class Vehicle implements Serializable {

    private final String vehicleID;       // Primary identifier, e.g., MTL1012
    private String vehicleType;     // Sedan, SUV, Truck
    private String licensePlate;    // Was "vehicleNumber" in Asg1
    private double reservationPrice;

    public Vehicle(String vehicleID, String vehicleType, String licensePlate, double reservationPrice) {
        this.vehicleID = vehicleID;
        this.vehicleType = vehicleType;
        this.licensePlate = licensePlate;
        this.reservationPrice = reservationPrice;
    }

    // ==================== Getters ====================

    public String getVehicleID() {
        return vehicleID;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    // ==================== Setters ====================

    public void setLicensePlate(String licensePlate) {
        this.licensePlate = licensePlate;
    }

    public double getReservationPrice() {
        return reservationPrice;
    }

    public void setReservationPrice(double reservationPrice) {
        this.reservationPrice = reservationPrice;
    }

    // ==================== Display ====================

    @Override
    public String toString() {
        return vehicleID + " " + vehicleType + " " + licensePlate + " " + reservationPrice;
    }
}
