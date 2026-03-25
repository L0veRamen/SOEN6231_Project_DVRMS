
package server.jaxws;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "cancelReservation", namespace = "http://server/")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "cancelReservation", namespace = "http://server/", propOrder = {
    "customerID",
    "vehicleID"
})
public class CancelReservation {

    @XmlElement(name = "customerID", namespace = "")
    private String customerID;
    @XmlElement(name = "vehicleID", namespace = "")
    private String vehicleID;

    /**
     * 
     * @return
     *     returns String
     */
    public String getCustomerID() {
        return this.customerID;
    }

    /**
     * 
     * @param customerID
     *     the value for the customerID property
     */
    public void setCustomerID(String customerID) {
        this.customerID = customerID;
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

}
