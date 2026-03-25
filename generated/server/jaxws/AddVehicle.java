
package server.jaxws;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "addVehicle", namespace = "http://server/")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "addVehicle", namespace = "http://server/", propOrder = {
    "managerID",
    "vehicleNumber",
    "vehicleType",
    "vehicleID",
    "reservationPrice"
})
public class AddVehicle {

    @XmlElement(name = "managerID", namespace = "")
    private String managerID;
    @XmlElement(name = "vehicleNumber", namespace = "")
    private String vehicleNumber;
    @XmlElement(name = "vehicleType", namespace = "")
    private String vehicleType;
    @XmlElement(name = "vehicleID", namespace = "")
    private String vehicleID;
    @XmlElement(name = "reservationPrice", namespace = "")
    private double reservationPrice;

    /**
     * 
     * @return
     *     returns String
     */
    public String getManagerID() {
        return this.managerID;
    }

    /**
     * 
     * @param managerID
     *     the value for the managerID property
     */
    public void setManagerID(String managerID) {
        this.managerID = managerID;
    }

    /**
     * 
     * @return
     *     returns String
     */
    public String getVehicleNumber() {
        return this.vehicleNumber;
    }

    /**
     * 
     * @param vehicleNumber
     *     the value for the vehicleNumber property
     */
    public void setVehicleNumber(String vehicleNumber) {
        this.vehicleNumber = vehicleNumber;
    }

    /**
     * 
     * @return
     *     returns String
     */
    public String getVehicleType() {
        return this.vehicleType;
    }

    /**
     * 
     * @param vehicleType
     *     the value for the vehicleType property
     */
    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }

    /**
     * 
     * @return
     *     returns String
     */
    public String getVehicleID() {
        return this.vehicleID;
    }

    /**
     * 
     * @param vehicleID
     *     the value for the vehicleID property
     */
    public void setVehicleID(String vehicleID) {
        this.vehicleID = vehicleID;
    }

    /**
     * 
     * @return
     *     returns double
     */
    public double getReservationPrice() {
        return this.reservationPrice;
    }

    /**
     * 
     * @param reservationPrice
     *     the value for the reservationPrice property
     */
    public void setReservationPrice(double reservationPrice) {
        this.reservationPrice = reservationPrice;
    }

}
